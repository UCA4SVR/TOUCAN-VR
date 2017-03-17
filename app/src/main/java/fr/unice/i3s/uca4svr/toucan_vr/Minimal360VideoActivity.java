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
 * Copyright 2017 Laboratoire I3S, CNR, Université côte d'azur
 * Author: Romaric Pighetti
 */

package fr.unice.i3s.uca4svr.toucan_vr;

import android.content.res.AssetFileDescriptor;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.view.Surface;

import com.google.android.exoplayer.ExoPlaybackException;
import com.google.android.exoplayer.ExoPlayer;
import com.google.android.exoplayer.util.Util;

import org.gearvrf.GVRActivity;
import org.gearvrf.scene_objects.GVRVideoSceneObject;
import org.gearvrf.scene_objects.GVRVideoSceneObjectPlayer;

import java.io.IOException;


public class Minimal360VideoActivity extends GVRActivity {

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!USE_EXO_PLAYER) {
            videoSceneObjectPlayer = makeMediaPlayer();
        } else {
            videoSceneObjectPlayer = makeExoPlayer();
        }

        if (null != videoSceneObjectPlayer) {
            final Minimal360Video main = new Minimal360Video(videoSceneObjectPlayer);
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

    private GVRVideoSceneObjectPlayer<MediaPlayer> makeMediaPlayer() {
        final MediaPlayer mediaPlayer = new MediaPlayer();
        final AssetFileDescriptor afd;
        final String uri = "http://mobile.360heros.com/producers/4630608605686575/9813601418398322/video/video_31b451b7ca49710719b19d22e19d9e60.mp4";

        if (USE_LOCAL_FILE) {
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
        } else {
            try {
                mediaPlayer.setDataSource(uri);
                mediaPlayer.prepare();
            } catch (Exception e) {
                android.util.Log.e("Minimal360Video", "Video was not loaded. Stopping application!");
                return null;
            }
        }

        mediaPlayer.setLooping(true);
        android.util.Log.d("Minimal360Video", "Starting player.");

        return GVRVideoSceneObject.makePlayerInstance(mediaPlayer);
    }

    private GVRVideoSceneObjectPlayer<ExoPlayer> makeExoPlayer() {
        final ExoPlayer player;
        String userAgent = Util.getUserAgent(this, "TOUCAN-VR");
        final String uriManifest = "https://bitmovin-a.akamaihd.net/content/playhouse-vr/mpds/105560.mpd";

        /** The ExoPlayer is now initialized inside the MyExoPlayer class, based on the DemoPlayer app provided by the framework.
         *  Other than playing a local file, we can now stream a 360-video from a manifest file.
         *  Constructors for DashRendererBuilder and ExtractorRendererBuilder (if needed) have been extended to accomodate new parameters.
         *
         *  The following parameters are needed by the constructor:
         *  @param uriManifest          //String with the url of the DASH manifest
         *  @param bufferSegmentSize
         *  @param bufferSegmentCount   //the product segment_size * segment_count is the total buffer size, in bytes
         *  @param minBufferMs          //minimum amount of data that must be buffered before the playback can start (default: 1000 ms)
         *  @param minRebufferMs        //minimum amount of data needed to restart playback after a rebuffering event (default: 5000 ms)
         **/

        if (USE_LOCAL_FILE) {      // N.B. for now the parameters are constants values
            myPlayer = new MyExoPlayer(new ExtractorRendererBuilder(this, userAgent, Uri.parse("asset:///videos_s_3.mp4"),
                    BUFFER_SEGMENT_SIZE, BUFFER_SEGMENT_COUNT), MIN_BUFFER_MS, MIN_REBUFFER_MS);
        } else {
            myPlayer = new MyExoPlayer(new DashRendererBuilder(this, userAgent, uriManifest, null,
                    BUFFER_SEGMENT_SIZE, BUFFER_SEGMENT_COUNT), MIN_BUFFER_MS, MIN_REBUFFER_MS);
        }
        player = myPlayer.prepare();

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
                                player.seekTo(0);
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
                myPlayer.setSurface(surface);
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

    private GVRVideoSceneObjectPlayer<?> videoSceneObjectPlayer;
    private MyExoPlayer myPlayer;

    static final boolean USE_EXO_PLAYER = true;    //boolean value to select whether to play the video with ExoPlayer or MediaPlayer
    static final boolean USE_LOCAL_FILE = false;    //boolean value to select whether to play a local video, or stream it from the internet

    static final int MIN_BUFFER_MS = 1500;
    static final int MIN_REBUFFER_MS = 5000;

    static final int BUFFER_SEGMENT_SIZE = 64 * 1024;
    static final int BUFFER_SEGMENT_COUNT = 256;
}
