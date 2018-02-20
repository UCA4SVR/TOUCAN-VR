/*
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
 */
package fr.unice.i3s.uca4svr.toucan_vr.mediaplayer.source.chunk;

import android.util.Log;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.FormatHolder;
import com.google.android.exoplayer2.decoder.DecoderInputBuffer;
import com.google.android.exoplayer2.source.AdaptiveMediaSourceEventListener.EventDispatcher;
import com.google.android.exoplayer2.source.SampleStream;
import com.google.android.exoplayer2.source.SequenceableLoader;
import com.google.android.exoplayer2.source.chunk.Chunk;
import com.google.android.exoplayer2.source.chunk.ChunkHolder;
import com.google.android.exoplayer2.source.chunk.ChunkSource;
import com.google.android.exoplayer2.source.chunk.MediaChunk;
import com.google.android.exoplayer2.source.dash.DefaultDashSRDChunkSource;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.upstream.Loader;
import com.google.android.exoplayer2.util.Assertions;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import fr.unice.i3s.uca4svr.toucan_vr.dynamicEditing.DynamicEditingHolder;
import fr.unice.i3s.uca4svr.toucan_vr.mediaplayer.extractor.ReplacementTrackOutput;
import fr.unice.i3s.uca4svr.toucan_vr.tilespicker.TilesPicker;
import fr.unice.i3s.uca4svr.toucan_vr.tracking.ReplacementTracker;
import fr.unice.i3s.uca4svr.toucan_vr.tracking.TileQualityTracker;

/**
 * A {@link SampleStream} that loads media in {@link Chunk}s, obtained from a {@link ChunkSource}.
 * May also be configured to expose additional embedded {@link SampleStream}s.
 */
public class OurChunkSampleStream<T extends ChunkSource> implements SampleStream, SequenceableLoader,
        Loader.Callback<Chunk> {

  private final int primaryTrackType;
  private final int[] embeddedTrackTypes;
  private final boolean[] embeddedTracksSelected;
  private final T chunkSource;
  private final Callback<OurChunkSampleStream<T>> callback;
  private final EventDispatcher eventDispatcher;
  private final int minLoadableRetryCount;
  private final Loader loader;
  private final ChunkHolder nextChunkHolder;
  private final LinkedList<BaseMediaChunk> mediaChunks;
  private final List<BaseMediaChunk> readOnlyMediaChunks;
  private final ReplacementTrackOutput primarySampleQueue;
  private final ReplacementTrackOutput[] embeddedSampleQueues;
  private final BaseMediaChunkOutput mediaChunkOutput;
  private final String highestFormatId;
  private final boolean noReplacement;
  public final int adaptationSetIndex;

  private TileQualityTracker tileQualityTracker;
  private BaseMediaChunk lastLoggedChunk;
  private BaseMediaChunk chunkToBeReplaced;
  private BaseMediaChunk replacingChunk;
  private int qualityLogged;
  private DynamicEditingHolder dynamicEditingHolder;

  private Format primaryDownstreamTrackFormat;
  private long pendingResetPositionUs;
  /* package */ long lastSeekPositionUs;
  /* package */ boolean loadingFinished;

  /**
   * @param primaryTrackType      The type of the primary track. One of the {@link C}
   *                              {@code TRACK_TYPE_*} constants.
   * @param embeddedTrackTypes    The types of any embedded tracks, or null.
   * @param chunkSource           A {@link ChunkSource} from which chunks to load are obtained.
   * @param callback              An {@link Callback} for the stream.
   * @param allocator             An {@link Allocator} from which allocations can be obtained.
   * @param positionUs            The position from which to start loading media.
   * @param minLoadableRetryCount The minimum number of times that the source should retry a load
   *                              before propagating an error.
   * @param eventDispatcher       A dispatcher to notify of events.
   */
  public OurChunkSampleStream(int adaptationSetIndex, int primaryTrackType, int[] embeddedTrackTypes, T chunkSource,
                              Callback<OurChunkSampleStream<T>> callback, Allocator allocator, long positionUs,
                              int minLoadableRetryCount, EventDispatcher eventDispatcher, DynamicEditingHolder dynamicEditingHolder,
                              TileQualityTracker tileQualityTracker, ReplacementTracker replacementTracker,
                              boolean noReplacement) {
    this.adaptationSetIndex = adaptationSetIndex;
    this.dynamicEditingHolder = dynamicEditingHolder;
    this.tileQualityTracker = tileQualityTracker;
    //Getting the highest format for this tile
    this.highestFormatId = ((DefaultDashSRDChunkSource) chunkSource).getHighestFormatId();
    this.primaryTrackType = primaryTrackType;
    this.embeddedTrackTypes = embeddedTrackTypes;
    this.chunkSource = chunkSource;
    this.callback = callback;
    this.eventDispatcher = eventDispatcher;
    this.minLoadableRetryCount = minLoadableRetryCount;
    this.noReplacement = noReplacement;
    loader = new Loader("Loader:ChunkSampleStream");
    nextChunkHolder = new ChunkHolder();
    mediaChunks = new LinkedList<>();
    readOnlyMediaChunks = Collections.unmodifiableList(mediaChunks);

    int embeddedTrackCount = embeddedTrackTypes == null ? 0 : embeddedTrackTypes.length;
    embeddedSampleQueues = new ReplacementTrackOutput[embeddedTrackCount];
    embeddedTracksSelected = new boolean[embeddedTrackCount];
    int[] trackTypes = new int[1 + embeddedTrackCount];
    ReplacementTrackOutput[] sampleQueues = new ReplacementTrackOutput[1 + embeddedTrackCount];

    primarySampleQueue = new ReplacementTrackOutput(allocator, replacementTracker);
    trackTypes[0] = primaryTrackType;
    sampleQueues[0] = primarySampleQueue;

    for (int i = 0; i < embeddedTrackCount; i++) {
      ReplacementTrackOutput trackOutput = new ReplacementTrackOutput(allocator, replacementTracker);
      embeddedSampleQueues[i] = trackOutput;
      sampleQueues[i + 1] = trackOutput;
      trackTypes[i + 1] = embeddedTrackTypes[i];
    }

    mediaChunkOutput = new BaseMediaChunkOutput(trackTypes, sampleQueues);
    pendingResetPositionUs = positionUs;
    lastSeekPositionUs = positionUs;
  }

  /**
   * Discards buffered media for embedded tracks that are not currently selected, up to the
   * specified position.
   *
   * @param positionUs The position to discard up to, in microseconds.
   */
  public void discardUnselectedEmbeddedTracksTo(long positionUs) {
    for (int i = 0; i < embeddedSampleQueues.length; i++) {
      if (!embeddedTracksSelected[i]) {
        embeddedSampleQueues[i].skipToKeyframeBefore(positionUs, true);
      }
    }
  }

  /**
   * Selects the embedded track, returning a new {@link EmbeddedSampleStream} from which the track's
   * samples can be consumed. {@link EmbeddedSampleStream#release()} must be called on the returned
   * stream when the track is no longer required, and before calling this method again to obtain
   * another stream for the same track.
   *
   * @param positionUs The current playback position in microseconds.
   * @param trackType  The type of the embedded track to enable.
   * @return The {@link EmbeddedSampleStream} for the embedded track.
   */
  public EmbeddedSampleStream selectEmbeddedTrack(long positionUs, int trackType) {
    for (int i = 0; i < embeddedSampleQueues.length; i++) {
      if (embeddedTrackTypes[i] == trackType) {
        Assertions.checkState(!embeddedTracksSelected[i]);
        embeddedTracksSelected[i] = true;
        embeddedSampleQueues[i].skipToKeyframeBefore(positionUs, true);
        return new EmbeddedSampleStream(this, embeddedSampleQueues[i], i);
      }
    }
    // Should never happen.
    throw new IllegalStateException();
  }

  /**
   * Returns the {@link ChunkSource} used by this stream.
   */
  public T getChunkSource() {
    return chunkSource;
  }

  /**
   * Returns an estimate of the position up to which data is buffered.
   *
   * @return An estimate of the absolute position in microseconds up to which data is buffered, or
   * {@link C#TIME_END_OF_SOURCE} if the track is fully buffered.
   */
  public long getBufferedPositionUs() {
    if (loadingFinished) {
      return C.TIME_END_OF_SOURCE;
    } else if (isPendingReset()) {
      return pendingResetPositionUs;
    } else {
      long bufferedPositionUs = lastSeekPositionUs;
      BaseMediaChunk lastMediaChunk = mediaChunks.getLast();
      BaseMediaChunk lastCompletedMediaChunk = lastMediaChunk.isLoadCompleted() ? lastMediaChunk
              : mediaChunks.size() > 1 ? mediaChunks.get(mediaChunks.size() - 2) : null;
      if (lastCompletedMediaChunk != null) {
        bufferedPositionUs = Math.max(bufferedPositionUs, lastCompletedMediaChunk.endTimeUs);
      }
      return Math.max(bufferedPositionUs, primarySampleQueue.getLargestQueuedTimestampUs());
    }
  }

  /**
   * Seeks to the specified position in microseconds.
   *
   * @param positionUs The seek position in microseconds.
   */
  public void seekToUs(long positionUs) {
    lastSeekPositionUs = positionUs;
    // If we're not pending a reset, see if we can seek within the primary sample queue.
    boolean seekInsideBuffer = !isPendingReset() && primarySampleQueue.skipToKeyframeBefore(
            positionUs, positionUs < getNextLoadPositionUs());
    if (seekInsideBuffer) {
      // We succeeded. We need to discard any chunks that we've moved past and perform the seek for
      // any embedded streams as well.
      while (mediaChunks.size() > 1
              && mediaChunks.get(1).getFirstSampleIndex(0) <= primarySampleQueue.getReadIndex()) {
        mediaChunks.removeFirst();
      }
      // TODO: For this to work correctly, the embedded streams must not discard anything from their
      // sample queues beyond the current read position of the primary stream.
      for (ReplacementTrackOutput embeddedSampleQueue : embeddedSampleQueues) {
        embeddedSampleQueue.skipToKeyframeBefore(positionUs);
      }
    } else {
      // We failed, and need to restart.
      pendingResetPositionUs = positionUs;
      loadingFinished = false;
      mediaChunks.clear();
      if (loader.isLoading()) {
        loader.cancelLoading();
      } else {
        primarySampleQueue.reset(true);
        for (ReplacementTrackOutput embeddedSampleQueue : embeddedSampleQueues) {
          embeddedSampleQueue.reset(true);
        }
      }
    }
  }

  /**
   * Releases the stream.
   * <p>
   * This method should be called when the stream is no longer required.
   */
  public void release() {
    primarySampleQueue.disable();
    for (ReplacementTrackOutput embeddedSampleQueue : embeddedSampleQueues) {
      embeddedSampleQueue.disable();
    }
    loader.release();
  }

  // SampleStream implementation.

  @Override
  public boolean isReady() {
    return loadingFinished || (!isPendingReset() && !primarySampleQueue.isEmpty());
  }

  @Override
  public void maybeThrowError() throws IOException {
    loader.maybeThrowError();
    if (!loader.isLoading()) {
      chunkSource.maybeThrowError();
    }
  }

  @Override
  public int readData(FormatHolder formatHolder, DecoderInputBuffer buffer,
                      boolean formatRequired) {
    if (isPendingReset()) {
      return C.RESULT_NOTHING_READ;
    }
    discardDownstreamMediaChunks(primarySampleQueue.getReadIndex());
    return primarySampleQueue.readData(formatHolder, buffer, formatRequired, loadingFinished,
            lastSeekPositionUs);
  }

  @Override
  public void skipToKeyframeBefore(long timeUs) {
    primarySampleQueue.skipToKeyframeBefore(timeUs);
  }

  // Loader.Callback implementation.

  @Override
  public void onLoadCompleted(Chunk loadable, long elapsedRealtimeMs, long loadDurationMs) {
    stopReplacing();
    chunkSource.onChunkLoadCompleted(loadable);
    eventDispatcher.loadCompleted(loadable.dataSpec, loadable.type, primaryTrackType,
            loadable.trackFormat, loadable.trackSelectionReason, loadable.trackSelectionData,
            loadable.startTimeUs, loadable.endTimeUs, elapsedRealtimeMs, loadDurationMs,
            loadable.bytesLoaded());
    callback.onContinueLoadingRequested(this);
  }

  @Override
  public void onLoadCanceled(Chunk loadable, long elapsedRealtimeMs, long loadDurationMs,
                             boolean released) {
    cancelReplacement();
    eventDispatcher.loadCanceled(loadable.dataSpec, loadable.type, primaryTrackType,
            loadable.trackFormat, loadable.trackSelectionReason, loadable.trackSelectionData,
            loadable.startTimeUs, loadable.endTimeUs, elapsedRealtimeMs, loadDurationMs,
            loadable.bytesLoaded());
    if (!released) {
      primarySampleQueue.reset(true);
      for (ReplacementTrackOutput embeddedSampleQueue : embeddedSampleQueues) {
        embeddedSampleQueue.reset(true);
      }
      callback.onContinueLoadingRequested(this);
    }
  }

  @Override
  public int onLoadError(Chunk loadable, long elapsedRealtimeMs, long loadDurationMs,
                         IOException error) {
    long bytesLoaded = loadable.bytesLoaded();
    boolean isMediaChunk = isMediaChunk(loadable);
    boolean cancelable = !isMediaChunk || bytesLoaded == 0 || mediaChunks.size() > 1;
    boolean canceled = false;
    if (chunkSource.onChunkLoadError(loadable, cancelable, error)) {
      canceled = true;
      if (isMediaChunk) {
        BaseMediaChunk removed = mediaChunks.removeLast();
        Assertions.checkState(removed == loadable);
        primarySampleQueue.discardUpstreamSamples(removed.getFirstSampleIndex(0));
        for (int i = 0; i < embeddedSampleQueues.length; i++) {
          embeddedSampleQueues[i].discardUpstreamSamples(removed.getFirstSampleIndex(i + 1));
        }
        if (mediaChunks.isEmpty()) {
          pendingResetPositionUs = lastSeekPositionUs;
        }
      }
    }
    eventDispatcher.loadError(loadable.dataSpec, loadable.type, primaryTrackType,
            loadable.trackFormat, loadable.trackSelectionReason, loadable.trackSelectionData,
            loadable.startTimeUs, loadable.endTimeUs, elapsedRealtimeMs, loadDurationMs, bytesLoaded,
            error, canceled);
    if (canceled) {
      callback.onContinueLoadingRequested(this);
      return Loader.DONT_RETRY;
    } else {
      return Loader.RETRY;
    }
  }

  // SequenceableLoader implementation

  @Override
  public boolean continueLoading(long positionUs) {
    if (loadingFinished || loader.isLoading()) {
      return false;
    }

    chunkSource.getNextChunk(mediaChunks.isEmpty() ? null : mediaChunks.getLast(),
            pendingResetPositionUs != C.TIME_UNSET ? pendingResetPositionUs : positionUs,
            nextChunkHolder);
    boolean endOfStream = nextChunkHolder.endOfStream;
    Chunk loadable = nextChunkHolder.chunk;
    nextChunkHolder.clear();

    if (endOfStream) {
      loadingFinished = true;
      return true;
    }

    if (loadable == null) {
      return false;
    }

    if (isMediaChunk(loadable)) {
      pendingResetPositionUs = C.TIME_UNSET;
      BaseMediaChunk mediaChunk = (BaseMediaChunk) loadable;
      mediaChunk.init(mediaChunkOutput);
      mediaChunks.add(mediaChunk);
    }
    long elapsedRealtimeMs = loader.startLoading(loadable, this, minLoadableRetryCount);
    eventDispatcher.loadStarted(loadable.dataSpec, loadable.type, primaryTrackType,
            loadable.trackFormat, loadable.trackSelectionReason, loadable.trackSelectionData,
            loadable.startTimeUs, loadable.endTimeUs, elapsedRealtimeMs);
    return true;
  }

  public boolean replace(long playbackPosition) {
    //Still buffering?
    if (loader.isLoading() || noReplacement) {
      return false;
    }

    /*
    Targeting the chunk to be replaced: mediachunks holds in a linked list all
    the chunks already downloaded. The chunk whose startTimeUS and endTimeUs satisfy
    startTimeUS < playbackPosition < endTimeUs is identified and then the replacement
    starts two segments after
    */
    MediaChunk maybeReplace = null;
    int currentIndex = 0;
    int maybeReplaceIndex = 0;
    long currentClosest = Long.MAX_VALUE;
    for (MediaChunk mediaChunk : mediaChunks) {
      if ((mediaChunk.startTimeUs < playbackPosition) && (mediaChunk.endTimeUs > playbackPosition)) {
        maybeReplace = mediaChunk;
        maybeReplaceIndex = currentIndex;
        currentClosest = mediaChunk.startTimeUs - playbackPosition;
        break;
      } else if (mediaChunk.startTimeUs - playbackPosition > 0 &&
              mediaChunk.startTimeUs - playbackPosition < currentClosest) {
        maybeReplace = mediaChunk;
        currentClosest = mediaChunk.startTimeUs - playbackPosition;
        maybeReplaceIndex = currentIndex;
      }
      currentIndex++;
    }

    if (maybeReplace != null) {
      /*
			I've found the chunk that is the closest to the playing position in the buffer.
			Checking if I can replace chunk starting x segments ahead of it.
			 */
      if (currentClosest < 4000000) {
        int shift = (int) Math.ceil((4000000 - currentClosest) / 1000000);
        maybeReplaceIndex += shift;
      }
      if (maybeReplaceIndex < 3) {
        maybeReplaceIndex = 3;
      }
      if (maybeReplaceIndex < mediaChunks.size()) {
        maybeReplace = mediaChunks.get(maybeReplaceIndex);

        /*if the video is dynamically edited I need to know if a snapchange occurs before the start of the chunk.
                If yes, the tile will be analyzed iff it is in the field of view after the snapchange.
                The replacement will take place if the quality is not the highest.
                 */
        boolean replaceFlag = false;
        if(dynamicEditingHolder.isDynamicEdited() && dynamicEditingHolder.nextSCMicroseconds<maybeReplace.startTimeUs) {
          /*
          The snapchange occurs before the beginning of the chunk.
          In this case I'm sure that it has the highest quality because the same consideration
          is adopted when choosing the quality for downloading the chunks.
          Do nothing!
           */
          Log.e("REPLACE", "Dynamic prevents");
        } else {
          //The video is not dynamically edited or a snapchange occurs AFTER the beginning of the chunk: i'll base my analysis on the picker
          TilesPicker tilesPicker = TilesPicker.getPicker();
          if(tilesPicker.isPicked(adaptationSetIndex) && !maybeReplace.trackFormat.id.equals(highestFormatId)) {
            //The tile is picked and it hasn't the highest quality -> replace
            replaceFlag = true;
          }
        }
        //Should I replace?
        if(replaceFlag) {
          Log.e("REPLACE", "Replacement launched in ChunkSampleStream");
          //Fire the download
          //First put into nextChunkHolder the correct Chunk
          ((DefaultDashSRDChunkSource)chunkSource).getChunkToBeReplaced(maybeReplace.chunkIndex,nextChunkHolder);
          //Then fire the download
          Chunk loadable = nextChunkHolder.chunk;
          nextChunkHolder.clear();

          //TODO HANDLE THE CALLBACK WITH ROMARIC's CODE TO UPDATE THE MEDIA CHUNK LINKED LIST
          if (isMediaChunk(loadable)) {
            BaseMediaChunk mediaChunk = (BaseMediaChunk) loadable;
            mediaChunk.initForReplacement(mediaChunkOutput, ((BaseMediaChunk)maybeReplace).getFirstSampleIndices());
            chunkToBeReplaced = mediaChunks.get(maybeReplaceIndex);
            replacingChunk = mediaChunk;
            mediaChunkOutput.startReplacement(loadable.startTimeUs, loadable.endTimeUs);
          }

          long elapsedRealtimeMs = loader.startLoading(loadable, this, minLoadableRetryCount);
          eventDispatcher.loadStarted(loadable.dataSpec, loadable.type, primaryTrackType,
                  loadable.trackFormat, loadable.trackSelectionReason, loadable.trackSelectionData,
                  loadable.startTimeUs, loadable.endTimeUs, elapsedRealtimeMs);
          return true;
        } else {
          //The tile is already at the maximum quality: do nothing
          return false;
        }
      } else {
        //No time to replace: do nothing
        return false;
      }
    } else {
			/*
			I've not found the chunk that is the closest to the playing position in the buffer.
			This means that the buffer is empty and should not happen. We will exit the replacement
			without doing anything.
			*/
      return false;
    }
  }

  @Override
  public long getNextLoadPositionUs() {
    if (isPendingReset()) {
      return pendingResetPositionUs;
    } else {
      return loadingFinished ? C.TIME_END_OF_SOURCE : mediaChunks.getLast().endTimeUs;
    }
  }

  // Internal methods

  // TODO[REFACTOR]: Call maybeDiscardUpstream for DASH and SmoothStreaming.

  /**
   * Discards media chunks from the back of the buffer if conditions have changed such that it's
   * preferable to re-buffer the media at a different quality.
   *
   * @param positionUs The current playback position in microseconds.
   */
  private void maybeDiscardUpstream(long positionUs) {
    int queueSize = chunkSource.getPreferredQueueSize(positionUs, readOnlyMediaChunks);
    discardUpstreamMediaChunks(Math.max(1, queueSize));
  }

  private boolean isMediaChunk(Chunk chunk) {
    return chunk instanceof BaseMediaChunk;
  }

  /* package */ boolean isPendingReset() {
    return pendingResetPositionUs != C.TIME_UNSET;
  }

  private void discardDownstreamMediaChunks(int primaryStreamReadIndex) {
    while (mediaChunks.size() > 1 && mediaChunks.get(1).getFirstSampleIndex(0) <= primaryStreamReadIndex) {
      mediaChunks.removeFirst();
    }

    BaseMediaChunk currentChunk = mediaChunks.getFirst();
      Log.e("SRD"+adaptationSetIndex,currentChunk.chunkIndex+"");
    //Logging of the quality
    if(tileQualityTracker !=null && currentChunk.trackFormat.width > 0) {
      if(!currentChunk.equals(this.lastLoggedChunk)) {
        qualityLogged = currentChunk.trackFormat.id.equals(this.highestFormatId) ? 1 : 0;
        tileQualityTracker.track(adaptationSetIndex, currentChunk.chunkIndex, currentChunk.startTimeUs, currentChunk.endTimeUs, qualityLogged);
        this.lastLoggedChunk = currentChunk;
      }
    }
    Format trackFormat = currentChunk.trackFormat;
    if (!trackFormat.equals(primaryDownstreamTrackFormat)) {
      eventDispatcher.downstreamFormatChanged(primaryTrackType, trackFormat,
              currentChunk.trackSelectionReason, currentChunk.trackSelectionData,
              currentChunk.startTimeUs);
    }
    primaryDownstreamTrackFormat = trackFormat;
  }

  /**
   * Discard upstream media chunks until the queue length is equal to the length specified.
   *
   * @param queueLength The desired length of the queue.
   * @return Whether chunks were discarded.
   */
  private boolean discardUpstreamMediaChunks(int queueLength) {
    if (mediaChunks.size() <= queueLength) {
      return false;
    }
    long startTimeUs = 0;
    long endTimeUs = mediaChunks.getLast().endTimeUs;
    BaseMediaChunk removed = null;
    while (mediaChunks.size() > queueLength) {
      removed = mediaChunks.removeLast();
      startTimeUs = removed.startTimeUs;
      loadingFinished = false;
    }
    primarySampleQueue.discardUpstreamSamples(removed.getFirstSampleIndex(0));
    for (int i = 0; i < embeddedSampleQueues.length; i++) {
      embeddedSampleQueues[i].discardUpstreamSamples(removed.getFirstSampleIndex(i + 1));
    }
    eventDispatcher.upstreamDiscarded(primaryTrackType, startTimeUs, endTimeUs);
    return true;
  }

  /**
   * A {@link SampleStream} embedded in a {@link OurChunkSampleStream}.
   */
  public final class EmbeddedSampleStream implements SampleStream {

    public final OurChunkSampleStream<T> parent;

    private final ReplacementTrackOutput sampleQueue;
    private final int index;

    public EmbeddedSampleStream(OurChunkSampleStream<T> parent, ReplacementTrackOutput sampleQueue,
                                int index) {
      this.parent = parent;
      this.sampleQueue = sampleQueue;
      this.index = index;
    }

    @Override
    public boolean isReady() {
      return loadingFinished || (!isPendingReset() && !sampleQueue.isEmpty());
    }

    @Override
    public void skipToKeyframeBefore(long timeUs) {
      sampleQueue.skipToKeyframeBefore(timeUs);
    }

    @Override
    public void maybeThrowError() throws IOException {
      // Do nothing. Errors will be thrown from the primary stream.
    }

    @Override
    public int readData(FormatHolder formatHolder, DecoderInputBuffer buffer,
                        boolean formatRequired) {
      if (isPendingReset()) {
        return C.RESULT_NOTHING_READ;
      }
      return sampleQueue.readData(formatHolder, buffer, formatRequired, loadingFinished,
              lastSeekPositionUs);
    }

    public void release() {
      Assertions.checkState(embeddedTracksSelected[index]);
      embeddedTracksSelected[index] = false;
    }

  }

  /**
   * The method is called when I'm downloading chunks for the replacement strategy but at some
   * point I need to rebuffer again. In this case I've to stop the download.
   */
  public void stopReplacing() {
    if(mediaChunkOutput.commitReplacement()) {
      for(int j = 0; j<mediaChunks.size(); j++) {
        if(mediaChunks.get(j).equals(chunkToBeReplaced)) {
          mediaChunks.set(j, replacingChunk);
          break;
        }
      }
      replacingChunk = null;
      chunkToBeReplaced = null;
    }
  }

  public void cancelReplacement() {
    mediaChunkOutput.cancelReplacement();
    if (loader.isLoading()) loader.cancelLoading();
  }

}
