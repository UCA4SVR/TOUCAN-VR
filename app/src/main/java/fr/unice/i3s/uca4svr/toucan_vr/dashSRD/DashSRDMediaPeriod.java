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
 */
package fr.unice.i3s.uca4svr.toucan_vr.dashSRD;

import android.app.Activity;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.source.AdaptiveMediaSourceEventListener.EventDispatcher;
import com.google.android.exoplayer2.source.CompositeSequenceableLoader;
import com.google.android.exoplayer2.source.MediaPeriod;
import com.google.android.exoplayer2.source.SampleStream;
import com.google.android.exoplayer2.source.SequenceableLoader;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.source.dash.DashChunkSource;
import com.google.android.exoplayer2.source.dash.manifest.AdaptationSet;
import com.google.android.exoplayer2.source.dash.manifest.DashManifest;
import com.google.android.exoplayer2.source.dash.manifest.Representation;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.upstream.LoaderErrorThrower;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;

import fr.unice.i3s.uca4svr.toucan_vr.dashSRD.manifest.AdaptationSetSRD;
import fr.unice.i3s.uca4svr.toucan_vr.dynamicEditing.DynamicEditingHolder;
import fr.unice.i3s.uca4svr.toucan_vr.tracking.ReplacementTracker;
import fr.unice.i3s.uca4svr.toucan_vr.tracking.TileQualityTracker;

import com.google.android.exoplayer2.source.chunk.OurChunkSampleStream;

/**
 * A DASH {@link MediaPeriod}.
 */
/* package */ final class DashSRDMediaPeriod implements MediaPeriod,
    SequenceableLoader.Callback<OurChunkSampleStream<DashChunkSource>> {

    /* package */ final int id;
    private final DashChunkSource.Factory chunkSourceFactory;
    private final int minLoadableRetryCount;
    private final EventDispatcher eventDispatcher;
    private final long elapsedRealtimeOffset;
    private final LoaderErrorThrower manifestLoaderErrorThrower;
    private final Allocator allocator;
    private final TrackGroupArray trackGroups;

    private Callback callback;
    private CompositeSequenceableLoader sequenceableLoader;
    private OurChunkSampleStream<DashChunkSource>[] sampleStreams;
    private DashManifest manifest;
    private int periodIndex;
    private List<AdaptationSet> adaptationSets;

    private DynamicEditingHolder dynamicEditingHolder;
    private final TileQualityTracker tileQualityTracker;
    private final ReplacementTracker replacementTracker;
    private Activity activity;

    public DashSRDMediaPeriod(Activity activity, int id, DashManifest manifest, int periodIndex,
                              DashChunkSource.Factory chunkSourceFactory, int minLoadableRetryCount,
                              EventDispatcher eventDispatcher, long elapsedRealtimeOffset,
                              LoaderErrorThrower manifestLoaderErrorThrower, Allocator allocator,
                              DynamicEditingHolder dynamicEditingHolder, TileQualityTracker tileQualityTracker,
                              ReplacementTracker replacementTracker) {
        this.id = id;
        this.manifest = manifest;
        this.periodIndex = periodIndex;
        this.chunkSourceFactory = chunkSourceFactory;
        this.minLoadableRetryCount = minLoadableRetryCount;
        this.eventDispatcher = eventDispatcher;
        this.elapsedRealtimeOffset = elapsedRealtimeOffset;
        this.manifestLoaderErrorThrower = manifestLoaderErrorThrower;
        this.allocator = allocator;
        this.dynamicEditingHolder = dynamicEditingHolder;
        this.tileQualityTracker = tileQualityTracker;
        this.replacementTracker = replacementTracker;
        this.activity = activity;
        sampleStreams = newSampleStreamArray(0);
        sequenceableLoader = new CompositeSequenceableLoader(sampleStreams);
        adaptationSets = manifest.getPeriod(periodIndex).adaptationSets;
        trackGroups = buildTrackGroups(adaptationSets);
        System.out.println("trackGroups length "+trackGroups.length);
        System.out.println("trackGroup1 length "+trackGroups.get(1).length);
    }

    public void updateManifest(DashManifest manifest, int periodIndex) {
        this.manifest = manifest;
        this.periodIndex = periodIndex;
        adaptationSets = manifest.getPeriod(periodIndex).adaptationSets;
        if (sampleStreams != null) {
            for (OurChunkSampleStream<DashChunkSource> sampleStream : sampleStreams) {
                sampleStream.getChunkSource().updateManifest(manifest, periodIndex);
            }
            callback.onContinueLoadingRequested(this);
        }
    }

    public void release() {
        for (OurChunkSampleStream<DashChunkSource> sampleStream : sampleStreams) {
            sampleStream.release();
        }
    }

    @Override
    public void prepare(Callback callback) {
        this.callback = callback;
        callback.onPrepared(this);
    }

    @Override
    public void maybeThrowPrepareError() throws IOException {
        manifestLoaderErrorThrower.maybeThrowError();
    }

    @Override
    public TrackGroupArray getTrackGroups() {
        return trackGroups;
    }

    @Override
    public long selectTracks(TrackSelection[] selections, boolean[] mayRetainStreamFlags,
                             SampleStream[] streams, boolean[] streamResetFlags, long positionUs) {
        int adaptationSetCount = adaptationSets.size();
        HashMap<Integer, OurChunkSampleStream<DashChunkSource>> primarySampleStreams = new HashMap<>();

        for (int i = 0; i < selections.length; i++) {
            if (streams[i] instanceof OurChunkSampleStream) {
                @SuppressWarnings("unchecked")
                OurChunkSampleStream<DashChunkSource> stream = (OurChunkSampleStream<DashChunkSource>) streams[i];
                if (selections[i] == null || !mayRetainStreamFlags[i]) {
                    stream.release();
                    streams[i] = null;
                } else {
                    int adaptationSetIndex = trackGroups.indexOf(selections[i].getTrackGroup());
                    primarySampleStreams.put(adaptationSetIndex, stream);
                }
            }
            if (streams[i] == null && selections[i] != null) {
                int trackGroupIndex = trackGroups.indexOf(selections[i].getTrackGroup());
                if (trackGroupIndex < adaptationSetCount) {
                    OurChunkSampleStream<DashChunkSource> stream = buildSampleStream(trackGroupIndex,
                            selections[i], positionUs);
                    primarySampleStreams.put(trackGroupIndex, stream);
                    streams[i] = stream;
                    streamResetFlags[i] = true;
                }
            }
        }
        sampleStreams = newSampleStreamArray(primarySampleStreams.size());
        primarySampleStreams.values().toArray(sampleStreams);
        sequenceableLoader = new CompositeSequenceableLoader(sampleStreams);
        return positionUs;
    }

    @Override
    public void discardBuffer(long positionUs) {
        for (OurChunkSampleStream<DashChunkSource> sampleStream : sampleStreams) {
            sampleStream.discardUnselectedEmbeddedTracksTo(positionUs);
        }
    }

    @Override
    public boolean continueLoading(long positionUs) {
        return sequenceableLoader.continueLoading(positionUs);
    }

    @Override
    public long getNextLoadPositionUs() {
        return sequenceableLoader.getNextLoadPositionUs();
    }

    @Override
    public long readDiscontinuity() {
        return C.TIME_UNSET;
    }

    @Override
    public long getBufferedPositionUs() {
        long bufferedPositionUs = Long.MAX_VALUE;
        for (OurChunkSampleStream<DashChunkSource> sampleStream : sampleStreams) {
            long rendererBufferedPositionUs = sampleStream.getBufferedPositionUs();
            if (rendererBufferedPositionUs != C.TIME_END_OF_SOURCE) {
                bufferedPositionUs = Math.min(bufferedPositionUs, rendererBufferedPositionUs);
            }
        }
        return bufferedPositionUs == Long.MAX_VALUE ? C.TIME_END_OF_SOURCE : bufferedPositionUs;
    }

    @Override
    public long seekToUs(long positionUs) {
        for (OurChunkSampleStream<DashChunkSource> sampleStream : sampleStreams) {
            sampleStream.seekToUs(positionUs);
        }
        return positionUs;
    }

    // SequenceableLoader.Callback implementation.

    @Override
    public void onContinueLoadingRequested(OurChunkSampleStream<DashChunkSource> sampleStream) {
        callback.onContinueLoadingRequested(this);
    }

    // Internal methods.

    // N.B. this method creates a group of tracks from each of the adaptation sets
    private static TrackGroupArray buildTrackGroups(List<AdaptationSet> adaptationSets) {
        int adaptationSetCount = adaptationSets.size();
        TrackGroup[] trackGroupArray = new TrackGroup[adaptationSetCount];

        for (int i = 0; i < adaptationSetCount; i++) {
            // casting to AdaptationSetSRD
            AdaptationSetSRD adaptationSet = (AdaptationSetSRD)adaptationSets.get(i);
            List<Representation> representations = adaptationSet.representations;
            Format[] formats = new Format[representations.size()];
            for (int j = 0; j < formats.length; j++) {
                formats[j] = representations.get(j).format;
            }
            trackGroupArray[i] = new TrackGroup(formats);
        }

        return new TrackGroupArray(trackGroupArray);
    }

    // N.B. this method creates a stream of chunks for each of the renderer
    //      (provided that each renderer has a group of exposed tracks and is enabled)
    private OurChunkSampleStream<DashChunkSource> buildSampleStream(int adaptationSetIndex,
                                                                 TrackSelection selection, long positionUs) {
        AdaptationSet adaptationSet = adaptationSets.get(adaptationSetIndex);
        DashChunkSource chunkSource = chunkSourceFactory.createDashChunkSource(
                manifestLoaderErrorThrower, manifest, periodIndex, adaptationSetIndex, selection,
                elapsedRealtimeOffset, /*enableEventMessageTrack*/ false, /*enableCea608Track*/ false);
        OurChunkSampleStream<DashChunkSource> stream = new OurChunkSampleStream<>(this.activity, adaptationSetIndex, adaptationSet.type,
                /*embeddedTrackTypes*/ null, chunkSource, this, allocator, positionUs, minLoadableRetryCount,
                eventDispatcher, dynamicEditingHolder, tileQualityTracker, replacementTracker);
        return stream;
    }

    @SuppressWarnings("unchecked")
    private static OurChunkSampleStream<DashChunkSource>[] newSampleStreamArray(int length) {
        return new OurChunkSampleStream[length];
    }
}
