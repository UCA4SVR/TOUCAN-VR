/*
 * Copyright 2017 Laboratoire I3S, CNRS, Université côte d'azur
 *
 * Inspired from com.google.android.exoplayer2.extractor.DefaultTrackOutput,
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package fr.unice.i3s.uca4svr.toucan_vr.mediaplayer.extractor;

import android.util.Log;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.FormatHolder;
import com.google.android.exoplayer2.decoder.DecoderInputBuffer;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.extractor.TrackOutput;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.ParsableByteArray;
import com.google.android.exoplayer2.util.Util;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A {@link TrackOutput} that buffers extracted samples in a queue and allows for consumption from
 * that queue. It also allows to replace part of the queue if it has not yet been consumed.
 */
public final class ReplacementTrackOutput implements TrackOutput {

  /**
   * A listener for changes to the upstream format.
   */
  public interface UpstreamFormatChangedListener {

    /**
     * Called on the loading thread when an upstream format change occurs.
     *
     * @param format The new upstream format.
     */
    void onUpstreamFormatChanged(Format format);

  }

  private static final int INITIAL_SCRATCH_SIZE = 32;

  private static final int STATE_ENABLED = 0;
  private static final int STATE_ENABLED_WRITING = 1;
  private static final int STATE_DISABLED = 2;

  private final InfoQueue infoQueue;
  private final byte[] dataQueue;
  private final BufferExtrasHolder extrasHolder;
  private final ParsableByteArray scratch;
  private final AtomicInteger state;

  // Accessed only by the consuming thread.
  private long totalBytesDropped;
  private Format downstreamFormat;

  // Accessed only by the loading thread (or the consuming thread when there is no loading thread).
  private boolean pendingFormatAdjustment;
  private Format lastUnadjustedFormat;
  private long sampleOffsetUs;
  private long totalBytesWritten;
  private boolean needKeyframe;
  private boolean pendingSplice;
  private UpstreamFormatChangedListener upstreamFormatChangeListener;

  // Variable for the replacement
  private final ReentrantLock lock;
  private boolean isReplaceing = false;
  private final byte[] replacementData;
  private final BufferExtrasHolder[] replacementMetaData;
  private long replacementStartTime;
  private long replacementEndTime;
  private long replacementTotalBytesWritten;
  private int replacementAbsoluteWriteIndex;

  // logs
  private static int nextIndex = 0;
  private int id;

  /**
   * @param allocator An {@link Allocator} from which allocations for sample data can be obtained.
   */
  public ReplacementTrackOutput(Allocator allocator) {
    lock = new ReentrantLock();
    infoQueue = new InfoQueue();
    replacementMetaData = new BufferExtrasHolder[InfoQueue.SAMPLE_CAPACITY_INCREMENT/2];
    //1048576
    dataQueue = new byte[33554432];
    replacementData = new byte[33554432/3];
    extrasHolder = new BufferExtrasHolder();
    scratch = new ParsableByteArray(INITIAL_SCRATCH_SIZE);
    state = new AtomicInteger();
    needKeyframe = true;
    id = nextIndex++;
  }

  // Called by the consuming thread, but only when there is no loading thread.

  /**
   * Resets the output.
   *
   * @param enable Whether the output should be enabled. False if it should be disabled.
   */
  public void reset(boolean enable) {
    int previousState = state.getAndSet(enable ? STATE_ENABLED : STATE_DISABLED);
    clearSampleData();
    infoQueue.resetLargestParsedTimestamps();
    if (previousState == STATE_DISABLED) {
      downstreamFormat = null;
    }
  }

  /**
   * Sets a source identifier for subsequent samples.
   *
   * @param sourceId The source identifier.
   */
  public void sourceId(int sourceId) {
    infoQueue.sourceId(sourceId);
  }

  /**
   * Indicates that samples subsequently queued to the buffer should be spliced into those already
   * queued.
   */
  public void splice() {
    pendingSplice = true;
  }

  /**
   * Returns the current absolute write index.
   */
  public int getWriteIndex() {
    return infoQueue.getWriteIndex();
  }

  /**
   * Discards samples from the write side of the buffer.
   *
   * @param discardFromIndex The absolute index of the first sample to be discarded.
   */
  public void discardUpstreamSamples(int discardFromIndex) {
    totalBytesWritten = infoQueue.discardUpstreamSamples(discardFromIndex);
  }

  // Called by the consuming thread.

  /**
   * Disables buffering of sample data and metadata.
   */
  public void disable() {
    if (state.getAndSet(STATE_DISABLED) == STATE_ENABLED) {
      clearSampleData();
    }
  }

  /**
   * Returns whether the buffer is empty.
   */
  public boolean isEmpty() {
    return infoQueue.isEmpty();
  }

  /**
   * Returns the current absolute read index.
   */
  public int getReadIndex() {
    return infoQueue.getReadIndex();
  }

  /**
   * Peeks the source id of the next sample, or the current upstream source id if the buffer is
   * empty.
   *
   * @return The source id.
   */
  public int peekSourceId() {
    return infoQueue.peekSourceId();
  }

  /**
   * Returns the upstream {@link Format} in which samples are being queued.
   */
  public Format getUpstreamFormat() {
    return infoQueue.getUpstreamFormat();
  }

  /**
   * Returns the largest sample timestamp that has been queued since the last {@link #reset}.
   * <p>
   * Samples that were discarded by calling {@link #discardUpstreamSamples(int)} are not
   * considered as having been queued. Samples that were dequeued from the front of the queue are
   * considered as having been queued.
   *
   * @return The largest sample timestamp that has been queued, or {@link Long#MIN_VALUE} if no
   *     samples have been queued.
   */
  public long getLargestQueuedTimestampUs() {
    return infoQueue.getLargestQueuedTimestampUs();
  }

  /**
   * Attempts to skip to the keyframe before or at the specified time. Succeeds only if the buffer
   * contains a keyframe with a timestamp of {@code timeUs} or earlier, and if {@code timeUs} falls
   * within the currently buffered media.
   * <p>
   * This method is equivalent to {@code skipToKeyframeBefore(timeUs, false)}.
   *
   * @param timeUs The seek time.
   * @return Whether the skip was successful.
   */
  public boolean skipToKeyframeBefore(long timeUs) {
    return skipToKeyframeBefore(timeUs, false);
  }

  /**
   * Attempts to skip to the keyframe before or at the specified time. Succeeds only if the buffer
   * contains a keyframe with a timestamp of {@code timeUs} or earlier. If
   * {@code allowTimeBeyondBuffer} is {@code false} then it is also required that {@code timeUs}
   * falls within the buffer.
   *
   * @param timeUs The seek time.
   * @param allowTimeBeyondBuffer Whether the skip can succeed if {@code timeUs} is beyond the end
   *     of the buffer.
   * @return Whether the skip was successful.
   */
  public boolean skipToKeyframeBefore(long timeUs, boolean allowTimeBeyondBuffer) {
    long nextOffset = infoQueue.skipToKeyframeBefore(timeUs, allowTimeBeyondBuffer);
    if (nextOffset == C.POSITION_UNSET) {
      return false;
    }
    dropDownstreamTo(nextOffset);
    return true;
  }

  /**
   * Attempts to read from the queue.
   *
   * @param formatHolder A {@link FormatHolder} to populate in the case of reading a format.
   * @param buffer A {@link DecoderInputBuffer} to populate in the case of reading a sample or the
   *     end of the stream. If the end of the stream has been reached, the
   *     {@link C#BUFFER_FLAG_END_OF_STREAM} flag will be set on the buffer.
   * @param formatRequired Whether the caller requires that the format of the stream be read even if
   *     it's not changing. A sample will never be read if set to true, however it is still possible
   *     for the end of stream or nothing to be read.
   * @param loadingFinished True if an empty queue should be considered the end of the stream.
   * @param decodeOnlyUntilUs If a buffer is read, the {@link C#BUFFER_FLAG_DECODE_ONLY} flag will
   *     be set if the buffer's timestamp is less than this value.
   * @return The result, which can be {@link C#RESULT_NOTHING_READ}, {@link C#RESULT_FORMAT_READ} or
   *     {@link C#RESULT_BUFFER_READ}.
   */
  public int readData(FormatHolder formatHolder, DecoderInputBuffer buffer, boolean formatRequired,
                      boolean loadingFinished, long decodeOnlyUntilUs) {
    int result = infoQueue.readData(formatHolder, buffer, formatRequired, loadingFinished,
            downstreamFormat, extrasHolder);
    switch (result) {
      case C.RESULT_FORMAT_READ:
        downstreamFormat = formatHolder.format;
        return C.RESULT_FORMAT_READ;
      case C.RESULT_BUFFER_READ:
        if (!buffer.isEndOfStream()) {
          if (buffer.timeUs < decodeOnlyUntilUs) {
            buffer.addFlag(C.BUFFER_FLAG_DECODE_ONLY);
          }
          // Read encryption data if the sample is encrypted.
          if (buffer.isEncrypted()) {
            readEncryptionData(buffer, extrasHolder);
          }
          // Write the sample data into the holder.
          buffer.ensureSpaceForWrite(extrasHolder.size);
          readData(extrasHolder.offset, buffer.data, extrasHolder.size);
          // Advance the read head.
          dropDownstreamTo(extrasHolder.nextOffset);
        }
        return C.RESULT_BUFFER_READ;
      case C.RESULT_NOTHING_READ:
        return C.RESULT_NOTHING_READ;
      default:
        throw new IllegalStateException();
    }
  }

  /**
   * Reads encryption data for the current sample.
   * <p>
   * The encryption data is written into {@link DecoderInputBuffer#cryptoInfo}, and
   * {@link BufferExtrasHolder#size} is adjusted to subtract the number of bytes that were read. The
   * same value is added to {@link BufferExtrasHolder#offset}.
   *
   * @param buffer The buffer into which the encryption data should be written.
   * @param extrasHolder The extras holder whose offset should be read and subsequently adjusted.
   */
  private void readEncryptionData(DecoderInputBuffer buffer, BufferExtrasHolder extrasHolder) {
    long offset = extrasHolder.offset;

    // Read the signal byte.
    scratch.reset(1);
    readData(offset, scratch.data, 1);
    offset++;
    byte signalByte = scratch.data[0];
    boolean subsampleEncryption = (signalByte & 0x80) != 0;
    int ivSize = signalByte & 0x7F;

    // Read the initialization vector.
    if (buffer.cryptoInfo.iv == null) {
      buffer.cryptoInfo.iv = new byte[16];
    }
    readData(offset, buffer.cryptoInfo.iv, ivSize);
    offset += ivSize;

    // Read the subsample count, if present.
    int subsampleCount;
    if (subsampleEncryption) {
      scratch.reset(2);
      readData(offset, scratch.data, 2);
      offset += 2;
      subsampleCount = scratch.readUnsignedShort();
    } else {
      subsampleCount = 1;
    }

    // Write the clear and encrypted subsample sizes.
    int[] clearDataSizes = buffer.cryptoInfo.numBytesOfClearData;
    if (clearDataSizes == null || clearDataSizes.length < subsampleCount) {
      clearDataSizes = new int[subsampleCount];
    }
    int[] encryptedDataSizes = buffer.cryptoInfo.numBytesOfEncryptedData;
    if (encryptedDataSizes == null || encryptedDataSizes.length < subsampleCount) {
      encryptedDataSizes = new int[subsampleCount];
    }
    if (subsampleEncryption) {
      int subsampleDataLength = 6 * subsampleCount;
      scratch.reset(subsampleDataLength);
      readData(offset, scratch.data, subsampleDataLength);
      offset += subsampleDataLength;
      scratch.setPosition(0);
      for (int i = 0; i < subsampleCount; i++) {
        clearDataSizes[i] = scratch.readUnsignedShort();
        encryptedDataSizes[i] = scratch.readUnsignedIntToInt();
      }
    } else {
      clearDataSizes[0] = 0;
      encryptedDataSizes[0] = extrasHolder.size - (int) (offset - extrasHolder.offset);
    }

    // Populate the cryptoInfo.
    buffer.cryptoInfo.set(subsampleCount, clearDataSizes, encryptedDataSizes,
            extrasHolder.encryptionKeyId, buffer.cryptoInfo.iv, C.CRYPTO_MODE_AES_CTR);

    // Adjust the offset and size to take into account the bytes read.
    int bytesRead = (int) (offset - extrasHolder.offset);
    extrasHolder.offset += bytesRead;
    extrasHolder.size -= bytesRead;
  }

  /**
   * Reads data from the front of the rolling buffer.
   *
   * @param absolutePosition The absolute position from which data should be read.
   * @param target The buffer into which data should be written.
   * @param length The number of bytes to read.
   */
  private void readData(long absolutePosition, ByteBuffer target, int length) {
    int remaining = length;
    while (remaining > 0) {
      dropDownstreamTo(absolutePosition);
      int positionInDataQueue = (int) totalBytesDropped % dataQueue.length;
      int toCopy = Math.min(remaining, dataQueue.length - positionInDataQueue);
      target.put(dataQueue, positionInDataQueue, toCopy);
      absolutePosition += toCopy;
      remaining -= toCopy;
    }
  }

  /**
   * Reads data from the front of the rolling buffer.
   *
   * @param absolutePosition The absolute position from which data should be read.
   * @param target The array into which data should be written.
   * @param length The number of bytes to read.
   */
  private void readData(long absolutePosition, byte[] target, int length) {
    int bytesRead = 0;
    while (bytesRead < length) {
      dropDownstreamTo(absolutePosition);
      int positionInDataQueue = (int) totalBytesDropped % dataQueue.length;
      int toCopy = Math.min(length - bytesRead, dataQueue.length - positionInDataQueue);
      System.arraycopy(dataQueue, positionInDataQueue, target, bytesRead, toCopy);
      absolutePosition += toCopy;
      bytesRead += toCopy;
    }
  }

  /**
   * Discard any allocations that hold data prior to the specified absolute position, returning
   * them to the allocator.
   *
   * @param absolutePosition The absolute position up to which allocations can be discarded.
   */
  private void dropDownstreamTo(long absolutePosition) {
    totalBytesDropped = absolutePosition;
  }

  // Called by the loading thread.

  /**
   * Sets a listener to be notified of changes to the upstream format.
   *
   * @param listener The listener.
   */
  public void setUpstreamFormatChangeListener(UpstreamFormatChangedListener listener) {
    upstreamFormatChangeListener = listener;
  }

  /**
   * Sets an offset that will be added to the timestamps (and sub-sample timestamps) of samples
   * subsequently queued to the buffer.
   *
   * @param sampleOffsetUs The timestamp offset in microseconds.
   */
  public void setSampleOffsetUs(long sampleOffsetUs) {
    if (this.sampleOffsetUs != sampleOffsetUs) {
      this.sampleOffsetUs = sampleOffsetUs;
      pendingFormatAdjustment = true;
    }
  }

  @Override
  public void format(Format format) {
    Format adjustedFormat = getAdjustedSampleFormat(format, sampleOffsetUs);
    boolean formatChanged = infoQueue.format(adjustedFormat);
    lastUnadjustedFormat = format;
    pendingFormatAdjustment = false;
    if (upstreamFormatChangeListener != null && formatChanged) {
      upstreamFormatChangeListener.onUpstreamFormatChanged(adjustedFormat);
    }
  }

  @Override
  public int sampleData(ExtractorInput input, int length, boolean allowEndOfInput)
          throws IOException, InterruptedException {
    if (!startWriteOperation()) {
      int bytesSkipped = input.skip(length);
      if (bytesSkipped == C.RESULT_END_OF_INPUT) {
        if (allowEndOfInput) {
          return C.RESULT_END_OF_INPUT;
        }
        throw new EOFException();
      }
      return bytesSkipped;
    }
    try {
      if (isReplaceing) {
        return sampleReplacementData(input, length, allowEndOfInput);
      }
      length = prepareForAppend(length);
      int relativeWriteIndex = (int)(totalBytesWritten % dataQueue.length);
      int bytesAppended = input.read(dataQueue, relativeWriteIndex, length);
      if (bytesAppended == C.RESULT_END_OF_INPUT) {
        if (allowEndOfInput) {
          return C.RESULT_END_OF_INPUT;
        }
        throw new EOFException();
      }
      totalBytesWritten += bytesAppended;
      return bytesAppended;
    } finally {
      endWriteOperation();
    }
  }

  private int sampleReplacementData(ExtractorInput input, int length, boolean allowEndOfInput)
          throws IOException, InterruptedException {
    length = prepareForAppendReplacement(length);
    int relativeWriteIndex = (int)(replacementTotalBytesWritten % replacementData.length);
    int bytesAppended = input.read(replacementData, relativeWriteIndex, length);
    if (bytesAppended == C.RESULT_END_OF_INPUT) {
      if (allowEndOfInput) {
        return C.RESULT_END_OF_INPUT;
      }
      throw new EOFException();
    }
    replacementTotalBytesWritten += bytesAppended;
    return bytesAppended;
  }
  @Override
  public void sampleData(ParsableByteArray buffer, int length) {
    if (!startWriteOperation()) {
      buffer.skipBytes(length);
      return;
    }
    if (isReplaceing) {
      sampleReplacementData(buffer, length);
    } else {
      while (length > 0) {
        int thisAppendLength = prepareForAppend(length);
        int relativeWrinteIndex = (int) (totalBytesWritten % dataQueue.length);
        buffer.readBytes(dataQueue, relativeWrinteIndex, thisAppendLength);
        totalBytesWritten += thisAppendLength;
        length -= thisAppendLength;
      }
    }
    endWriteOperation();
  }

  private void sampleReplacementData(ParsableByteArray buffer, int length) {
    while (length > 0) {
      int thisAppendLength = prepareForAppendReplacement(length);
      int relativeWrinteIndex = (int)(replacementTotalBytesWritten % replacementData.length);
      buffer.readBytes(replacementData, relativeWrinteIndex, thisAppendLength);
      replacementTotalBytesWritten += thisAppendLength;
      length -= thisAppendLength;
    }
  }

  @Override
  public void sampleMetadata(long timeUs, @C.BufferFlags int flags, int size, int offset,
                             byte[] encryptionKey) {
    if (pendingFormatAdjustment) {
      format(lastUnadjustedFormat);
    }
    if (!startWriteOperation()) {
      if (isReplaceing) {

      } else {
        infoQueue.commitSampleTimestamp(timeUs);
      }
      return;
    }
    try {
      if (isReplaceing) {
        sampleReplacementMetadata(timeUs, flags, size, offset, encryptionKey);
        return;
      }
      if (pendingSplice) {
        if ((flags & C.BUFFER_FLAG_KEY_FRAME) == 0 || !infoQueue.attemptSplice(timeUs)) {
          return;
        }
        pendingSplice = false;
      }
      if (needKeyframe) {
        if ((flags & C.BUFFER_FLAG_KEY_FRAME) == 0) {
          return;
        }
        needKeyframe = false;
      }
      timeUs += sampleOffsetUs;
      long absoluteOffset = totalBytesWritten - size - offset;
      infoQueue.commitSample(timeUs, flags, absoluteOffset, size, encryptionKey);

    } finally {
      endWriteOperation();
    }
  }

  private void sampleReplacementMetadata(long timeUs, int flags, int size, int offset, byte[] encryptionKey) {
    int writeIndex = replacementAbsoluteWriteIndex % replacementMetaData.length;
    BufferExtrasHolder extras = new BufferExtrasHolder();
    extras.timesUs = timeUs;
    extras.flag = flags;
    extras.size = size;
    extras.offset = replacementTotalBytesWritten - size - offset;
    extras.encryptionKeyId = encryptionKey;
    extras.format = infoQueue.upstreamFormat;
    extras.sourceId = infoQueue.upstreamSourceId;
    replacementMetaData[writeIndex] = extras;
    replacementAbsoluteWriteIndex++;
  }

  // Private methods.

  private boolean startWriteOperation() {
    return state.compareAndSet(STATE_ENABLED, STATE_ENABLED_WRITING);
  }

  private void endWriteOperation() {
    if (!state.compareAndSet(STATE_ENABLED_WRITING, STATE_ENABLED)) {
      clearSampleData();
    }
  }

  private void clearSampleData() {
    infoQueue.clearSampleData();
    totalBytesDropped = 0;
    totalBytesWritten = 0;
    needKeyframe = true;
  }

  /**
   * Prepares the rolling sample buffer for an append of up to {@code length} bytes, returning the
   * number of bytes that can actually be appended.
   */
  private int prepareForAppend(int length) {
    int remaining = dataQueue.length - (int)(totalBytesWritten - totalBytesDropped);
    remaining = Math.min(remaining, dataQueue.length - (int)(totalBytesWritten % dataQueue.length));
    return Math.min(length, remaining);
  }

  /**
   * Prepares the rolling sample buffer for the replacement for an append of up to {@code length} bytes,
   * returning the number of bytes that can actually be appended. The replacement buffer cannot roll
   * it must contain the whole replacement data though. And this buffer is not read until it has been
   * completely populated with the replacement data.
   */
  private int prepareForAppendReplacement(int length) {
    int remaining = replacementData.length - (int)(replacementTotalBytesWritten);
    return Math.min(length, remaining);
  }

  /**
   * Adjusts a {@link Format} to incorporate a sample offset into {@link Format#subsampleOffsetUs}.
   *
   * @param format The {@link Format} to adjust.
   * @param sampleOffsetUs The offset to apply.
   * @return The adjusted {@link Format}.
   */
  private static Format getAdjustedSampleFormat(Format format, long sampleOffsetUs) {
    if (format == null) {
      return null;
    }
    if (sampleOffsetUs != 0 && format.subsampleOffsetUs != Format.OFFSET_SAMPLE_RELATIVE) {
      format = format.copyWithSubsampleOffsetUs(format.subsampleOffsetUs + sampleOffsetUs);
    }
    return format;
  }

  // Function for the replacement
  public void beginReplacement(long startTimeUs, long endTimeUs) {
    lock.lock();
    try {
      if (!isReplaceing) {
        Log.e("REPLACE", id + ": beginning a replacement " + startTimeUs + " " + endTimeUs);
        isReplaceing = true;
        replacementStartTime = startTimeUs;
        replacementEndTime = endTimeUs;
      }
    } finally {
      lock.unlock();
    }
  }

  public void commitReplacement() {
    if (!isReplaceing) {
      return;
    }
    lock.lock();
    try {
      Log.e("REPLACE", id + ": committing a replacement " + replacementStartTime + " " + replacementEndTime);
      // perform the replacement
      // Identify where the replacement must happen
      int startInfoIndex = infoQueue.findIndexAfterTime(replacementStartTime);
      int endInfoIndex = infoQueue.findIndexAfterTime(replacementEndTime);

      if (startInfoIndex == -1) {
        // it's too late
        cancelReplacement();
        return;
      }

      // If the end index has not been found, we consider it to be the writing point because it means
      // the searched time has not been buffered yet.
      endInfoIndex = endInfoIndex == -1 ? infoQueue.relativeWriteIndex : endInfoIndex;
      long dataStartOffset = infoQueue.offsets[startInfoIndex];
      long dataEndOffset = infoQueue.offsets[endInfoIndex];
      if (endInfoIndex == infoQueue.relativeWriteIndex) {
        // When endInfoIndex is the infoQueue writing point, we cannot rely on it to get a proper offset
        // in the dataQueue. But the offset can be found using the offset and the size of the last
        // registered data. We need to check that we don't need to wrap around though (if endInfoIndex ==0).
        int lookAtIndex = endInfoIndex != 0 ? endInfoIndex - 1 : infoQueue.capacity - 1;
        dataEndOffset = infoQueue.offsets[lookAtIndex] + infoQueue.sizes[lookAtIndex];
      }

      // Check if we have room to put the replacement data into the dataQueue and the infoQueue
      if (infoQueue.queueSize + replacementAbsoluteWriteIndex > infoQueue.capacity ||
              totalBytesWritten + replacementTotalBytesWritten - totalBytesDropped > dataQueue.length) {
        cancelReplacement();
        return;
      }

      // Copy data forward or backward if needed and update totalByteWritten
      // First store the data into a temp array, we cannot copy in place it would override the data
      // we want to copy before reading them.
      byte[] temp = new byte[(int) (totalBytesWritten - dataEndOffset)];
      int dataStartCopyIndex = (int) dataEndOffset % dataQueue.length;
      int dataEndCopyIndex = (int) totalBytesWritten % dataQueue.length;
      if (temp.length != 0) {
        if (dataEndCopyIndex > dataStartCopyIndex) {
          // The data doesn't reach the end of the array and wrap around
          System.arraycopy(dataQueue, dataStartCopyIndex, temp, 0, (int) (totalBytesWritten - dataEndOffset));
        } else {
          // The data does reach the end of the buffer and wrap around
          System.arraycopy(dataQueue, dataStartCopyIndex, temp, 0, dataQueue.length - dataStartCopyIndex);
          System.arraycopy(dataQueue, 0, temp, dataQueue.length - dataStartCopyIndex, dataEndCopyIndex);
        }

        // Then perform the copy
        // The starting point is after the data that we will insert
        dataStartCopyIndex = (int) (dataStartOffset + replacementTotalBytesWritten) % dataQueue.length;
        if (dataStartCopyIndex + temp.length <= dataQueue.length) {
          // No wrap
          System.arraycopy(temp, 0, dataQueue, dataStartCopyIndex, temp.length);
        } else {
          // Wrap
          System.arraycopy(temp, 0, dataQueue, dataStartCopyIndex, dataQueue.length - dataStartCopyIndex);
          System.arraycopy(temp, dataQueue.length - dataStartCopyIndex, dataQueue, 0,
              temp.length - dataQueue.length + dataStartCopyIndex);
        }
      }

      // Update the totalBytesWritten to reflect the new position of the writing head after the shift
      // and the insertion
      totalBytesWritten = dataStartOffset + replacementTotalBytesWritten + temp.length;

      // Copy the replacement data into the dataQueue
      dataStartCopyIndex = (int) dataStartOffset % dataQueue.length;
      if (dataStartCopyIndex + replacementTotalBytesWritten <= dataQueue.length) {
        // No wrap
        System.arraycopy(replacementData, 0, dataQueue, dataStartCopyIndex, (int) replacementTotalBytesWritten);
      } else {
        // Wrap
        System.arraycopy(replacementData, 0, dataQueue, dataStartCopyIndex, dataQueue.length - dataStartCopyIndex);
        System.arraycopy(replacementData, dataQueue.length - dataStartCopyIndex, dataQueue, 0,
            (int) (replacementTotalBytesWritten - (dataQueue.length - dataStartCopyIndex)));
      }

      // Copy the metadata forward or backward if needed in the info queue and update the write index

      // To get the amount of metadata to be copied I need to have the endInfoIndex and the writeInfoIndex
      // expressed using the same reference. So if the endInfoIndex is not greater than the startInfoIndex
      // this means that there is a wrap around in between so I add the infoQueue capacity to the
      // end index  to have it expressed in the same referential as the start index.
      endInfoIndex = endInfoIndex >= startInfoIndex ? endInfoIndex : infoQueue.capacity + endInfoIndex;
      int replacedSamplesSize = endInfoIndex - startInfoIndex;
      if (replacementAbsoluteWriteIndex != replacedSamplesSize) {
        // First put the meta data into temp arrays
        int size = -1;
        if (infoQueue.relativeWriteIndex < endInfoIndex) {
          size = infoQueue.relativeWriteIndex + infoQueue.capacity - endInfoIndex;
        } else {
          size = infoQueue.relativeWriteIndex - endInfoIndex;
        }

        if (size != 0) {
          int[] tempSourceIds = new int[size];
          long[] tempOffsets = new long[size];
          int[] tempSizes = new int[size];
          int[] tempFlags = new int[size];
          long[] tempTimesUs = new long[size];
          byte[][] tempEncryptionKeys = new byte[size][];
          Format[] tempFormats = new Format[size];

          // Come back to a relative index, removes the capacity that we might have added before
          int relativeStartIndex = endInfoIndex % infoQueue.capacity;
          if (relativeStartIndex + size <= infoQueue.capacity) {
            System.arraycopy(infoQueue.sourceIds, relativeStartIndex, tempSourceIds, 0, size);
            System.arraycopy(infoQueue.offsets, relativeStartIndex, tempOffsets, 0, size);
            System.arraycopy(infoQueue.sizes, relativeStartIndex, tempSizes, 0, size);
            System.arraycopy(infoQueue.flags, relativeStartIndex, tempFlags, 0, size);
            System.arraycopy(infoQueue.timesUs, relativeStartIndex, tempTimesUs, 0, size);
            System.arraycopy(infoQueue.encryptionKeys, relativeStartIndex, tempEncryptionKeys, 0, size);
            System.arraycopy(infoQueue.formats, relativeStartIndex, tempFormats, 0, size);
          } else {
            // before wrap
            System.arraycopy(infoQueue.sourceIds, relativeStartIndex, tempSourceIds, 0, infoQueue.capacity - relativeStartIndex);
            System.arraycopy(infoQueue.offsets, relativeStartIndex, tempOffsets, 0, infoQueue.capacity - relativeStartIndex);
            System.arraycopy(infoQueue.sizes, relativeStartIndex, tempSizes, 0, infoQueue.capacity - relativeStartIndex);
            System.arraycopy(infoQueue.flags, relativeStartIndex, tempFlags, 0, infoQueue.capacity - relativeStartIndex);
            System.arraycopy(infoQueue.timesUs, relativeStartIndex, tempTimesUs, 0, infoQueue.capacity - relativeStartIndex);
            System.arraycopy(infoQueue.encryptionKeys, relativeStartIndex, tempEncryptionKeys, 0, infoQueue.capacity - relativeStartIndex);
            System.arraycopy(infoQueue.formats, relativeStartIndex, tempFormats, 0, infoQueue.capacity - relativeStartIndex);
            // after wrap
            System.arraycopy(infoQueue.sourceIds, 0, tempSourceIds, infoQueue.capacity - relativeStartIndex, infoQueue.relativeWriteIndex);
            System.arraycopy(infoQueue.offsets, 0, tempOffsets, infoQueue.capacity - relativeStartIndex, infoQueue.relativeWriteIndex);
            System.arraycopy(infoQueue.sizes, 0, tempSizes, infoQueue.capacity - relativeStartIndex, infoQueue.relativeWriteIndex);
            System.arraycopy(infoQueue.flags, 0, tempFlags, infoQueue.capacity - relativeStartIndex, infoQueue.relativeWriteIndex);
            System.arraycopy(infoQueue.timesUs, 0, tempTimesUs, infoQueue.capacity - relativeStartIndex, infoQueue.relativeWriteIndex);
            System.arraycopy(infoQueue.encryptionKeys, 0, tempEncryptionKeys, infoQueue.capacity - relativeStartIndex, infoQueue.relativeWriteIndex);
            System.arraycopy(infoQueue.formats, 0, tempFormats, infoQueue.capacity - relativeStartIndex, infoQueue.relativeWriteIndex);
          }


          // Then copy them in the right position
          relativeStartIndex = (startInfoIndex + replacementAbsoluteWriteIndex) % infoQueue.capacity;
          if (relativeStartIndex + size <= infoQueue.capacity) {
            System.arraycopy(tempSourceIds, 0, infoQueue.sourceIds, relativeStartIndex, size);
            System.arraycopy(tempOffsets, 0, infoQueue.offsets, relativeStartIndex, size);
            System.arraycopy(tempSizes, 0, infoQueue.sizes, relativeStartIndex, size);
            System.arraycopy(tempFlags, 0, infoQueue.flags, relativeStartIndex, size);
            System.arraycopy(tempTimesUs, 0, infoQueue.timesUs, relativeStartIndex, size);
            System.arraycopy(tempEncryptionKeys, 0, infoQueue.encryptionKeys, relativeStartIndex, size);
            System.arraycopy(tempFormats, 0, infoQueue.formats, relativeStartIndex, size);
          } else {
            // before wrap
            System.arraycopy(tempSourceIds, 0, infoQueue.sourceIds, relativeStartIndex, infoQueue.capacity - relativeStartIndex);
            System.arraycopy(tempOffsets, 0, infoQueue.offsets, relativeStartIndex, infoQueue.capacity - relativeStartIndex);
            System.arraycopy(tempSizes, 0, infoQueue.sizes, relativeStartIndex, infoQueue.capacity - relativeStartIndex);
            System.arraycopy(tempFlags, 0, infoQueue.flags, relativeStartIndex, infoQueue.capacity - relativeStartIndex);
            System.arraycopy(tempTimesUs, 0, infoQueue.timesUs, relativeStartIndex, infoQueue.capacity - relativeStartIndex);
            System.arraycopy(tempEncryptionKeys, 0, infoQueue.encryptionKeys, relativeStartIndex, infoQueue.capacity - relativeStartIndex);
            System.arraycopy(tempFormats, 0, infoQueue.formats, relativeStartIndex, infoQueue.capacity - relativeStartIndex);
            // after wrap
            System.arraycopy(tempSourceIds, infoQueue.capacity - relativeStartIndex, infoQueue.sourceIds, 0, size - (infoQueue.capacity - relativeStartIndex));
            System.arraycopy(tempOffsets, infoQueue.capacity - relativeStartIndex, infoQueue.offsets, 0, size - (infoQueue.capacity - relativeStartIndex));
            System.arraycopy(tempSizes, infoQueue.capacity - relativeStartIndex, infoQueue.sizes, 0, size - (infoQueue.capacity - relativeStartIndex));
            System.arraycopy(tempFlags, infoQueue.capacity - relativeStartIndex, infoQueue.flags, 0, size - (infoQueue.capacity - relativeStartIndex));
            System.arraycopy(tempTimesUs, infoQueue.capacity - relativeStartIndex, infoQueue.timesUs, 0, size - (infoQueue.capacity - relativeStartIndex));
            System.arraycopy(tempEncryptionKeys, infoQueue.capacity - relativeStartIndex, infoQueue.encryptionKeys, 0, size - (infoQueue.capacity - relativeStartIndex));
            System.arraycopy(tempFormats, infoQueue.capacity - relativeStartIndex, infoQueue.formats, 0, size - (infoQueue.capacity - relativeStartIndex));
          }
        }

        int shift = replacementAbsoluteWriteIndex - replacedSamplesSize;
        infoQueue.queueSize += shift;
        infoQueue.relativeWriteIndex = (infoQueue.relativeWriteIndex + shift) % infoQueue.capacity;

      }

      // Copy the replacement metadata into de infoqueue
      for (int i = 0; i < replacementAbsoluteWriteIndex; i++) {
        int infoQueueWriteIndex = (startInfoIndex + i) % infoQueue.capacity;
        BufferExtrasHolder holder = replacementMetaData[i];
        infoQueue.sourceIds[infoQueueWriteIndex] = holder.sourceId;
        infoQueue.offsets[infoQueueWriteIndex] = holder.offset + dataStartOffset;
        infoQueue.sizes[infoQueueWriteIndex] = holder.size;
        infoQueue.flags[infoQueueWriteIndex] = holder.flag;
        infoQueue.timesUs[infoQueueWriteIndex] = holder.timesUs;
        infoQueue.encryptionKeys[infoQueueWriteIndex] = holder.encryptionKeyId;
        infoQueue.formats[infoQueueWriteIndex] = holder.format;
        infoQueue.commitSampleTimestamp(holder.timesUs);
      }

      // Shifting the offset of all subsequent metadata
      for (int i = (startInfoIndex + replacementAbsoluteWriteIndex) % infoQueue.capacity;
              i != infoQueue.relativeWriteIndex;
              i = (i + 1) % infoQueue.capacity) {
        int previousIndex = i == 0 ? infoQueue.capacity - 1 : i-1;
        infoQueue.offsets[i] = infoQueue.offsets[previousIndex] + infoQueue.sizes[previousIndex];
      }

      // end the replacement
      cancelReplacement();
    } finally {
      lock.unlock();
    }
  }

  public void cancelReplacement() {
    isReplaceing = false;
    replacementTotalBytesWritten = 0;
    replacementAbsoluteWriteIndex = 0;
  }

  /**
   * Holds information about the samples in the rolling buffer.
   */
  private static final class InfoQueue {

    private static final int SAMPLE_CAPACITY_INCREMENT = 1000;

    private int capacity;

    private int[] sourceIds;
    private long[] offsets;
    private int[] sizes;
    private int[] flags;
    private long[] timesUs;
    private byte[][] encryptionKeys;
    private Format[] formats;

    private int queueSize;
    private int absoluteReadIndex;
    private int relativeReadIndex;
    private int relativeWriteIndex;

    private long largestDequeuedTimestampUs;
    private long largestQueuedTimestampUs;
    private boolean upstreamFormatRequired;
    private Format upstreamFormat;
    private int upstreamSourceId;

    private static int nextId = 0;
    private int id;

    public InfoQueue() {
      capacity = SAMPLE_CAPACITY_INCREMENT;
      sourceIds = new int[capacity];
      offsets = new long[capacity];
      timesUs = new long[capacity];
      flags = new int[capacity];
      sizes = new int[capacity];
      encryptionKeys = new byte[capacity][];
      formats = new Format[capacity];
      largestDequeuedTimestampUs = Long.MIN_VALUE;
      largestQueuedTimestampUs = Long.MIN_VALUE;
      upstreamFormatRequired = true;
      id = nextId++;
    }

    public void clearSampleData() {
      absoluteReadIndex = 0;
      relativeReadIndex = 0;
      relativeWriteIndex = 0;
      queueSize = 0;
    }

    // Called by the consuming thread, but only when there is no loading thread.

    public void resetLargestParsedTimestamps() {
      largestDequeuedTimestampUs = Long.MIN_VALUE;
      largestQueuedTimestampUs = Long.MIN_VALUE;
    }

    /**
     * Returns the current absolute write index.
     */
    public int getWriteIndex() {
      return absoluteReadIndex + queueSize;
    }

    /**
     * Discards samples from the write side of the buffer.
     *
     * @param discardFromIndex The absolute index of the first sample to be discarded.
     * @return The reduced total number of bytes written, after the samples have been discarded.
     */
    public long discardUpstreamSamples(int discardFromIndex) {
      int discardCount = getWriteIndex() - discardFromIndex;
      Assertions.checkArgument(0 <= discardCount && discardCount <= queueSize);

      if (discardCount == 0) {
        if (absoluteReadIndex == 0) {
          // queueSize == absoluteReadIndex == 0, so nothing has been written to the queue.
          return 0;
        }
        int lastWriteIndex = (relativeWriteIndex == 0 ? capacity : relativeWriteIndex) - 1;
        return offsets[lastWriteIndex] + sizes[lastWriteIndex];
      }

      queueSize -= discardCount;
      relativeWriteIndex = (relativeWriteIndex + capacity - discardCount) % capacity;
      // Update the largest queued timestamp, assuming that the timestamps prior to a keyframe are
      // always less than the timestamp of the keyframe itself, and of subsequent frames.
      largestQueuedTimestampUs = Long.MIN_VALUE;
      for (int i = queueSize - 1; i >= 0; i--) {
        int sampleIndex = (relativeReadIndex + i) % capacity;
        largestQueuedTimestampUs = Math.max(largestQueuedTimestampUs, timesUs[sampleIndex]);
        if ((flags[sampleIndex] & C.BUFFER_FLAG_KEY_FRAME) != 0) {
          break;
        }
      }
      return offsets[relativeWriteIndex];
    }

    public void sourceId(int sourceId) {
      upstreamSourceId = sourceId;
    }

    // Called by the consuming thread.

    /**
     * Returns the current absolute read index.
     */
    public int getReadIndex() {
      return absoluteReadIndex;
    }

    /**
     * Peeks the source id of the next sample, or the current upstream source id if the queue is
     * empty.
     */
    public int peekSourceId() {
      return queueSize == 0 ? upstreamSourceId : sourceIds[relativeReadIndex];
    }

    /**
     * Returns whether the queue is empty.
     */
    public synchronized boolean isEmpty() {
      return queueSize == 0;
    }

    /**
     * Returns the upstream {@link Format} in which samples are being queued.
     */
    public synchronized Format getUpstreamFormat() {
      return upstreamFormatRequired ? null : upstreamFormat;
    }

    /**
     * Returns the largest sample timestamp that has been queued since the last {@link #reset}.
     * <p>
     * Samples that were discarded by calling {@link #discardUpstreamSamples(int)} are not
     * considered as having been queued. Samples that were dequeued from the front of the queue are
     * considered as having been queued.
     *
     * @return The largest sample timestamp that has been queued, or {@link Long#MIN_VALUE} if no
     *     samples have been queued.
     */
    public synchronized long getLargestQueuedTimestampUs() {
      return Math.max(largestDequeuedTimestampUs, largestQueuedTimestampUs);
    }

    /**
     * Attempts to read from the queue.
     *
     * @param formatHolder A {@link FormatHolder} to populate in the case of reading a format.
     * @param buffer A {@link DecoderInputBuffer} to populate in the case of reading a sample or the
     *     end of the stream. If a sample is read then the buffer is populated with information
     *     about the sample, but not its data. The size and absolute position of the data in the
     *     rolling buffer is stored in {@code extrasHolder}, along with an encryption id if present
     *     and the absolute position of the first byte that may still be required after the current
     *     sample has been read. May be null if the caller requires that the format of the stream be
     *     read even if it's not changing.
     * @param formatRequired Whether the caller requires that the format of the stream be read even
     *     if it's not changing. A sample will never be read if set to true, however it is still
     *     possible for the end of stream or nothing to be read.
     * @param loadingFinished True if an empty queue should be considered the end of the stream.
     * @param downstreamFormat The current downstream {@link Format}. If the format of the next
     *     sample is different to the current downstream format then a format will be read.
     * @param extrasHolder The holder into which extra sample information should be written.
     * @return The result, which can be {@link C#RESULT_NOTHING_READ}, {@link C#RESULT_FORMAT_READ}
     *     or {@link C#RESULT_BUFFER_READ}.
     */
    @SuppressWarnings("ReferenceEquality")
    public synchronized int readData(FormatHolder formatHolder, DecoderInputBuffer buffer,
                                     boolean formatRequired, boolean loadingFinished, Format downstreamFormat,
                                     BufferExtrasHolder extrasHolder) {
      if (queueSize == 0) {
        if (loadingFinished) {
          buffer.setFlags(C.BUFFER_FLAG_END_OF_STREAM);
          return C.RESULT_BUFFER_READ;
        } else if (upstreamFormat != null
                && (formatRequired || upstreamFormat != downstreamFormat)) {
          formatHolder.format = upstreamFormat;
          return C.RESULT_FORMAT_READ;
        } else {
          return C.RESULT_NOTHING_READ;
        }
      }

      if (formatRequired || formats[relativeReadIndex] != downstreamFormat) {
        formatHolder.format = formats[relativeReadIndex];
        return C.RESULT_FORMAT_READ;
      }

      buffer.timeUs = timesUs[relativeReadIndex];
      buffer.setFlags(flags[relativeReadIndex]);
      extrasHolder.size = sizes[relativeReadIndex];
      extrasHolder.offset = offsets[relativeReadIndex];
      extrasHolder.encryptionKeyId = encryptionKeys[relativeReadIndex];

      largestDequeuedTimestampUs = Math.max(largestDequeuedTimestampUs, buffer.timeUs);
      queueSize--;
      relativeReadIndex++;
      absoluteReadIndex++;
      if (relativeReadIndex == capacity) {
        // Wrap around.
        relativeReadIndex = 0;
      }

      extrasHolder.nextOffset = queueSize > 0 ? offsets[relativeReadIndex]
              : extrasHolder.offset + extrasHolder.size;
      return C.RESULT_BUFFER_READ;
    }

    /**
     * Attempts to locate the keyframe before or at the specified time. If
     * {@code allowTimeBeyondBuffer} is {@code false} then it is also required that {@code timeUs}
     * falls within the buffer.
     *
     * @param timeUs The seek time.
     * @param allowTimeBeyondBuffer Whether the skip can succeed if {@code timeUs} is beyond the end
     *     of the buffer.
     * @return The offset of the keyframe's data if the keyframe was present.
     *     {@link C#POSITION_UNSET} otherwise.
     */
    public synchronized long skipToKeyframeBefore(long timeUs, boolean allowTimeBeyondBuffer) {
      if (queueSize == 0 || timeUs < timesUs[relativeReadIndex]) {
        return C.POSITION_UNSET;
      }

      if (timeUs > largestQueuedTimestampUs && !allowTimeBeyondBuffer) {
        return C.POSITION_UNSET;
      }

      // This could be optimized to use a binary search, however in practice callers to this method
      // often pass times near to the start of the buffer. Hence it's unclear whether switching to
      // a binary search would yield any real benefit.
      int sampleCount = 0;
      int sampleCountToKeyframe = -1;
      int searchIndex = relativeReadIndex;
      while (searchIndex != relativeWriteIndex) {
        if (timesUs[searchIndex] > timeUs) {
          // We've gone too far.
          break;
        } else if ((flags[searchIndex] & C.BUFFER_FLAG_KEY_FRAME) != 0) {
          // We've found a keyframe, and we're still before the seek position.
          sampleCountToKeyframe = sampleCount;
        }
        searchIndex = (searchIndex + 1) % capacity;
        sampleCount++;
      }

      if (sampleCountToKeyframe == -1) {
        return C.POSITION_UNSET;
      }

      queueSize -= sampleCountToKeyframe;
      relativeReadIndex = (relativeReadIndex + sampleCountToKeyframe) % capacity;
      absoluteReadIndex += sampleCountToKeyframe;
      return offsets[relativeReadIndex];
    }

    // Called by the loading thread.

    public synchronized boolean format(Format format) {
      if (format == null) {
        upstreamFormatRequired = true;
        return false;
      }
      upstreamFormatRequired = false;
      if (Util.areEqual(format, upstreamFormat)) {
        // Suppress changes between equal formats so we can use referential equality in readData.
        return false;
      } else {
        upstreamFormat = format;
        return true;
      }
    }

    public synchronized void commitSample(long timeUs, @C.BufferFlags int sampleFlags, long offset,
                                          int size, byte[] encryptionKey) {
      Assertions.checkState(!upstreamFormatRequired);
      commitSampleTimestamp(timeUs);
      timesUs[relativeWriteIndex] = timeUs;
      offsets[relativeWriteIndex] = offset;
      sizes[relativeWriteIndex] = size;
      flags[relativeWriteIndex] = sampleFlags;
      encryptionKeys[relativeWriteIndex] = encryptionKey;
      formats[relativeWriteIndex] = upstreamFormat;
      sourceIds[relativeWriteIndex] = upstreamSourceId;
      // Increment the write index.
      queueSize++;
      if (queueSize == capacity) {
        // Increase the capacity.
        int newCapacity = capacity + SAMPLE_CAPACITY_INCREMENT;
        int[] newSourceIds = new int[newCapacity];
        long[] newOffsets = new long[newCapacity];
        long[] newTimesUs = new long[newCapacity];
        int[] newFlags = new int[newCapacity];
        int[] newSizes = new int[newCapacity];
        byte[][] newEncryptionKeys = new byte[newCapacity][];
        Format[] newFormats = new Format[newCapacity];
        int beforeWrap = capacity - relativeReadIndex;
        System.arraycopy(offsets, relativeReadIndex, newOffsets, 0, beforeWrap);
        System.arraycopy(timesUs, relativeReadIndex, newTimesUs, 0, beforeWrap);
        System.arraycopy(flags, relativeReadIndex, newFlags, 0, beforeWrap);
        System.arraycopy(sizes, relativeReadIndex, newSizes, 0, beforeWrap);
        System.arraycopy(encryptionKeys, relativeReadIndex, newEncryptionKeys, 0, beforeWrap);
        System.arraycopy(formats, relativeReadIndex, newFormats, 0, beforeWrap);
        System.arraycopy(sourceIds, relativeReadIndex, newSourceIds, 0, beforeWrap);
        int afterWrap = relativeReadIndex;
        System.arraycopy(offsets, 0, newOffsets, beforeWrap, afterWrap);
        System.arraycopy(timesUs, 0, newTimesUs, beforeWrap, afterWrap);
        System.arraycopy(flags, 0, newFlags, beforeWrap, afterWrap);
        System.arraycopy(sizes, 0, newSizes, beforeWrap, afterWrap);
        System.arraycopy(encryptionKeys, 0, newEncryptionKeys, beforeWrap, afterWrap);
        System.arraycopy(formats, 0, newFormats, beforeWrap, afterWrap);
        System.arraycopy(sourceIds, 0, newSourceIds, beforeWrap, afterWrap);
        offsets = newOffsets;
        timesUs = newTimesUs;
        flags = newFlags;
        sizes = newSizes;
        encryptionKeys = newEncryptionKeys;
        formats = newFormats;
        sourceIds = newSourceIds;
        relativeReadIndex = 0;
        relativeWriteIndex = capacity;
        queueSize = capacity;
        capacity = newCapacity;
      } else {
        relativeWriteIndex++;
        if (relativeWriteIndex == capacity) {
          // Wrap around.
          relativeWriteIndex = 0;
        }
      }
    }

    public synchronized void commitSampleTimestamp(long timeUs) {
      largestQueuedTimestampUs = Math.max(largestQueuedTimestampUs, timeUs);
    }

    /**
     * Attempts to discard samples from the tail of the queue to allow samples starting from the
     * specified timestamp to be spliced in.
     *
     * @param timeUs The timestamp at which the splice occurs.
     * @return Whether the splice was successful.
     */
    public synchronized boolean attemptSplice(long timeUs) {
      if (largestDequeuedTimestampUs >= timeUs) {
        return false;
      }
      int retainCount = queueSize;
      while (retainCount > 0
              && timesUs[(relativeReadIndex + retainCount - 1) % capacity] >= timeUs) {
        retainCount--;
      }
      discardUpstreamSamples(absoluteReadIndex + retainCount);
      return true;
    }

    // Functions for the replacement
    public int findIndexAfterTime(long timeUs) {
      int result = -1;
      for (int i = relativeReadIndex; i != relativeWriteIndex; i = (i+1) % capacity) {
        if (timesUs[i] >= timeUs) {
          result = i;
          break;
        }
      }
      return result;
    }

  }

  /**
   * Holds additional buffer information not held by {@link DecoderInputBuffer}.
   */
  private static final class BufferExtrasHolder {

    public int size;
    public long offset;
    public long nextOffset;
    public byte[] encryptionKeyId;

    public int sourceId;
    public long timesUs;;
    public int flag;
    public int sizes;
    public Format format;
  }

}
