/*
 * Copyright (C) 2014 The Android Open Source Project
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
package fr.unice.i3s.uca4svr.toucan_vr.ExoPlayer;


import android.content.Context;
import android.media.AudioManager;
import android.media.MediaCodec;
import android.net.Uri;
import android.os.Handler;

import com.google.android.exoplayer.MediaCodecAudioTrackRenderer;
import com.google.android.exoplayer.MediaCodecSelector;
import com.google.android.exoplayer.MediaCodecVideoTrackRenderer;
import com.google.android.exoplayer.TrackRenderer;
import com.google.android.exoplayer.audio.AudioCapabilities;
import com.google.android.exoplayer.extractor.Extractor;
import com.google.android.exoplayer.extractor.ExtractorSampleSource;
import com.google.android.exoplayer.upstream.Allocator;
import com.google.android.exoplayer.upstream.DataSource;
import com.google.android.exoplayer.upstream.DefaultAllocator;
import com.google.android.exoplayer.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer.upstream.DefaultUriDataSource;

/**
 * A {@link MyExoPlayer.RendererBuilder} for streams that can be read using an {@link Extractor}.
 */
public class ExtractorRendererBuilder implements MyExoPlayer.RendererBuilder {

  private final Context context;
  private final String userAgent;
  private final Uri uri;
  private final int bufferSegmentSize;
  private final int bufferSegmentCount;

  public ExtractorRendererBuilder(Context context, String userAgent, Uri uri) {
    this.context = context;
    this.userAgent = userAgent;
    this.uri = uri;
    this.bufferSegmentSize = 64 * 1024;
    this.bufferSegmentCount = 256;
  }

  public ExtractorRendererBuilder(Context context, String userAgent, Uri uri, int bufferSegmentSize, int bufferSegmentCount) {
    this.context = context;
    this.userAgent = userAgent;
    this.uri = uri;
    this.bufferSegmentSize = bufferSegmentSize;
    this.bufferSegmentCount = bufferSegmentCount;
  }

  @Override
  public void buildRenderers(MyExoPlayer player) {
    Allocator allocator = new DefaultAllocator(bufferSegmentSize);
    Handler mainHandler = player.getMainHandler();

    // Build the video and audio renderers.
    DefaultBandwidthMeter bandwidthMeter = new DefaultBandwidthMeter(mainHandler, null);
    DataSource dataSource = new DefaultUriDataSource(context, bandwidthMeter, userAgent);
    ExtractorSampleSource sampleSource = new ExtractorSampleSource(uri, dataSource, allocator,
            bufferSegmentSize * bufferSegmentCount, mainHandler, player, 0);
    MediaCodecVideoTrackRenderer videoRenderer = new MediaCodecVideoTrackRenderer(context,
        sampleSource, MediaCodecSelector.DEFAULT, MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT, 5000,
        mainHandler, player, 50);
    MediaCodecAudioTrackRenderer audioRenderer = new MediaCodecAudioTrackRenderer(sampleSource,
        MediaCodecSelector.DEFAULT, null, true, mainHandler, player,
        AudioCapabilities.getCapabilities(context), AudioManager.STREAM_MUSIC);

    // Invoke the callback.
    TrackRenderer[] renderers = new TrackRenderer[MyExoPlayer.RENDERER_COUNT];
    renderers[MyExoPlayer.TYPE_VIDEO] = videoRenderer;
    renderers[MyExoPlayer.TYPE_AUDIO] = audioRenderer;
    player.onRenderers(renderers, bandwidthMeter);
  }

  @Override
  public void cancel() {
    // Do nothing.
  }

}
