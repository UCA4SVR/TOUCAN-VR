/* Copyright 2015 Samsung Electronics Co., LTD
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Modifications:
 * Package name
 * Copyright 2017 Laboratoire I3S, CNRS, Université côte d'azur
 * Author: Romaric Pighetti
 */

package fr.unice.i3s.uca4svr.toucan_vr;

import android.content.res.AssetFileDescriptor;
import android.media.MediaCodec;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.view.MotionEvent;
import android.view.Surface;

import com.google.android.exoplayer.ExoPlaybackException;
import com.google.android.exoplayer.ExoPlayer;
import com.google.android.exoplayer.MediaCodecAudioTrackRenderer;
import com.google.android.exoplayer.MediaCodecSelector;
import com.google.android.exoplayer.MediaCodecVideoTrackRenderer;
import com.google.android.exoplayer.extractor.ExtractorSampleSource;
import com.google.android.exoplayer.upstream.AssetDataSource;
import com.google.android.exoplayer.upstream.DefaultAllocator;

import org.gearvrf.GVRActivity;
import org.gearvrf.scene_objects.GVRVideoSceneObject;
import org.gearvrf.scene_objects.GVRVideoSceneObjectPlayer;

import java.io.IOException;

import fr.unice.i3s.uca4svr.toucan_vr.permissions.PermissionManager;

public class Minimal360VideoActivity extends GVRActivity {
    
    private PermissionManager permissionManager = null;
    private GVRVideoSceneObjectPlayer<?> videoSceneObjectPlayer;
    private Minimal360Video main;
    private long lastDownTime;

    static final boolean USE_EXO_PLAYER = true;

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        permissionManager = new PermissionManager(this);

        if (!USE_EXO_PLAYER) {
            videoSceneObjectPlayer = makeMediaPlayer();
        } else {
            videoSceneObjectPlayer = makeExoPlayer();
        }

        if (null != videoSceneObjectPlayer) {
            main = new Minimal360Video(videoSceneObjectPlayer, permissionManager);
            setMain(main, "gvr.xml");
        }

    }

    @Override
    protected void onPause() {
        super.onPause();
        if (null != videoSceneObjectPlayer) {
            final Object player = videoSceneObjectPlayer.getPlayer();
            if (!USE_EXO_PLAYER) {
                MediaPlayer mediaPlayer = (MediaPlayer) player;
                mediaPlayer.pause();
            } else {
                ExoPlayer exoPlayer = (ExoPlayer) player;
                exoPlayer.setPlayWhenReady(false);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (null != videoSceneObjectPlayer) {
            final Object player = videoSceneObjectPlayer.getPlayer();
            if (!USE_EXO_PLAYER) {
                MediaPlayer mediaPlayer = (MediaPlayer) player;
                mediaPlayer.start();
            } else {
                ExoPlayer exoPlayer = (ExoPlayer) player;
                exoPlayer.setPlayWhenReady(true);
            }
        }
    }

    /**
     * The event is triggered every time the user touches the GearVR trackpad.
     * If the touch event lasts less than 200 ms it is recognized as a "Tap"
     * and the playback is paused or restarted according to the current state.
     */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        super.onTouchEvent(event);
        if (null != videoSceneObjectPlayer) {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN)
                lastDownTime = event.getDownTime();
            if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                if (event.getEventTime() - lastDownTime < 200) {
                    final ExoPlayer exoPlayer = (ExoPlayer) videoSceneObjectPlayer.getPlayer();
                    if (exoPlayer.getPlayWhenReady())
                        exoPlayer.setPlayWhenReady(false);
                    else
                        exoPlayer.setPlayWhenReady(true);
                }
            }
        }
        return true;
    }

    private void onPlaybackEnded() {
        // release the video scene object and put it to null to avoid tap actions
        videoSceneObjectPlayer.release();
        videoSceneObjectPlayer = null;

        // update the 3D scene with the request to remove the headset
        main.createEndScene();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // Forwarding the call to the PermissionManager.
        permissionManager.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    private GVRVideoSceneObjectPlayer<MediaPlayer> makeMediaPlayer() {
        final MediaPlayer mediaPlayer = new MediaPlayer();
        final AssetFileDescriptor afd;

        try {
            afd = getAssets().openFd("videos_s_3.mp4");
            android.util.Log.d("Minimal360Video", "Assets was found.");
            mediaPlayer.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            android.util.Log.d("Minimal360Video", "DataSource was set.");
            afd.close();
            mediaPlayer.prepare();
        } catch (IOException e) {
            e.printStackTrace();
            finish();
            android.util.Log.e("Minimal360Video", "Assets were not loaded. Stopping application!");
            return null;
        }

        mediaPlayer.setLooping(true);
        android.util.Log.d("Minimal360Video", "starting player.");

        return GVRVideoSceneObject.makePlayerInstance(mediaPlayer);
    }

    private GVRVideoSceneObjectPlayer<ExoPlayer> makeExoPlayer() {
        final ExoPlayer player = ExoPlayer.Factory.newInstance(2);

        final AssetDataSource dataSource = new AssetDataSource(this);
        final ExtractorSampleSource sampleSource = new ExtractorSampleSource(Uri.parse("asset:///videos_s_3.mp4"),
                dataSource, new DefaultAllocator(64 * 1024), 64 * 1024 * 256);

        final MediaCodecVideoTrackRenderer videoRenderer = new MediaCodecVideoTrackRenderer(this, sampleSource,
                MediaCodecSelector.DEFAULT, MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT);
        final MediaCodecAudioTrackRenderer audioRenderer = new MediaCodecAudioTrackRenderer(sampleSource,
                MediaCodecSelector.DEFAULT);
        player.prepare(videoRenderer, audioRenderer);

        return new GVRVideoSceneObjectPlayer<ExoPlayer>() {
            @Override
            public ExoPlayer getPlayer() {
                return player;
            }

            @Override
            public void setSurface(final Surface surface) {
                player.addListener(new ExoPlayer.Listener() {
                    @Override
                    public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
                        switch (playbackState) {
                            case ExoPlayer.STATE_BUFFERING:
                                break;
                            case ExoPlayer.STATE_ENDED:
                                // release the video source
                                release();
                                // handle the event in order to rebuild the scene
                                onPlaybackEnded();
                                break;
                            case ExoPlayer.STATE_IDLE:
                                break;
                            case ExoPlayer.STATE_PREPARING:
                                break;
                            case ExoPlayer.STATE_READY:
                                break;
                            default:
                                break;
                        }
                    }

                    @Override
                    public void onPlayWhenReadyCommitted() {
                        surface.release();
                    }

                    @Override
                    public void onPlayerError(ExoPlaybackException error) {
                    }
                });

                player.sendMessage(videoRenderer, MediaCodecVideoTrackRenderer.MSG_SET_SURFACE, surface);
            }

            @Override
            public void release() {
                player.release();
            }

            @Override
            public boolean canReleaseSurfaceImmediately() {
                return false;
            }

            @Override
            public void pause() {
                player.setPlayWhenReady(false);
            }

            @Override
            public void start() {
                player.setPlayWhenReady(true);
            }
        };
    }
}
