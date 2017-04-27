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
 * Adaptations to handle tiled videos using several renderers and codecs in parallel.
 * Copyright 2017 Laboratoire I3S, CNRS, Université côte d'azur
 */
package fr.unice.i3s.uca4svr.toucan_vr.mediaplayer;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.SurfaceTexture;
import android.media.MediaCodec;
import android.media.PlaybackParams;
import android.os.Handler;
import android.support.annotation.IntDef;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.LoadControl;
import com.google.android.exoplayer2.Renderer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.audio.AudioCapabilities;
import com.google.android.exoplayer2.audio.AudioProcessor;
import com.google.android.exoplayer2.audio.AudioRendererEventListener;
import com.google.android.exoplayer2.audio.MediaCodecAudioRenderer;
import com.google.android.exoplayer2.decoder.DecoderCounters;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.drm.FrameworkMediaCrypto;
import com.google.android.exoplayer2.mediacodec.MediaCodecSelector;
import com.google.android.exoplayer2.metadata.Metadata;
import com.google.android.exoplayer2.metadata.MetadataRenderer;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.text.Cue;
import com.google.android.exoplayer2.text.TextRenderer;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.trackselection.TrackSelector;
import com.google.android.exoplayer2.video.MediaCodecVideoRenderer;
import com.google.android.exoplayer2.video.VideoRendererEventListener;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;

/**
 * This class is based on SimpleExoPlayer, and provides an {@link ExoPlayer} implementation.
 * It allows to build a specified number of video renderers, overcoming the limitation of the default class.
 * The number of video renderers can be passed as a parameter to the class constructor.
 * The method setVideoSurface is called from the ExoPlayerSceneObject to provide the surface(s) for the player.
 * The method release() is also called from ExoPlayerSceneObject to release the player.
 *
 * Created by Giuseppe Samela on 12/04/17.
 */

public class TiledExoPlayer implements ExoPlayer {

    /**
     * Modes for using extension renderers.
     */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({EXTENSION_RENDERER_MODE_OFF, EXTENSION_RENDERER_MODE_ON, EXTENSION_RENDERER_MODE_PREFER})
    public @interface ExtensionRendererMode {}
    /**
     * Do not allow use of extension renderers.
     */
    public static final int EXTENSION_RENDERER_MODE_OFF = 0;
    /**
     * Allow use of extension renderers. Extension renderers are indexed after core renderers of the
     * same type. A {@link TrackSelector} that prefers the first suitable renderer will therefore
     * prefer to use a core renderer to an extension renderer in the case that both are able to play
     * a given track.
     */
    public static final int EXTENSION_RENDERER_MODE_ON = 1;
    /**
     * Allow use of extension renderers. Extension renderers are indexed before core renderers of the
     * same type. A {@link TrackSelector} that prefers the first suitable renderer will therefore
     * prefer to use an extension renderer to a core renderer in the case that both are able to play
     * a given track.
     */
    public static final int EXTENSION_RENDERER_MODE_PREFER = 2;
    /**
     * The default maximum duration for which a video renderer can attempt to seamlessly join an
     * ongoing playback.
     */
    public static final long DEFAULT_ALLOWED_VIDEO_JOINING_TIME_MS = 5000;

    private static final String TAG = "TiledExoPlayer";

    protected static final int MAX_DROPPED_VIDEO_FRAME_COUNT_TO_NOTIFY = 50;

    private final ExoPlayer player;
    private final Renderer[] renderers;
    private final ComponentListener componentListener;
    private final Handler mainHandler;
    private final int videoRendererCount;
    private final int audioRendererCount;
    private final Semaphore surfaceInitSemaphore = new Semaphore(1, true);

    private Format videoFormat;
    private Format audioFormat;

    private Surface[] surfaces;
    private int nextSurfaceTileId;
    private boolean ownsSurfaces;
    private boolean released = false;

    @C.VideoScalingMode
    private int videoScalingMode;
    private SurfaceHolder surfaceHolder;
    private TextureView textureView;
    private TextRenderer.Output textOutput;
    private MetadataRenderer.Output metadataOutput;
    private VideoListener videoListener;
    private AudioRendererEventListener audioDebugListener;
    private VideoRendererEventListener videoDebugListener;
    private DecoderCounters videoDecoderCounters;
    private DecoderCounters audioDecoderCounters;
    private int audioSessionId;
    @C.StreamType
    private int audioStreamType;
    private float audioVolume;
    private PlaybackParamsHolder playbackParamsHolder;

    /**
     * The player's main constructor.
     *
     * @param context The {@link Context} associated with the player.
     * @param videoRendererCount The number of video renderers to be built.
     * @param trackSelector The {@link TrackSelector} that will be used by the player
     *                      to select the tracks consumed by each of the available renderers.
     * @param loadControl The {@link LoadControl} that controls when the MediaSource buffers
     *                    more media, and how much media is buffered.
     */
    public TiledExoPlayer(Context context, int videoRendererCount, TrackSelector trackSelector, LoadControl loadControl) {
        mainHandler = new Handler();
        componentListener = new ComponentListener();

        // The number of video renderers should be provided to the class constructor.
        this.videoRendererCount = videoRendererCount;

        // We expect to have a different surface for each of the video tracks
        this.surfaces = new Surface[videoRendererCount];

        // There are no surfaces set at first.
        this.nextSurfaceTileId = 0;

        // Build the renderers.
        ArrayList<Renderer> renderersList = new ArrayList<>();
        buildRenderers(context, mainHandler, /*drmSessionManager*/ null, EXTENSION_RENDERER_MODE_OFF,
                DEFAULT_ALLOWED_VIDEO_JOINING_TIME_MS, renderersList);
        renderers = renderersList.toArray(new Renderer[renderersList.size()]);

        // Obtain counts of audio renderers. We already know the number of video renderers.
        int audioRendererCount = 0;
        for (Renderer renderer : renderers) {
           if(renderer.getTrackType() == C.TRACK_TYPE_AUDIO) {
               audioRendererCount++;
               break;
            }
        }
        this.audioRendererCount = audioRendererCount;

        // Set initial values.
        audioVolume = 1;
        audioSessionId = C.AUDIO_SESSION_ID_UNSET;
        audioStreamType = C.STREAM_TYPE_DEFAULT;
        videoScalingMode = C.VIDEO_SCALING_MODE_DEFAULT;

        // Build the player and associated objects.
        player = ExoPlayerFactory.newInstance(renderers, trackSelector, loadControl);
    }

    /**
     * Sets the video scaling mode.
     * <p>
     * Note that the scaling mode only applies if a {@link MediaCodec}-based video {@link Renderer} is
     * enabled and if the output surface is owned by a {@link android.view.SurfaceView}.
     *
     * @param videoScalingMode The video scaling mode.
     */
    public void setVideoScalingMode(@C.VideoScalingMode int videoScalingMode) {
        this.videoScalingMode = videoScalingMode;
        ExoPlayerMessage[] messages = new ExoPlayerMessage[videoRendererCount];
        int count = 0;
        for (Renderer renderer : renderers) {
            if (renderer.getTrackType() == C.TRACK_TYPE_VIDEO) {
                messages[count++] = new ExoPlayerMessage(renderer, C.MSG_SET_SCALING_MODE,
                        videoScalingMode);
            }
        }
        player.sendMessages(messages);
    }

    /**
     * Returns the video scaling mode.
     */
    public @C.VideoScalingMode int getVideoScalingMode() {
        return videoScalingMode;
    }

    /**
     * Sets the {@link Surface} onto which video will be rendered. The caller is responsible for
     * tracking the lifecycle of the surface, and must clear the surface by calling
     * {@code setVideoSurface(null)} if the surface is destroyed.
     * <p>
     * If the surface is held by a {@link SurfaceView}, {@link TextureView} or {@link SurfaceHolder}
     * then it's recommended to use {@link #setVideoSurfaceView(SurfaceView)},
     * {@link #setVideoTextureView(TextureView)} or {@link #setVideoSurfaceHolder(SurfaceHolder)}
     * rather than this method, since passing the holder allows the player to track the lifecycle of
     * the surface automatically.
     *
     * @param surface The {@link Surface}.
     */
    public void setVideoSurface(Surface surface) {
        removeSurfaceCallbacks();
        setVideoSurfaceInternal(surface, false);
    }

    /**
     * Sets the {@link SurfaceHolder} that holds the {@link Surface} onto which video will be
     * rendered. The player will track the lifecycle of the surface automatically.
     *
     * @param surfaceHolder The surface holder.
     */
    public void setVideoSurfaceHolder(SurfaceHolder surfaceHolder) {
        removeSurfaceCallbacks();
        this.surfaceHolder = surfaceHolder;
        if (surfaceHolder == null) {
            setVideoSurfaceInternal(null, false);
        } else {
            setVideoSurfaceInternal(surfaceHolder.getSurface(), false);
            surfaceHolder.addCallback(componentListener);
        }
    }

    /**
     * Sets the {@link SurfaceView} onto which video will be rendered. The player will track the
     * lifecycle of the surface automatically.
     *
     * @param surfaceView The surface view.
     */
    public void setVideoSurfaceView(SurfaceView surfaceView) {
        setVideoSurfaceHolder(surfaceView.getHolder());
    }

    /**
     * Sets the {@link TextureView} onto which video will be rendered. The player will track the
     * lifecycle of the surface automatically.
     *
     * @param textureView The texture view.
     */
    public void setVideoTextureView(TextureView textureView) {
        removeSurfaceCallbacks();
        this.textureView = textureView;
        if (textureView == null) {
            setVideoSurfaceInternal(null, true);
        } else {
            if (textureView.getSurfaceTextureListener() != null) {
                Log.w(TAG, "Replacing existing SurfaceTextureListener.");
            }
            SurfaceTexture surfaceTexture = textureView.getSurfaceTexture();
            setVideoSurfaceInternal(surfaceTexture == null ? null : new Surface(surfaceTexture), true);
            textureView.setSurfaceTextureListener(componentListener);
        }
    }

    /**
     * Sets the stream type for audio playback (see {@link C.StreamType} and
     * {@link android.media.AudioTrack#AudioTrack(int, int, int, int, int, int)}). If the stream type
     * is not set, audio renderers use {@link C#STREAM_TYPE_DEFAULT}.
     * <p>
     * Note that when the stream type changes, the AudioTrack must be reinitialized, which can
     * introduce a brief gap in audio output. Note also that tracks in the same audio session must
     * share the same routing, so a new audio session id will be generated.
     *
     * @param audioStreamType The stream type for audio playback.
     */
    public void setAudioStreamType(@C.StreamType int audioStreamType) {
        this.audioStreamType = audioStreamType;
        ExoPlayerMessage[] messages = new ExoPlayerMessage[audioRendererCount];
        int count = 0;
        for (Renderer renderer : renderers) {
            if (renderer.getTrackType() == C.TRACK_TYPE_AUDIO) {
                messages[count++] = new ExoPlayerMessage(renderer, C.MSG_SET_STREAM_TYPE, audioStreamType);
            }
        }
        player.sendMessages(messages);
    }

    /**
     * Returns the stream type for audio playback.
     */
    public @C.StreamType int getAudioStreamType() {
        return audioStreamType;
    }

    /**
     * Sets the audio volume, with 0 being silence and 1 being unity gain.
     *
     * @param audioVolume The audio volume.
     */
    public void setVolume(float audioVolume) {
        this.audioVolume = audioVolume;
        ExoPlayerMessage[] messages = new ExoPlayerMessage[audioRendererCount];
        int count = 0;
        for (Renderer renderer : renderers) {
            if (renderer.getTrackType() == C.TRACK_TYPE_AUDIO) {
                messages[count++] = new ExoPlayerMessage(renderer, C.MSG_SET_VOLUME, audioVolume);
            }
        }
        player.sendMessages(messages);
    }

    /**
     * Returns the audio volume, with 0 being silence and 1 being unity gain.
     */
    public float getVolume() {
        return audioVolume;
    }

    /**
     * Sets the {@link PlaybackParams} governing audio playback.
     *
     * @param params The {@link PlaybackParams}, or null to clear any previously set parameters.
     */
    @TargetApi(23)
    public void setPlaybackParams(PlaybackParams params) {
        if (params != null) {
            // The audio renderers will call this on the playback thread to ensure they can query
            // parameters without failure. We do the same up front, which is redundant except that it
            // ensures an immediate call to getPlaybackParams will retrieve the instance with defaults
            // allowed, rather than this change becoming visible sometime later once the audio renderers
            // receive the parameters.
            params.allowDefaults();
            playbackParamsHolder = new PlaybackParamsHolder(params);
        } else {
            playbackParamsHolder = null;
        }
        ExoPlayerMessage[] messages = new ExoPlayerMessage[audioRendererCount];
        int count = 0;
        for (Renderer renderer : renderers) {
            if (renderer.getTrackType() == C.TRACK_TYPE_AUDIO) {
                messages[count++] = new ExoPlayerMessage(renderer, C.MSG_SET_PLAYBACK_PARAMS, params);
            }
        }
        player.sendMessages(messages);
    }

    /**
     * Returns the {@link PlaybackParams} governing audio playback, or null if not set.
     */
    @TargetApi(23)
    public PlaybackParams getPlaybackParams() {
        return playbackParamsHolder == null ? null : playbackParamsHolder.params;
    }

    /**
     * Returns the video format currently being played, or null if no video is being played.
     */
    public Format getVideoFormat() {
        return videoFormat;
    }

    /**
     * Returns the audio format currently being played, or null if no audio is being played.
     */
    public Format getAudioFormat() {
        return audioFormat;
    }

    /**
     * Returns the audio session identifier, or {@link C#AUDIO_SESSION_ID_UNSET} if not set.
     */
    public int getAudioSessionId() {
        return audioSessionId;
    }

    /**
     * Returns {@link DecoderCounters} for video, or null if no video is being played.
     */
    public DecoderCounters getVideoDecoderCounters() {
        return videoDecoderCounters;
    }

    /**
     * Returns {@link DecoderCounters} for audio, or null if no audio is being played.
     */
    public DecoderCounters getAudioDecoderCounters() {
        return audioDecoderCounters;
    }

    /**
     * Sets a listener to receive video events.
     *
     * @param listener The listener.
     */
    public void setVideoListener(VideoListener listener) {
        videoListener = listener;
    }

    /**
     * Sets a listener to receive debug events from the video renderer.
     *
     * @param listener The listener.
     */
    public void setVideoDebugListener(VideoRendererEventListener listener) {
        videoDebugListener = listener;
    }

    /**
     * Sets a listener to receive debug events from the audio renderer.
     *
     * @param listener The listener.
     */
    public void setAudioDebugListener(AudioRendererEventListener listener) {
        audioDebugListener = listener;
    }

    /**
     * Sets an output to receive text events.
     *
     * @param output The output.
     */
    public void setTextOutput(TextRenderer.Output output) {
        textOutput = output;
    }

    /**
     * Sets a listener to receive metadata events.
     *
     * @param output The output.
     */
    public void setMetadataOutput(MetadataRenderer.Output output) {
        metadataOutput = output;
    }

    /**
     * Sets the Id of the tile renderer to which the surface will be handed out when next calling
     * setVideoSurface.
     * Ensure a call to setVideoSurface is done before calling this method again, subsequent calls
     * will be blocked waiting until setVideoSurface is called.
     *
     * @param nextSurfaceTileId
     *      The Id of the tile renderer to which the next provided surface is given.
     */
    public void setNextSurfaceTileId(int nextSurfaceTileId) {
        try {
            surfaceInitSemaphore.acquire();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        this.nextSurfaceTileId = nextSurfaceTileId;
    }

    // ExoPlayer implementation

    @Override
    public void addListener(EventListener listener) {
        player.addListener(listener);
    }

    @Override
    public void removeListener(EventListener listener) {
        player.removeListener(listener);
    }

    @Override
    public int getPlaybackState() {
        return player.getPlaybackState();
    }

    @Override
    public void prepare(MediaSource mediaSource) {
        player.prepare(mediaSource);
    }

    @Override
    public void prepare(MediaSource mediaSource, boolean resetPosition, boolean resetState) {
        player.prepare(mediaSource, resetPosition, resetState);
    }

    @Override
    public void setPlayWhenReady(boolean playWhenReady) {
        player.setPlayWhenReady(playWhenReady);
    }

    @Override
    public boolean getPlayWhenReady() {
        return player.getPlayWhenReady();
    }

    @Override
    public boolean isLoading() {
        return player.isLoading();
    }

    @Override
    public void seekToDefaultPosition() {
        player.seekToDefaultPosition();
    }

    @Override
    public void seekToDefaultPosition(int windowIndex) {
        player.seekToDefaultPosition(windowIndex);
    }

    @Override
    public void seekTo(long positionMs) {
        player.seekTo(positionMs);
    }

    @Override
    public void seekTo(int windowIndex, long positionMs) {
        player.seekTo(windowIndex, positionMs);
    }

    @Override
    public void stop() {
        player.stop();
    }

    /**
     * Releases the player.
     *
     * All the surfaces are released before releasing the player itself if the player is responsible
     * for the surfaces.
     *
     */
    @Override
    public void release() {
        if (!released) {
            for (int i = 0; i < surfaces.length; i++) {
                Surface surface = surfaces[i];
                if (ownsSurfaces && surface != null) {
                    surface.release();
                }
                surfaces[i] = null;
            }

            player.release();
            removeSurfaceCallbacks();
            released = true;
        }
    }

    @Override
    public void sendMessages(ExoPlayerMessage... messages) {
        player.sendMessages(messages);
    }

    @Override
    public void blockingSendMessages(ExoPlayerMessage... messages) {
        player.blockingSendMessages(messages);
    }

    @Override
    public int getRendererCount() {
        return player.getRendererCount();
    }

    @Override
    public int getRendererType(int index) {
        return player.getRendererType(index);
    }

    @Override
    public TrackGroupArray getCurrentTrackGroups() {
        return player.getCurrentTrackGroups();
    }

    @Override
    public TrackSelectionArray getCurrentTrackSelections() {
        return player.getCurrentTrackSelections();
    }

    @Override
    public Timeline getCurrentTimeline() {
        return player.getCurrentTimeline();
    }

    @Override
    public Object getCurrentManifest() {
        return player.getCurrentManifest();
    }

    @Override
    public int getCurrentPeriodIndex() {
        return player.getCurrentPeriodIndex();
    }

    @Override
    public int getCurrentWindowIndex() {
        return player.getCurrentWindowIndex();
    }

    @Override
    public long getDuration() {
        return player.getDuration();
    }

    @Override
    public long getCurrentPosition() {
        return player.getCurrentPosition();
    }

    @Override
    public long getBufferedPosition() {
        return player.getBufferedPosition();
    }

    @Override
    public int getBufferedPercentage() {
        return player.getBufferedPercentage();
    }

    @Override
    public boolean isCurrentWindowDynamic() {
        return player.isCurrentWindowDynamic();
    }

    @Override
    public boolean isCurrentWindowSeekable() {
        return player.isCurrentWindowSeekable();
    }

    // Renderer building.

    private void buildRenderers(Context context, Handler mainHandler,
                                DrmSessionManager<FrameworkMediaCrypto> drmSessionManager,
                                @TiledExoPlayer.ExtensionRendererMode int extensionRendererMode, long allowedVideoJoiningTimeMs,
                                ArrayList<Renderer> out) {
        buildVideoRenderers(context, mainHandler, drmSessionManager, extensionRendererMode,
                componentListener, allowedVideoJoiningTimeMs, out);
        buildAudioRenderers(context, mainHandler, drmSessionManager, extensionRendererMode,
                componentListener, buildAudioProcessors(), out);
        buildTextRenderers(context, mainHandler, extensionRendererMode, componentListener, out);
        buildMetadataRenderers(context, mainHandler, extensionRendererMode, componentListener, out);
        buildMiscellaneousRenderers(context, mainHandler, extensionRendererMode, out);
    }

    /**
     * Builds video renderers for use by the player.
     *
     * @param context The {@link Context} associated with the player.
     * @param mainHandler A handler associated with the main thread's looper.
     * @param drmSessionManager An optional {@link DrmSessionManager}. May be null if the player will
     *     not be used for DRM protected playbacks.
     * @param extensionRendererMode The extension renderer mode.
     * @param eventListener An event listener.
     * @param allowedVideoJoiningTimeMs The maximum duration in milliseconds for which video renderers
     *     can attempt to seamlessly join an ongoing playback.
     * @param out An array to which the built renderers should be appended.
     */
    protected void buildVideoRenderers(Context context, Handler mainHandler,
                                       DrmSessionManager<FrameworkMediaCrypto> drmSessionManager,
                                       @TiledExoPlayer.ExtensionRendererMode int extensionRendererMode, VideoRendererEventListener eventListener,
                                       long allowedVideoJoiningTimeMs, ArrayList<Renderer> out) {
        for (int i = 0; i < videoRendererCount; i++)
            out.add(new MediaCodecVideoRenderer(context, MediaCodecSelector.DEFAULT,
                    allowedVideoJoiningTimeMs, drmSessionManager, false, mainHandler, eventListener,
                    MAX_DROPPED_VIDEO_FRAME_COUNT_TO_NOTIFY));

        if (extensionRendererMode == EXTENSION_RENDERER_MODE_OFF) {
            return;
        }
        int extensionRendererIndex = out.size();
        if (extensionRendererMode == EXTENSION_RENDERER_MODE_PREFER) {
            extensionRendererIndex--;
        }
        try {
            Class<?> clazz =
                    Class.forName("com.google.android.exoplayer2.ext.vp9.LibvpxVideoRenderer");
            Constructor<?> constructor = clazz.getConstructor(boolean.class, long.class, Handler.class,
                    VideoRendererEventListener.class, int.class);
            Renderer renderer = (Renderer) constructor.newInstance(true, allowedVideoJoiningTimeMs,
                    mainHandler, componentListener, MAX_DROPPED_VIDEO_FRAME_COUNT_TO_NOTIFY);
            out.add(extensionRendererIndex++, renderer);
            Log.i(TAG, "Loaded LibvpxVideoRenderer.");
        } catch (ClassNotFoundException e) {
            // Expected if the app was built without the extension.
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Builds audio renderers for use by the player.
     *
     * @param context The {@link Context} associated with the player.
     * @param mainHandler A handler associated with the main thread's looper.
     * @param drmSessionManager An optional {@link DrmSessionManager}. May be null if the player will
     *     not be used for DRM protected playbacks.
     * @param extensionRendererMode The extension renderer mode.
     * @param eventListener An event listener.
     * @param audioProcessors An array of {@link AudioProcessor}s that will process PCM audio buffers
     *     before output. May be empty.
     * @param out An array to which the built renderers should be appended.
     */
    protected void buildAudioRenderers(Context context, Handler mainHandler,
                                       DrmSessionManager<FrameworkMediaCrypto> drmSessionManager,
                                       @TiledExoPlayer.ExtensionRendererMode int extensionRendererMode, AudioRendererEventListener eventListener,
                                       AudioProcessor[] audioProcessors, ArrayList<Renderer> out) {
        out.add(new MediaCodecAudioRenderer(MediaCodecSelector.DEFAULT, drmSessionManager, true,
                mainHandler, eventListener, AudioCapabilities.getCapabilities(context), audioProcessors));

        if (extensionRendererMode == EXTENSION_RENDERER_MODE_OFF) {
            return;
        }
        int extensionRendererIndex = out.size();
        if (extensionRendererMode == EXTENSION_RENDERER_MODE_PREFER) {
            extensionRendererIndex--;
        }

        try {
            Class<?> clazz =
                    Class.forName("com.google.android.exoplayer2.ext.opus.LibopusAudioRenderer");
            Constructor<?> constructor = clazz.getConstructor(Handler.class,
                    AudioRendererEventListener.class, AudioProcessor[].class);
            Renderer renderer = (Renderer) constructor.newInstance(mainHandler, componentListener,
                    audioProcessors);
            out.add(extensionRendererIndex++, renderer);
            Log.i(TAG, "Loaded LibopusAudioRenderer.");
        } catch (ClassNotFoundException e) {
            // Expected if the app was built without the extension.
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        try {
            Class<?> clazz =
                    Class.forName("com.google.android.exoplayer2.ext.flac.LibflacAudioRenderer");
            Constructor<?> constructor = clazz.getConstructor(Handler.class,
                    AudioRendererEventListener.class, AudioProcessor[].class);
            Renderer renderer = (Renderer) constructor.newInstance(mainHandler, componentListener,
                    audioProcessors);
            out.add(extensionRendererIndex++, renderer);
            Log.i(TAG, "Loaded LibflacAudioRenderer.");
        } catch (ClassNotFoundException e) {
            // Expected if the app was built without the extension.
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        try {
            Class<?> clazz =
                    Class.forName("com.google.android.exoplayer2.ext.ffmpeg.FfmpegAudioRenderer");
            Constructor<?> constructor = clazz.getConstructor(Handler.class,
                    AudioRendererEventListener.class, AudioProcessor[].class);
            Renderer renderer = (Renderer) constructor.newInstance(mainHandler, componentListener,
                    audioProcessors);
            out.add(extensionRendererIndex++, renderer);
            Log.i(TAG, "Loaded FfmpegAudioRenderer.");
        } catch (ClassNotFoundException e) {
            // Expected if the app was built without the extension.
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Builds text renderers for use by the player.
     *
     * @param context The {@link Context} associated with the player.
     * @param mainHandler A handler associated with the main thread's looper.
     * @param extensionRendererMode The extension renderer mode.
     * @param output An output for the renderers.
     * @param out An array to which the built renderers should be appended.
     */
    protected void buildTextRenderers(Context context, Handler mainHandler,
                                      @TiledExoPlayer.ExtensionRendererMode int extensionRendererMode, TextRenderer.Output output,
                                      ArrayList<Renderer> out) {
        out.add(new TextRenderer(output, mainHandler.getLooper()));
    }

    /**
     * Builds metadata renderers for use by the player.
     *
     * @param context The {@link Context} associated with the player.
     * @param mainHandler A handler associated with the main thread's looper.
     * @param extensionRendererMode The extension renderer mode.
     * @param output An output for the renderers.
     * @param out An array to which the built renderers should be appended.
     */
    protected void buildMetadataRenderers(Context context, Handler mainHandler,
                                          @TiledExoPlayer.ExtensionRendererMode int extensionRendererMode, MetadataRenderer.Output output,
                                          ArrayList<Renderer> out) {
        out.add(new MetadataRenderer(output, mainHandler.getLooper()));
    }

    /**
     * Builds any miscellaneous renderers used by the player.
     *
     * @param context The {@link Context} associated with the player.
     * @param mainHandler A handler associated with the main thread's looper.
     * @param extensionRendererMode The extension renderer mode.
     * @param out An array to which the built renderers should be appended.
     */
    protected void buildMiscellaneousRenderers(Context context, Handler mainHandler,
                                               @TiledExoPlayer.ExtensionRendererMode int extensionRendererMode, ArrayList<Renderer> out) {
        // Do nothing.
    }

    /**
     * Builds an array of {@link AudioProcessor}s that will process PCM audio before output.
     */
    protected AudioProcessor[] buildAudioProcessors() {
        return new AudioProcessor[0];
    }

    // Internal methods.

    private void removeSurfaceCallbacks() {
        if (textureView != null) {
            if (textureView.getSurfaceTextureListener() != componentListener) {
                Log.w(TAG, "SurfaceTextureListener already unset or replaced.");
            } else {
                textureView.setSurfaceTextureListener(null);
            }
            textureView = null;
        }
        if (surfaceHolder != null) {
            surfaceHolder.removeCallback(componentListener);
            surfaceHolder = null;
        }
    }

    /**
     * Sets the {@link Surface} onto which video will be rendered.
     * This method can be now called multiple times, providing a different Surface every time.
     * Each new surface should be correctly given to the proper video renderer.
     * Use {@link #setNextSurfaceTileId(int)} to set the id of the tile corresponding to the given
     * surface before calling this method (or any method that subsequently calls this method)
     */
    private void setVideoSurfaceInternal(Surface surface, boolean ownsSurface) {
        int count = 0;
        for (Renderer renderer : renderers) {
            if (renderer.getTrackType() == C.TRACK_TYPE_VIDEO && count == nextSurfaceTileId) {
                player.sendMessages(new ExoPlayer.ExoPlayerMessage(renderer, C.MSG_SET_SURFACE, surface));
                surfaces[nextSurfaceTileId]=surface;
                break;
            } else {
                count++;
            }
        }
        this.ownsSurfaces = ownsSurface;
        surfaceInitSemaphore.release();
    }

    private final class ComponentListener implements VideoRendererEventListener,
            AudioRendererEventListener, TextRenderer.Output, MetadataRenderer.Output,
            SurfaceHolder.Callback, TextureView.SurfaceTextureListener {

        // VideoRendererEventListener implementation

        @Override
        public void onVideoEnabled(DecoderCounters counters) {
            videoDecoderCounters = counters;
            if (videoDebugListener != null) {
                videoDebugListener.onVideoEnabled(counters);
            }
        }

        @Override
        public void onVideoDecoderInitialized(String decoderName, long initializedTimestampMs,
                                              long initializationDurationMs) {
            if (videoDebugListener != null) {
                videoDebugListener.onVideoDecoderInitialized(decoderName, initializedTimestampMs,
                        initializationDurationMs);
            }
        }

        @Override
        public void onVideoInputFormatChanged(Format format) {
            videoFormat = format;
            if (videoDebugListener != null) {
                videoDebugListener.onVideoInputFormatChanged(format);
            }
        }

        @Override
        public void onDroppedFrames(int count, long elapsed) {
            if (videoDebugListener != null) {
                videoDebugListener.onDroppedFrames(count, elapsed);
            }
        }

        @Override
        public void onVideoSizeChanged(int width, int height, int unappliedRotationDegrees,
                                       float pixelWidthHeightRatio) {
            if (videoListener != null) {
                videoListener.onVideoSizeChanged(width, height, unappliedRotationDegrees,
                        pixelWidthHeightRatio);
            }
            if (videoDebugListener != null) {
                videoDebugListener.onVideoSizeChanged(width, height, unappliedRotationDegrees,
                        pixelWidthHeightRatio);
            }
        }

        @Override
        public void onRenderedFirstFrame(Surface surface) {
            if (videoListener != null /*&& TiledExoPlayer.this.surface == surface*/) {
                videoListener.onRenderedFirstFrame();
            }
            if (videoDebugListener != null) {
                videoDebugListener.onRenderedFirstFrame(surface);
            }
        }

        @Override
        public void onVideoDisabled(DecoderCounters counters) {
            if (videoDebugListener != null) {
                videoDebugListener.onVideoDisabled(counters);
            }
            videoFormat = null;
            videoDecoderCounters = null;
        }

        // AudioRendererEventListener implementation

        @Override
        public void onAudioEnabled(DecoderCounters counters) {
            audioDecoderCounters = counters;
            if (audioDebugListener != null) {
                audioDebugListener.onAudioEnabled(counters);
            }
        }

        @Override
        public void onAudioSessionId(int sessionId) {
            audioSessionId = sessionId;
            if (audioDebugListener != null) {
                audioDebugListener.onAudioSessionId(sessionId);
            }
        }

        @Override
        public void onAudioDecoderInitialized(String decoderName, long initializedTimestampMs,
                                              long initializationDurationMs) {
            if (audioDebugListener != null) {
                audioDebugListener.onAudioDecoderInitialized(decoderName, initializedTimestampMs,
                        initializationDurationMs);
            }
        }

        @Override
        public void onAudioInputFormatChanged(Format format) {
            audioFormat = format;
            if (audioDebugListener != null) {
                audioDebugListener.onAudioInputFormatChanged(format);
            }
        }

        @Override
        public void onAudioTrackUnderrun(int bufferSize, long bufferSizeMs,
                                         long elapsedSinceLastFeedMs) {
            if (audioDebugListener != null) {
                audioDebugListener.onAudioTrackUnderrun(bufferSize, bufferSizeMs, elapsedSinceLastFeedMs);
            }
        }

        @Override
        public void onAudioDisabled(DecoderCounters counters) {
            if (audioDebugListener != null) {
                audioDebugListener.onAudioDisabled(counters);
            }
            audioFormat = null;
            audioDecoderCounters = null;
            audioSessionId = C.AUDIO_SESSION_ID_UNSET;
        }

        // TextRenderer.Output implementation

        @Override
        public void onCues(List<Cue> cues) {
            if (textOutput != null) {
                textOutput.onCues(cues);
            }
        }

        // MetadataRenderer.Output implementation

        @Override
        public void onMetadata(Metadata metadata) {
            if (metadataOutput != null) {
                metadataOutput.onMetadata(metadata);
            }
        }

        // SurfaceHolder.Callback implementation

        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            setVideoSurfaceInternal(holder.getSurface(), false);
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            // Do nothing.
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            setVideoSurfaceInternal(null, false);
        }

        // TextureView.SurfaceTextureListener implementation

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int width, int height) {
            setVideoSurfaceInternal(new Surface(surfaceTexture), true);
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int width, int height) {
            // Do nothing.
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
            setVideoSurfaceInternal(null, true);
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {
            // Do nothing.
        }

    }

    @TargetApi(23)
    private static final class PlaybackParamsHolder {

        public final PlaybackParams params;

        public PlaybackParamsHolder(PlaybackParams params) {
            this.params = params;
        }

    }

    /**
     * A listener for video rendering information from a {@link TiledExoPlayer}.
     */
    public interface VideoListener {

        /**
         * Called each time there's a change in the size of the video being rendered.
         *
         * @param width The video width in pixels.
         * @param height The video height in pixels.
         * @param unappliedRotationDegrees For videos that require a rotation, this is the clockwise
         *     rotation in degrees that the application should apply for the video for it to be rendered
         *     in the correct orientation. This value will always be zero on API levels 21 and above,
         *     since the renderer will apply all necessary rotations internally. On earlier API levels
         *     this is not possible. Applications that use {@link android.view.TextureView} can apply
         *     the rotation by calling {@link android.view.TextureView#setTransform}. Applications that
         *     do not expect to encounter rotated videos can safely ignore this parameter.
         * @param pixelWidthHeightRatio The width to height ratio of each pixel. For the normal case
         *     of square pixels this will be equal to 1.0. Different values are indicative of anamorphic
         *     content.
         */
        void onVideoSizeChanged(int width, int height, int unappliedRotationDegrees,
                                float pixelWidthHeightRatio);

        /**
         * Called when a frame is rendered for the first time since setting the surface, and when a
         * frame is rendered for the first time since a video track was selected.
         */
        void onRenderedFirstFrame();

    }
}
