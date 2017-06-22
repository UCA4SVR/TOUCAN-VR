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
import com.google.android.exoplayer2.upstream.Allocation;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.ParsableByteArray;
import com.google.android.exoplayer2.util.Util;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import fr.unice.i3s.uca4svr.toucan_vr.mediaplayer.upstream.UnboundedAllocator;

/**
 * A {@link TrackOutput} that buffers extracted samples in a queue and allows for consumption from
 * that queue. Also allows the replacement of any sample in the queue.
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

  private final UnboundedAllocator allocator;

  private final InfoQueue infoQueue;
  private final List<Allocation> dataQueue;
  private final BufferExtrasHolder extrasHolder;
  private final ParsableByteArray scratch;
  private final AtomicInteger state;

  // Accessed only by the consuming thread.
  private long totalBytesDropped = 0;
  private long currentOffset = 0;
  private Format downstreamFormat;

  // Accessed only by the loading thread (or the consuming thread when there is no loading thread).
  private boolean pendingFormatAdjustment;
  private Format lastUnadjustedFormat;
  private long sampleOffsetUs;
  private long totalBytesWritten;
  private Allocation lastAllocation;
  private int lastAllocationOffset;
  private boolean needKeyframe;
  private UpstreamFormatChangedListener upstreamFormatChangeListener;

  private final ReentrantLock lock = new ReentrantLock();

  private ReplacementInfo replacement = null;
  List<Allocation> replacementAllocations = null;
  private Allocation replacementAllocation;
  private int replacementAllocationOffset;

  /**
   * @param allocator An {@link Allocator} from which allocations for sample data can be obtained.
   *                  Must inherit from {@link UnboundedAllocator}.
   */
  public ReplacementTrackOutput(Allocator allocator) {
    this.allocator = (UnboundedAllocator) allocator;
    infoQueue = new InfoQueue(lock);
    ArrayList<Allocation> queue = new ArrayList<>();
    dataQueue = Collections.synchronizedList(queue);
    extrasHolder = new BufferExtrasHolder();
    scratch = new ParsableByteArray(INITIAL_SCRATCH_SIZE);
    state = new AtomicInteger();
    needKeyframe = true;
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
    dropUpstreamFrom(totalBytesWritten);
  }

  /**
   * Discards data from the write side of the buffer. Data is discarded from the specified absolute
   * position. Any allocations that are fully discarded are returned to the allocator.
   *
   * @param absolutePosition The absolute position of the first sample to be discarded.
   */
  private void dropUpstreamFrom(long absolutePosition) {
    long position = totalBytesDropped;
    int index = 0;

    while (position < absolutePosition && index < dataQueue.size()) {
      position = position + dataQueue.get(index).data.length;
      index++;
    }
    // Discard the allocations.
    while (index < dataQueue.size()) {
      allocator.release(dataQueue.remove(index));
    }
    // Reset the offset to 0 if everything has been discarded
    if (index == 0) {
      currentOffset = 0;
    }
    // Update lastAllocation and lastAllocationOffset to reflect the new position.
    lastAllocation = dataQueue.get(dataQueue.size() - 1);
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
   * samples have been queued.
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
   * @param timeUs                The seek time.
   * @param allowTimeBeyondBuffer Whether the skip can succeed if {@code timeUs} is beyond the end
   *                              of the buffer.
   * @return Whether the skip was successful.
   */
  public boolean skipToKeyframeBefore(long timeUs, boolean allowTimeBeyondBuffer) {
    long absolutePosition = infoQueue.skipToKeyframeBefore(timeUs, allowTimeBeyondBuffer);
    if (absolutePosition == C.POSITION_UNSET) {
      return false;
    }
    dropDownstreamTo(absolutePosition);
    return true;
  }

  /**
   * Attempts to read from the queue.
   *
   * @param formatHolder      A {@link FormatHolder} to populate in the case of reading a format.
   * @param buffer            A {@link DecoderInputBuffer} to populate in the case of reading a sample or the
   *                          end of the stream. If the end of the stream has been reached, the
   *                          {@link C#BUFFER_FLAG_END_OF_STREAM} flag will be set on the buffer.
   * @param formatRequired    Whether the caller requires that the format of the stream be read even if
   *                          it's not changing. A sample will never be read if set to true, however it is still possible
   *                          for the end of stream or nothing to be read.
   * @param loadingFinished   True if an empty queue should be considered the end of the stream.
   * @param decodeOnlyUntilUs If a buffer is read, the {@link C#BUFFER_FLAG_DECODE_ONLY} flag will
   *                          be set if the buffer's timestamp is less than this value.
   * @return The result, which can be {@link C#RESULT_NOTHING_READ}, {@link C#RESULT_FORMAT_READ} or
   * {@link C#RESULT_BUFFER_READ}.
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
          BufferExtrasHolder toStore = new BufferExtrasHolder();
          toStore.encryptionKeyId = extrasHolder.encryptionKeyId;
          toStore.nextOffset = extrasHolder.nextOffset;
          toStore.offset = extrasHolder.offset;
          toStore.size = extrasHolder.size;
          buffer.ensureSpaceForWrite(extrasHolder.size);
          readData(extrasHolder.offset, buffer.data, extrasHolder.size);
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
   * <p>
   * TODO: This is not working properly right now. Must be investigated and fixed to play DRM content.
   *
   * @param buffer       The buffer into which the encryption data should be written.
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
   * Reads data from the front of the buffer.
   *
   * @param absolutePosition The absolute position from which data should be read.
   * @param target           The buffer into which data should be written.
   * @param length           The number of bytes to read.
   */
  private void readData(long absolutePosition, ByteBuffer target, int length) {
    int remaining = length;
    dropDownstreamTo(absolutePosition);
    while (remaining > 0) {
      if (dataQueue.size() > 0 && currentOffset >= dataQueue.get(0).data.length) {
        dropDownstreamTo(1);
      }

      Allocation allocation = dataQueue.get(0);
      int toCopy = (int) Math.min(remaining, allocation.data.length - currentOffset);
      target.put(allocation.data, allocation.translateOffset((int) currentOffset), toCopy);
      currentOffset += toCopy;
      remaining -= toCopy;
    }
    if (dataQueue.size() > 0 && currentOffset >= dataQueue.get(0).data.length) {
      dropDownstreamTo(1);
    } else {
      currentOffset = 0;
    }
  }

  /**
   * Reads data from the front of the buffer.
   *
   * @param absolutePosition The relative position from which data should be read.
   * @param target           The array into which data should be written.
   * @param length           The number of bytes to read.
   */
  private void readData(long absolutePosition, byte[] target, int length) {
    int bytesRead = 0;
    dropDownstreamTo(absolutePosition);
    while (bytesRead < length) {
      if (currentOffset >= dataQueue.get(0).data.length) {
        dropDownstreamTo(1);
      }
      Allocation allocation = dataQueue.get(0);
      int toCopy = (int) Math.min(length - bytesRead, allocation.data.length - currentOffset);
      System.arraycopy(allocation.data, allocation.translateOffset((int) currentOffset), target,
              bytesRead, toCopy);
      currentOffset += toCopy;
      bytesRead += toCopy;
    }
    if (currentOffset >= dataQueue.get(0).data.length) {
      dropDownstreamTo(1);
    }
  }

  /**
   * Discard any allocations that hold data prior to the specified absolute position, returning
   * them to the allocator.
   *
   * @param relativeIndex The absolute position up to which allocations can be discarded.
   */
  private void dropDownstreamTo(int relativeIndex) {
    try {
      //lock.lock();
      for (int i = 0; i < relativeIndex; i++) {
        totalBytesDropped += dataQueue.get(0).data.length;
        allocator.release(dataQueue.remove(0));
        currentOffset = 0;
      }
    }finally {
      //lock.unlock();
    }
  }

  private void dropDownstreamTo(long absolutePosition) {
    try {
      //lock.lock();
      while (dataQueue.size() > 0 && totalBytesDropped + dataQueue.get(0).data.length <= absolutePosition) {
        totalBytesDropped += dataQueue.get(0).data.length;
        allocator.release(dataQueue.remove(0));
        if (currentOffset > 0) {
          currentOffset = 0;
        }
      }
      currentOffset = absolutePosition - totalBytesDropped;
    } finally {
      //lock.unlock();
    }
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

  public void replaceSample(List<Allocation> replacementData, int length, long timeUs,
                            @C.BufferFlags int flags, int size, byte[] encryptionKey) {
    try {
      lock.lock();
      int infoQueueIndex = infoQueue.findIndex(timeUs);
      if (infoQueueIndex > 0) {
        int dataQueueIndex = locateOffset(infoQueue.offsets.get(infoQueueIndex));
        int amountToDiscard = infoQueue.sizes.get(infoQueueIndex);
        while (amountToDiscard > 0) {
          amountToDiscard -= dataQueue.get(dataQueueIndex).data.length;
          if (amountToDiscard >= 0) {
            allocator.release(dataQueue.get(dataQueueIndex));
            dataQueue.remove(dataQueueIndex);
          } else {
            throw new RuntimeException("Shouldn't be discarding too much data.");
          }
        }

        // TODO: There might be issues when replacing with a new format. Need to investigate more.
        if (pendingFormatAdjustment) {
          format(lastUnadjustedFormat);
        }

        int offsetShift = size - infoQueue.sizes.get(infoQueueIndex);
        for (int i = replacementData.size() - 1; i >= 0; i--) {
          dataQueue.add(dataQueueIndex, replacementData.get(i));
        }

        infoQueue.flags.set(infoQueueIndex, flags);
        infoQueue.sizes.set(infoQueueIndex, size);
        infoQueue.encryptionKeys.set(infoQueueIndex, encryptionKey);
        infoQueue.formats.set(infoQueueIndex, infoQueue.upstreamFormat);

        for (int index = infoQueueIndex + 1; index < infoQueue.offsets.size(); index++) {
          infoQueue.offsets.set(index, infoQueue.offsets.get(index) + offsetShift);
        }
      }
    } finally {
      lock.unlock();
    }
  }

  /**
   * Must be called from a block synchronized on the dataQueue.
   *
   * @param offset The starting offset of the searched sample of the data queue
   * @return The index of the sample of the data queue starting with the provided offset
   */
  private int locateOffset(long offset) {
    long currentOffset = totalBytesDropped;
    int index = 0;
    while (currentOffset < offset) {
      currentOffset += dataQueue.get(index).data.length;
      index++;
    }
    if (currentOffset == offset) {
      return index;
    }
    throw new RuntimeException("Offset must lead to the beginning of a sample.");
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
      lock.lock();
      if (replacement != null) {
        return sampleForReplacement(input, length, allowEndOfInput);
      }
      length = prepareForAppend(length);
      return fillAllocation(input, length, allowEndOfInput, lastAllocation, lastAllocationOffset,
              false);
    } finally {
      endWriteOperation();
      lock.unlock();
    }
  }

  @Override
  public void sampleData(ParsableByteArray buffer, int length) {
    try {
      lock.lock();
      if (!startWriteOperation()) {
        buffer.skipBytes(length);
        return;
      }
      if (replacement != null) {
        sampleForReplacement(buffer, length);
        return;
      }
      length = prepareForAppend(length);
      fillAllocation(buffer, length, lastAllocation, lastAllocationOffset, false);
    } finally {
      endWriteOperation();
      lock.unlock();
    }
  }

  @Override
  public void sampleMetadata(long timeUs, @C.BufferFlags int flags, int size, int offset,
                             byte[] encryptionKey) {
    if (pendingFormatAdjustment) {
      format(lastUnadjustedFormat);
    }
    if (!startWriteOperation()) {
      infoQueue.commitSampleTimestamp(timeUs);
      return;
    }
    try {
      lock.lock();
      if (replacement != null) {
        sampleMetadataForReplacement(timeUs, flags, size, offset, encryptionKey);
        return;
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
      lock.unlock();
    }
  }

  private int sampleForReplacement(ExtractorInput input, int length, boolean allowEndOfInput) throws IOException, InterruptedException {
    Log.e("REPLACE", "sample for replace extractor " + length);
    if (replacementAllocations == null) {
      ArrayList<Allocation> queue = new ArrayList<>();
      replacementAllocations = Collections.synchronizedList(queue);
    }
    length = prepareForReplacement(length);
    return fillAllocation(input, length, allowEndOfInput, replacementAllocation,
            replacementAllocationOffset, true);
  }

  private void sampleForReplacement(ParsableByteArray buffer, int length) {
    Log.e("REPLACE", "sample for replace byte array " + length);
    if (replacementAllocations == null) {
      ArrayList<Allocation> queue = new ArrayList<>();
      replacementAllocations = Collections.synchronizedList(queue);
    }
    length = prepareForReplacement(length);
    fillAllocation(buffer, length, replacementAllocation, replacementAllocationOffset, true);
  }

  private void sampleMetadataForReplacement(long timeUs, int flags, int size, int offset, byte[] encryptionKey) {
    Log.e("REPLACE", "sample metadata " + size);
    //*
    replaceSample(replacementAllocations, size, timeUs, flags, size, encryptionKey);
    replacementAllocations = null;
    replacement = null;
    replacementAllocation = null;
    replacementAllocationOffset = 0;
    //*/
  }

  private int fillAllocation(ExtractorInput input, int length, boolean allowEndOfInput,
                             Allocation allocation, int allocationOffset, boolean replacement) throws IOException, InterruptedException {
    int bytesAppended = input.read(allocation.data,
            allocation.translateOffset(allocationOffset), length);
    if (bytesAppended == C.RESULT_END_OF_INPUT) {
      if (allowEndOfInput) {
        return C.RESULT_END_OF_INPUT;
      }
      throw new EOFException();
    }
    if (!replacement) {
      totalBytesWritten += bytesAppended;
      lastAllocationOffset += bytesAppended;
    } else {
      replacementAllocationOffset += bytesAppended;
    }
    return bytesAppended;
  }

  private void fillAllocation(ParsableByteArray buffer, int length, Allocation allocation,
                              int offset, boolean replacement) {
    while (length > 0) {
      int thisAppendLength = replacement ? prepareForReplacement(length) : prepareForAppend(length);
      buffer.readBytes(allocation.data, allocation.translateOffset(offset),
              thisAppendLength);
      if (!replacement) {
        lastAllocationOffset += thisAppendLength;
        totalBytesWritten += thisAppendLength;
      } else {
        replacementAllocationOffset += thisAppendLength;
      }
      offset += thisAppendLength;
      length -= thisAppendLength;
    }
  }

  public void beginReplacement(long startTimeUs, long endTimeUs) {
    try {
      lock.lock();
      if (this.replacement == null) {
        this.replacement = new ReplacementInfo(startTimeUs, endTimeUs);
      } else {
        throw new RuntimeException("Cannot have two replacements at the same time !");
      }
    } finally {
      lock.unlock();
    }
  }

  public void cancelReplacement() {
    lock.lock();
    replacement = null;
    replacementAllocation = null;
    lock.unlock();
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
    allocator.release(dataQueue.toArray(new Allocation[dataQueue.size()]));
    dataQueue.clear();
    allocator.trim();
    totalBytesDropped = 0;
    totalBytesWritten = 0;
    lastAllocation = null;
    needKeyframe = true;
  }

  /**
   * Prepares the rolling sample buffer for an append of up to {@code length} bytes, returning the
   * number of bytes that can actually be appended.
   */
  private int prepareForAppend(int length) {
    if (lastAllocation == null || lastAllocationOffset >= lastAllocation.data.length) {
      lastAllocationOffset = 0;
      lastAllocation = allocator.allocate(length);
      dataQueue.add(lastAllocation);
    }
    return lastAllocation.data.length - lastAllocationOffset;
  }

  private int prepareForReplacement(int length) {
    if (replacementAllocation == null || replacementAllocationOffset >= replacementAllocation.data.length) {
      replacementAllocationOffset = 0;
      replacementAllocation = allocator.allocate(length);
      replacementAllocations.add(replacementAllocation);
    }
    return replacementAllocation.data.length - replacementAllocationOffset;
  }

  /**
   * Adjusts a {@link Format} to incorporate a sample offset into {@link Format#subsampleOffsetUs}.
   *
   * @param format         The {@link Format} to adjust.
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

  /**
   * Holds information about the samples in the buffer.
   */
  private static final class InfoQueue {

    private List<Integer> sourceIds;
    private List<Long> offsets;
    private List<Integer> sizes;
    private List<Integer> flags;
    private List<Long> timesUs;
    private List<byte[]> encryptionKeys;
    private List<Format> formats;

    private int absoluteReadIndex;

    private long largestDequeuedTimestampUs;
    private long largestQueuedTimestampUs;
    private boolean upstreamFormatRequired;
    private Format upstreamFormat;
    private int upstreamSourceId;

    private final int batchReadingLimit = 20;
    private int readCounter = 0;

    private ReentrantLock lock;

    public InfoQueue(ReentrantLock lock) {
      sourceIds = new ArrayList<>();
      offsets = new ArrayList<>();
      timesUs = new ArrayList<>();
      flags = new ArrayList<>();
      sizes = new ArrayList<>();
      encryptionKeys = new ArrayList<>();
      formats = new ArrayList<>();
      sourceIds = Collections.synchronizedList(sourceIds);
      offsets = Collections.synchronizedList(offsets);
      timesUs = Collections.synchronizedList(timesUs);
      flags = Collections.synchronizedList(flags);
      sizes = Collections.synchronizedList(sizes);
      encryptionKeys = Collections.synchronizedList(encryptionKeys);
      formats = Collections.synchronizedList(formats);
      largestDequeuedTimestampUs = Long.MIN_VALUE;
      largestQueuedTimestampUs = Long.MIN_VALUE;
      upstreamFormatRequired = true;
      this.lock = lock;
    }

    public void clearSampleData() {
      try {
        //lock.lock();
        absoluteReadIndex = 0;
        sourceIds.clear();
        offsets.clear();
        sizes.clear();
        flags.clear();
        timesUs.clear();
        encryptionKeys.clear();
        formats.clear();
      } finally {
        //lock.unlock();
      }
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
      return absoluteReadIndex + queueSize();
    }

    public int queueSize() {
      return offsets.size();
    }

    /**
     * Discards samples from the write side of the buffer.
     *
     * @param discardFromIndex The absolute index of the first sample to be discarded.
     * @return The reduced total number of bytes written, after the samples have been discarded.
     */
    public long discardUpstreamSamples(int discardFromIndex) {
      int discardCount = getWriteIndex() - discardFromIndex;
      Assertions.checkArgument(0 <= discardCount && discardCount <= queueSize());

      if (discardCount == 0) {
        if (absoluteReadIndex == 0) {
          // queueSize == absoluteReadIndex == 0, so nothing has been written to the queue.
          return 0;
        }
      } else {
        try {
          //lock.lock();
          for (int j = 0; j < discardCount; j++) {
            int index = queueSize() - 1;
            sourceIds.remove(index);
            offsets.remove(index);
            sizes.remove(index);
            flags.remove(index);
            timesUs.remove(index);
            encryptionKeys.remove(index);
            formats.remove(index);
          }
        } finally {
          //lock.unlock();
        }

        // Update the largest queued timestamp, assuming that the timestamps prior to a keyframe are
        // always less than the timestamp of the keyframe itself, and of subsequent frames.
        largestQueuedTimestampUs = Long.MIN_VALUE;
        for (int i = queueSize() - 1; i >= 0; i--) {
          largestQueuedTimestampUs = Math.max(largestQueuedTimestampUs, timesUs.get(i));
          if ((flags.get(i) & C.BUFFER_FLAG_KEY_FRAME) != 0) {
            break;
          }
        }
      }
      return offsets.get(queueSize() - 1) + sizes.get(queueSize() - 1);
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
      try {
        //lock.lock();
        return queueSize() == 0 ? upstreamSourceId : sourceIds.get(0);
      } finally {
        //lock.unlock();
      }
    }

    /**
     * Returns whether the queue is empty.
     */
    public boolean isEmpty() {
      try {
        //lock.lock();
        return queueSize() == 0;
      } finally {
        //lock.unlock();
      }
    }

    /**
     * Returns the upstream {@link Format} in which samples are being queued.
     */
    public Format getUpstreamFormat() {
      try {
        //lock.lock();
        return upstreamFormatRequired ? null : upstreamFormat;
      } finally {
        //lock.unlock();
      }
    }

    /**
     * Returns the largest sample timestamp that has been queued since the last {@link #reset}.
     * <p>
     * Samples that were discarded by calling {@link #discardUpstreamSamples(int)} are not
     * considered as having been queued. Samples that were dequeued from the front of the queue are
     * considered as having been queued.
     *
     * @return The largest sample timestamp that has been queued, or {@link Long#MIN_VALUE} if no
     * samples have been queued.
     */
    public long getLargestQueuedTimestampUs() {
      return Math.max(largestDequeuedTimestampUs, largestQueuedTimestampUs);
    }

    /**
     * Attempts to read from the queue.
     *
     * @param formatHolder     A {@link FormatHolder} to populate in the case of reading a format.
     * @param buffer           A {@link DecoderInputBuffer} to populate in the case of reading a sample or the
     *                         end of the stream. If a sample is read then the buffer is populated with information
     *                         about the sample, but not its data. The size and absolute position of the data in the
     *                         rolling buffer is stored in {@code extrasHolder}, along with an encryption id if present
     *                         and the absolute position of the first byte that may still be required after the current
     *                         sample has been read. May be null if the caller requires that the format of the stream be
     *                         read even if it's not changing.
     * @param formatRequired   Whether the caller requires that the format of the stream be read even
     *                         if it's not changing. A sample will never be read if set to true, however it is still
     *                         possible for the end of stream or nothing to be read.
     * @param loadingFinished  True if an empty queue should be considered the end of the stream.
     * @param downstreamFormat The current downstream {@link Format}. If the format of the next
     *                         sample is different to the current downstream format then a format will be read.
     * @param extrasHolder     The holder into which extra sample information should be written.
     * @return The result, which can be {@link C#RESULT_NOTHING_READ}, {@link C#RESULT_FORMAT_READ}
     * or {@link C#RESULT_BUFFER_READ}.
     */
    @SuppressWarnings("ReferenceEquality")
    public int readData(FormatHolder formatHolder, DecoderInputBuffer buffer,
                                     boolean formatRequired, boolean loadingFinished, Format downstreamFormat,
                                     BufferExtrasHolder extrasHolder) {
      try {
        //lock.lock();
        if (queueSize() == 0) {
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

        if (formatRequired /*|| formats.get(0) != downstreamFormat*/) {
          formatHolder.format = formats.get(0);
          return C.RESULT_FORMAT_READ;
        }

        if (readCounter >= batchReadingLimit) {
          readCounter = 0;
          return C.RESULT_NOTHING_READ;
        }

        buffer.timeUs = timesUs.get(0);
        buffer.setFlags(flags.get(0));
        extrasHolder.size = sizes.get(0);
        extrasHolder.offset = offsets.get(0);
        extrasHolder.encryptionKeyId = encryptionKeys.get(0);

        largestDequeuedTimestampUs = Math.max(largestDequeuedTimestampUs, buffer.timeUs);
        timesUs.remove(0);
        flags.remove(0);
        sizes.remove(0);
        offsets.remove(0);
        encryptionKeys.remove(0);
        formats.remove(0);
        sourceIds.remove(0);
        absoluteReadIndex++;

        extrasHolder.nextOffset = queueSize() > 0 ? offsets.get(0)
                : extrasHolder.offset + extrasHolder.size;
        readCounter++;
        return C.RESULT_BUFFER_READ;
      } finally {
        //lock.unlock();
      }
    }

    /**
     * Attempts to locate the keyframe before or at the specified time. If
     * {@code allowTimeBeyondBuffer} is {@code false} then it is also required that {@code timeUs}
     * falls within the buffer.
     *
     * @param timeUs                The seek time.
     * @param allowTimeBeyondBuffer Whether the skip can succeed if {@code timeUs} is beyond the end
     *                              of the buffer.
     * @return The offset of the keyframe's data if the keyframe was present.
     * {@link C#POSITION_UNSET} otherwise.
     */
    public long skipToKeyframeBefore(long timeUs, boolean allowTimeBeyondBuffer) {
      try {
        //lock.lock();
        if (queueSize() == 0 || timeUs < timesUs.get(0)) {
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
        int searchIndex = 0;
        while (searchIndex != queueSize()) {
          if (timesUs.get(searchIndex) > timeUs) {
            // We've gone too far.
            break;
          } else if ((flags.get(searchIndex) & C.BUFFER_FLAG_KEY_FRAME) != 0) {
            // We've found a keyframe, and we're still before the seek position.
            sampleCountToKeyframe = sampleCount;
          }
          searchIndex = (searchIndex + 1);
          sampleCount++;
        }

        if (sampleCountToKeyframe == -1) {
          return C.POSITION_UNSET;
        }

        for (int i = 0; i < sampleCountToKeyframe; i++) {
          timesUs.remove(0);
          flags.remove(0);
          sizes.remove(0);
          offsets.remove(0);
          encryptionKeys.remove(0);
        }
        absoluteReadIndex += sampleCountToKeyframe;
        long result = offsets.get(0);
        return result;
      } finally {
        //lock.unlock();
      }
    }

    // Called by the loading thread.

    public boolean format(Format format) {
      try {
        //lock.lock();
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
      } finally {
        //lock.unlock();
      }
    }

    public void commitSample(long timeUs, @C.BufferFlags int sampleFlags, long offset,
                                          int size, byte[] encryptionKey) {
      Assertions.checkState(!upstreamFormatRequired);
      commitSampleTimestamp(timeUs);
      //lock.lock();
      timesUs.add(timeUs);
      offsets.add(offset);
      sizes.add(size);
      flags.add(sampleFlags);
      encryptionKeys.add(encryptionKey);
      formats.add(upstreamFormat);
      sourceIds.add(upstreamSourceId);
      //lock.unlock();
    }

    public void commitSampleTimestamp(long timeUs) {
      try {
        //lock.lock();
        largestQueuedTimestampUs = Math.max(largestQueuedTimestampUs, timeUs);
      } finally {
        //lock.unlock();
      }
    }

    /**
     * Finds the index of the sample in the info queue with the specified offset
     *
     * @param timeUs The time of the sample for which we want the offset
     * @return The index of the sample in the infoQueue if it was found, -1 else.
     */
    public int findIndex(long timeUs) {
      try {
        //lock.lock();
        int index = 0;
        while (index < timesUs.size() && timesUs.get(index) < timeUs) {
          index++;
        }
        if (index < timesUs.size() && timesUs.get(index) == timeUs) {
          return index;
        }
        return -1;
      } finally {
        //lock.unlock();
      }
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
  }

  private final class ReplacementInfo {
    private long startTime;
    private long endTime;

    public ReplacementInfo(long startTime, long endTime) {
      this.startTime = startTime;
      this.endTime = endTime;
    }

    public long getStartTime() {
      return this.startTime;
    }

    public long getEndTime() {
      return this.endTime;
    }
  }

}
