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
 *
 * Modifications:
 * Package name
 * Added SRD support to the parser
 * Copyright 2017 Laboratoire I3S, CNRS, Université côte d'azur
 * Author: Savino Dambra
 */
package fr.unice.i3s.uca4svr.toucan_vr.dashSRD.track_selection;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.chunk.MediaChunk;
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection;
import com.google.android.exoplayer2.trackselection.BaseTrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.upstream.BandwidthMeter;

import java.util.Arrays;
import java.util.List;

import fr.unice.i3s.uca4svr.toucan_vr.dynamicEditing.DynamicEditingHolder;
import fr.unice.i3s.uca4svr.toucan_vr.dynamicEditing.operations.DynamicOperation;
import fr.unice.i3s.uca4svr.toucan_vr.dynamicEditing.operations.SnapChange;
import fr.unice.i3s.uca4svr.toucan_vr.tilespicker.TilesPicker;

public class PyramidalTrackSelection extends BaseTrackSelection {

    public static final int DEFAULT_MAX_BUFFER_SIZE = 10000000;
    public static final int DEFAULT_MAX_INITIAL_BITRATE = 800000;
    public static final int DEFAULT_MIN_DURATION_FOR_QUALITY_INCREASE_MS = 10000;
    public static final int DEFAULT_MAX_DURATION_FOR_QUALITY_DECREASE_MS = 25000;
    public static final int DEFAULT_MIN_DURATION_TO_RETAIN_AFTER_DISCARD_MS = 1000;
    public static final int DEFAULT_MAX_DURATION_TO_RETAIN_AFTER_DISCARD_MS = 6000;
    public static final float DEFAULT_BANDWIDTH_FRACTION = 0.75f;

    private final BandwidthMeter bandwidthMeter;
    private final int maxInitialBitrate;
    private final int bufferSize;
    private final long minDurationForQualityIncreaseUs;
    private final long maxDurationForQualityDecreaseUs;
    private final long minDurationToRetainAfterDiscardUs;
    private final long maxDurationToRetainAfterDiscardUs;
    private final float bandwidthFraction;

    private int selectedIndex;
    private int reason;
    private int adaptationSetIndex;
    private long currentPlaybackPosition;
    private long nextChunkStartTimeUs;
    private long nextChunkEndTimeUs;
    private DynamicEditingHolder dynamicEditingHolder;

    /**
     * Factory for {@link AdaptiveTrackSelection} instances.
     */
    public static final class Factory implements TrackSelection.Factory {

        private final BandwidthMeter bandwidthMeter;
        private final int maxInitialBitrate;
        private final int bufferSizeMs;
        private final int minDurationForQualityIncreaseMs;
        private final int maxDurationForQualityDecreaseMs;
        private final int minDurationToRetainAfterDiscardMs;
        private final int maxDurationToRetainAfterDiscardMs;
        private final float bandwidthFraction;
        private DynamicEditingHolder dynamicEditingHolder;

        /**
         * @param bandwidthMeter Provides an estimate of the currently available bandwidth.
         * @param maxInitialBitrate The maximum bitrate in bits per second that should be assumed
         *     when a bandwidth estimate is unavailable.
         * @param minDurationForQualityIncreaseMs The minimum duration of buffered data required for
         *     the selected track to switch to one of higher quality.
         * @param maxDurationForQualityDecreaseMs The maximum duration of buffered data required for
         *     the selected track to switch to one of lower quality.
         * @param minDurationToRetainAfterDiscardMs When switching to a track of significantly higher
         *     quality, the selection may indicate that media already buffered at the lower quality can
         *     be discarded to speed up the switch. This is the minimum duration of media that must be
         *     retained at the lower quality.
         * @param bandwidthFraction The fraction of the available bandwidth that the selection should
         *     consider available for use. Setting to a value less than 1 is recommended to account
         *     for inaccuracies in the bandwidth estimator.
         * @param dynamicEditingHolder The object that holds the information about the snapchanges
         */
        public Factory(BandwidthMeter bandwidthMeter, int maxInitialBitrate, int bufferSizeMs,
                       int minDurationForQualityIncreaseMs, int maxDurationForQualityDecreaseMs,
                       int minDurationToRetainAfterDiscardMs, int maxDurationToRetainAfterDiscardMs,
                       float bandwidthFraction, DynamicEditingHolder dynamicEditingHolder) {
            this.bandwidthMeter = bandwidthMeter;
            this.maxInitialBitrate = maxInitialBitrate;
            this.bufferSizeMs = bufferSizeMs;
            this.minDurationForQualityIncreaseMs = minDurationForQualityIncreaseMs;
            this.maxDurationForQualityDecreaseMs = maxDurationForQualityDecreaseMs;
            this.minDurationToRetainAfterDiscardMs = minDurationToRetainAfterDiscardMs;
            this.maxDurationToRetainAfterDiscardMs = maxDurationToRetainAfterDiscardMs;
            this.bandwidthFraction = bandwidthFraction;
            this.dynamicEditingHolder = dynamicEditingHolder;
        }

        @Override
        public PyramidalTrackSelection createTrackSelection(TrackGroup group, int... tracks) {
            return new PyramidalTrackSelection(group, tracks, bandwidthMeter, maxInitialBitrate,
                    bufferSizeMs, minDurationForQualityIncreaseMs, maxDurationForQualityDecreaseMs,
                    minDurationToRetainAfterDiscardMs, maxDurationToRetainAfterDiscardMs,
                    bandwidthFraction, dynamicEditingHolder);
        }
    }

    /**
     * @param group The {@link TrackGroup}.
     * @param tracks The indices of the selected tracks within the {@link TrackGroup}. Must not be
     *     empty. May be in any order.
     * @param bandwidthMeter Provides an estimate of the currently available bandwidth.
     * @param maxInitialBitrate The maximum bitrate in bits per second that should be assumed when a
     *     bandwidth estimate is unavailable.
     * @param bufferSizeMs The max size of the buffer in milliseconds.
     * @param minDurationForQualityIncreaseMs The minimum duration of buffered data required for the
     *     selected track to switch to one of higher quality.
     * @param maxDurationForQualityDecreaseMs The maximum duration of buffered data required for the
     *     selected track to switch to one of lower quality.
     * @param minDurationToRetainAfterDiscardMs When switching to a track of significantly higher
     *     quality, the selection may indicate that media already buffered at the lower quality can
     *     be discarded to speed up the switch. This is the minimum duration of media that must be
     *     retained at the lower quality (aka the minimum duration of the safe margin).
     * @param maxDurationToRetainAfterDiscardMs When switching to a track of significantly higher
     *     quality, the selection may indicate that media already buffered at the lower quality can
     *     be discarded to speed up the switch. This is the maximum duration of media that must be
     *     retained at the lower quality (aka the maximum duration of the safe margin).
     * @param bandwidthFraction The fraction of the available bandwidth that the selection should
     *     consider available for use. Setting to a value less than 1 is recommended to account
     *     for inaccuracies in the bandwidth estimator.
     * @param dynamicEditingHolder The object that holds the information about the snapchanges
     */
    public PyramidalTrackSelection(TrackGroup group, int[] tracks,
                                   BandwidthMeter bandwidthMeter, int maxInitialBitrate, int bufferSizeMs,
                                   long minDurationForQualityIncreaseMs, long maxDurationForQualityDecreaseMs,
                                   long minDurationToRetainAfterDiscardMs, long maxDurationToRetainAfterDiscardMs,
                                   float bandwidthFraction, DynamicEditingHolder dynamicEditingHolder) {
        super(group, tracks);
        this.bandwidthMeter = bandwidthMeter;
        this.maxInitialBitrate = maxInitialBitrate;
        this.bufferSize = bufferSizeMs * 1000;
        this.minDurationForQualityIncreaseUs = minDurationForQualityIncreaseMs * 1000L;
        this.maxDurationForQualityDecreaseUs = maxDurationForQualityDecreaseMs * 1000L;
        this.minDurationToRetainAfterDiscardUs = minDurationToRetainAfterDiscardMs * 1000L;
        this.maxDurationToRetainAfterDiscardUs = maxDurationToRetainAfterDiscardMs * 1000L;
        this.bandwidthFraction = bandwidthFraction;
        this.dynamicEditingHolder = dynamicEditingHolder;
        selectedIndex = determineIdealSelectedIndex(false);
        reason = C.SELECTION_REASON_INITIAL;
    }

    public void updateAdaptationSetIndex(int adaptationSetIndex) {
        this.adaptationSetIndex = adaptationSetIndex;
    }

    public void updatePlaybackPosition(long playbackPositionUs) {
        this.currentPlaybackPosition = playbackPositionUs;
    }

    public void updateNextChunkStartTime(long chunkStartTimeUs) {
        this.nextChunkStartTimeUs = chunkStartTimeUs;
    }

    public void updateNextChunkEndTime(long chunkEndTimeUs) {
        this.nextChunkEndTimeUs = chunkEndTimeUs;
    }

    /**
     * Updates the selected quality for the next chunk to download in the current chunk stream.
     * Quality is based either on the current Field of View, provided by the TilesPicker,
     * or on the snap changes included in the DynamicEditingHolder.
     * When the index selected is 1 the quality requested is low, otherwise is high.
     *
     * @param bufferedDurationUs Amount of data currently stored in the buffer.
     */
    @Override
    public void updateSelectedTrack(long bufferedDurationUs) {
        boolean isPicked = TilesPicker.getPicker().isPicked(adaptationSetIndex);
        int currentSelectedIndex = selectedIndex;

        if(dynamicEditingHolder.isDynamicEdited()) {
            List<DynamicOperation> operations = dynamicEditingHolder.getOperations();
            SnapChange closestSnapChange = null;
            // In case of multiple snap changes in the buffer, consider the last one when making decisions
            for (int i = 0; i < operations.size() && operations.get(i).getMicroseconds() < nextChunkEndTimeUs; i++) {
                closestSnapChange = (SnapChange)operations.get(i);
            }
            if (closestSnapChange != null) {
                int desiredIndex = Arrays.binarySearch(closestSnapChange.getSCfoVTiles(), adaptationSetIndex) >= 0 ? 0 : 1;
                if (closestSnapChange.getMicroseconds() >= nextChunkStartTimeUs && desiredIndex == 1) {
                    // The snap change involves the current chunk. Provide a smooth transition when the snap change
                    // is forcing the quality to be low while the tile is still displayed to the user.
                    desiredIndex = selectedIndex;
                }
                selectedIndex = desiredIndex;
            } else {
                // No snap changes in the buffer
                selectedIndex = determineIdealSelectedIndex(isPicked);
            }
        } else {
            // No dynamic editing
            selectedIndex = determineIdealSelectedIndex(isPicked);
        }

        if (selectedIndex != currentSelectedIndex) {
            reason = C.SELECTION_REASON_ADAPTIVE;
        }
    }

    public void forceSelectedTrack() {
        selectedIndex = 0;
    }

    @Override
    public int getSelectedIndex() {
        return selectedIndex;
    }

    @Override
    public int getSelectionReason() {
        return reason;
    }

    @Override
    public Object getSelectionData() {
        return selectedIndex;
    }

    /**
     * Returns the desired queue length to obtain after discarding low quality chunks.
     *
     * @param playbackPositionUs The current position in the playback.
     * @param queue The list of media chunks currently in the buffer.
     * @return The desired queue size.
     */
    @Override
    public int evaluateQueueSize(long playbackPositionUs, List<? extends MediaChunk> queue) {
        if (queue.isEmpty()) {
            return 0;
        }
        int queueSize = queue.size();
        long bufferedDurationUs = queue.get(queueSize - 1).endTimeUs - playbackPositionUs;
        double bufferedPercentage = (double)bufferedDurationUs/bufferSize;
        double adjustedThreshold = bufferedPercentage*(maxDurationToRetainAfterDiscardUs - minDurationToRetainAfterDiscardUs);
        long safeMargin = maxDurationToRetainAfterDiscardUs - (long)adjustedThreshold;
        if (safeMargin < minDurationToRetainAfterDiscardUs)
            safeMargin = minDurationToRetainAfterDiscardUs;
        if (bufferedDurationUs < safeMargin) {
            // Not enough buffered data. Never discard in this case.
            return queueSize;
        }

        MediaChunk previousChunk = queue.get(0);
        if(dynamicEditingHolder.isDynamicEdited() &&
                dynamicEditingHolder.nextSCMicroseconds <= nextChunkEndTimeUs) {
            long timeBeforeSnapChangeUs = dynamicEditingHolder.nextSCMicroseconds - playbackPositionUs;
            if (timeBeforeSnapChangeUs < maxDurationToRetainAfterDiscardUs) {
                // The snap change is close to the playback position. It's not worth discarding.
                return queueSize;
            }
            // Check for the last chunk before the snap change position
            for (int i = 1; i < queueSize && queue.get(i).endTimeUs < dynamicEditingHolder.nextSCMicroseconds; i++) {
                previousChunk = queue.get(i);
            }

        } else {
            previousChunk = queue.get(queueSize - 1);
        }

        int idealQualityIndex = determineIdealSelectedIndex(TilesPicker.getPicker().isPicked(adaptationSetIndex));
        int currentQualityIndex = (int)previousChunk.trackSelectionData;

        // Lower index means better quality
        if (idealQualityIndex < currentQualityIndex) {
            // Computes the desired queue length by searching the first chunk in the buffer after
            // minDurationToRetainAfterDiscardUs that has lower quality than the ideal track.
            for (int i = 0; i < queueSize; i++) {
                MediaChunk chunk = queue.get(i);
                long durationBeforeThisChunkUs = chunk.startTimeUs - playbackPositionUs;
                if (durationBeforeThisChunkUs > safeMargin
                        && (int)chunk.trackSelectionData == 1) {
                    return i;
                }
            }
        }
        return queueSize;
    }

    /**
     * Determines the ideal quality for the next chunk.
     * If the tile is in the field of view, the highest quality is selected,
     * otherwise the lowest quality is chosen.
     */
    private int determineIdealSelectedIndex(boolean isPicked) {
        if(isPicked)
            return 0;
        else
            return 1;
    }
}
