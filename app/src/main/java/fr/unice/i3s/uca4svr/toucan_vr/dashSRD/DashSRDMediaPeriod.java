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

import android.util.Log;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.source.AdaptiveMediaSourceEventListener.EventDispatcher;
import com.google.android.exoplayer2.source.MediaPeriod;
import com.google.android.exoplayer2.source.SRDCompositeSequenceableLoader;
import com.google.android.exoplayer2.source.SampleStream;
import com.google.android.exoplayer2.source.SequenceableLoader;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.source.chunk.SRDChunkSampleStream;
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
import fr.unice.i3s.uca4svr.toucan_vr.tilespicker.TilesPicker;

/**
 * A DASH {@link MediaPeriod}.
 */
/* package */ final class DashSRDMediaPeriod implements MediaPeriod,
        SequenceableLoader.Callback<SRDChunkSampleStream<DashChunkSource>> {

    /* package */ final int id;
    private final DashChunkSource.Factory chunkSourceFactory;
    private final int minLoadableRetryCount;
    private final EventDispatcher eventDispatcher;
    private final long elapsedRealtimeOffset;
    private final LoaderErrorThrower manifestLoaderErrorThrower;
    private final Allocator allocator;
    private final TrackGroupArray trackGroups;
    private final int minBufferMs;
    private final int maxBufferMs;

    private Callback callback;
    private SRDChunkSampleStream<DashChunkSource>[] sampleStreams;
    private SRDCompositeSequenceableLoader sequenceableLoader;
    private DashManifest manifest;
    private int periodIndex;
    private List<AdaptationSet> adaptationSets;

    public DashSRDMediaPeriod(int id, DashManifest manifest, int periodIndex,
                           DashChunkSource.Factory chunkSourceFactory,  int minLoadableRetryCount,
                           EventDispatcher eventDispatcher, long elapsedRealtimeOffset,
                           LoaderErrorThrower manifestLoaderErrorThrower, Allocator allocator, int minBufferMs, int maxBufferMs) {
        this.id = id;
        this.manifest = manifest;
        this.periodIndex = periodIndex;
        this.chunkSourceFactory = chunkSourceFactory;
        this.minLoadableRetryCount = minLoadableRetryCount;
        this.eventDispatcher = eventDispatcher;
        this.elapsedRealtimeOffset = elapsedRealtimeOffset;
        this.manifestLoaderErrorThrower = manifestLoaderErrorThrower;
        this.allocator = allocator;
        //Buffers are provided in ms and then used in microseconds
        this.minBufferMs = minBufferMs*1000;
        this.maxBufferMs = maxBufferMs*1000;
        sampleStreams = newSampleStreamArray(0);
        sequenceableLoader = new SRDCompositeSequenceableLoader(sampleStreams);
        adaptationSets = manifest.getPeriod(periodIndex).adaptationSets;
        trackGroups = buildTrackGroups(adaptationSets);
    }

    public void updateManifest(DashManifest manifest, int periodIndex) {
        this.manifest = manifest;
        this.periodIndex = periodIndex;
        adaptationSets = manifest.getPeriod(periodIndex).adaptationSets;
        if (sampleStreams != null) {
            for (SRDChunkSampleStream<DashChunkSource> sampleStream : sampleStreams) {
                sampleStream.getChunkSource().updateManifest(manifest, periodIndex);
            }
            callback.onContinueLoadingRequested(this);
        }
    }

    public void release() {
        for (SRDChunkSampleStream<DashChunkSource> sampleStream : sampleStreams) {
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
        HashMap<Integer, SRDChunkSampleStream<DashChunkSource>> primarySampleStreams = new HashMap<>();

        for (int i = 0; i < selections.length; i++) {
            if (streams[i] instanceof SRDChunkSampleStream) {
                @SuppressWarnings("unchecked")
                SRDChunkSampleStream<DashChunkSource> stream = (SRDChunkSampleStream<DashChunkSource>) streams[i];
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
                    SRDChunkSampleStream<DashChunkSource> stream = buildSampleStream(trackGroupIndex,
                            selections[i], positionUs);
                    primarySampleStreams.put(trackGroupIndex, stream);
                    streams[i] = stream;
                    streamResetFlags[i] = true;
                }
            }
        }
        sampleStreams = newSampleStreamArray(primarySampleStreams.size());
        primarySampleStreams.values().toArray(sampleStreams);
        sequenceableLoader = new SRDCompositeSequenceableLoader(sampleStreams);
        return positionUs;
    }

    @Override
    public void discardBuffer(long positionUs) {
        for (SRDChunkSampleStream<DashChunkSource> sampleStream : sampleStreams) {
            sampleStream.discardUnselectedEmbeddedTracksTo(positionUs);
        }
    }

    @Override
    public boolean continueLoading(long positionUs) {
        long bufferedPosition = getBufferedPositionUs();
        /*If the buffer is full and the playback has started, start replacing chunks.
        It doesn't make sense to replace if the playback hasn't started: we have no information
        from the picker */
        if ((bufferedPosition-positionUs) > maxBufferMs) {
            if (positionUs>0) {
                if (!sequenceableLoader.replaceChunks(positionUs))
                    callback.onContinueLoadingRequested(this);
            } else {
                //Nothing to do
				callback.onContinueLoadingRequested(this);
            }
        } else {
            sequenceableLoader.continueLoading(positionUs);
        }
        // I think the problem is that when LoadControl returns true, the method maybeContinueLoading
        // only gets called when something finishes downloading. Instead, when it returns false, I guess
        // something periodically checks again by calling maybeContinueLoading.
        // So, we need to implement the replacement strategy to make this work.
        // A workaround is to call the following method. Not efficient.
        // It doesn't matter what we return (returned value isn't used in the ExoPlayerImpInternal).
        return true;
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
        for (SRDChunkSampleStream<DashChunkSource> sampleStream : sampleStreams) {
            long rendererBufferedPositionUs = sampleStream.getBufferedPositionUs();
            if (rendererBufferedPositionUs != C.TIME_END_OF_SOURCE) {
                bufferedPositionUs = Math.min(bufferedPositionUs, rendererBufferedPositionUs);
            }
        }
        return bufferedPositionUs == Long.MAX_VALUE ? C.TIME_END_OF_SOURCE : bufferedPositionUs;
    }

    @Override
    public long seekToUs(long positionUs) {
        for (SRDChunkSampleStream<DashChunkSource> sampleStream : sampleStreams) {
            sampleStream.seekToUs(positionUs);
        }
        return positionUs;
    }

    // SequenceableLoader.Callback implementation.

    @Override
    public void onContinueLoadingRequested(SRDChunkSampleStream<DashChunkSource> sampleStream) {
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

            // TODO: parse SupplementalProperty to build a proper object to attach to the TrackGroup (?)

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
    private SRDChunkSampleStream<DashChunkSource> buildSampleStream(int adaptationSetIndex,
                                                                 TrackSelection selection, long positionUs) {
        AdaptationSet adaptationSet = adaptationSets.get(adaptationSetIndex);
        DashChunkSource chunkSource = chunkSourceFactory.createDashChunkSource(
                manifestLoaderErrorThrower, manifest, periodIndex, adaptationSetIndex, selection,
                elapsedRealtimeOffset, /*enableEventMessageTrack*/ false, /*enableCea608Track*/ false);
        SRDChunkSampleStream<DashChunkSource> stream = new SRDChunkSampleStream<>(adaptationSetIndex,adaptationSet.type,
                /*embeddedTrackTypes*/ null, chunkSource, this, allocator, positionUs, minLoadableRetryCount,
                eventDispatcher);
        return stream;
    }

    @SuppressWarnings("unchecked")
    private static SRDChunkSampleStream<DashChunkSource>[] newSampleStreamArray(int length) {
        return new SRDChunkSampleStream[length];
    }
}
