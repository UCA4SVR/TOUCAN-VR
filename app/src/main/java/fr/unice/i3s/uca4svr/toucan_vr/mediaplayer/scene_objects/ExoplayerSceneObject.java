/*
 * Copyright 2017 Laboratoire I3S, CNRS, Université côte d'azur
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package fr.unice.i3s.uca4svr.toucan_vr.mediaplayer.scene_objects;

import android.view.Surface;

import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;

import org.gearvrf.scene_objects.GVRVideoSceneObjectPlayer;

import fr.unice.i3s.uca4svr.toucan_vr.mediaplayer.TiledExoPlayer;

public class ExoplayerSceneObject implements GVRVideoSceneObjectPlayer<ExoPlayer> {

    private ExoPlayer player;

    public ExoplayerSceneObject(ExoPlayer player) {
        this.player = player;
    }

    @Override
    public ExoPlayer getPlayer() {
        return player;
    }

    @Override
    public void setSurface(Surface surface) {
        player.addListener(new ExoPlayer.EventListener() {
            @Override
            public void onTimelineChanged(Timeline timeline, Object manifest) {
                // Does Nothing
            }

            @Override
            public void onTracksChanged(TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {
                // Does Nothing
            }

            @Override
            public void onLoadingChanged(boolean isLoading) {
                // Does Nothing
            }

            @Override
            public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
                switch (playbackState) {
                    case ExoPlayer.STATE_ENDED:
                        // handle the event in order to rebuild the scene
                        release();
                        break;
                    default:
                        break;
                }
            }

            @Override
            public void onPlayerError(ExoPlaybackException error) {
                release();
            }

            @Override
            public void onPositionDiscontinuity() {
                // Does Nothing
            }

            @Override
            public void onPlaybackParametersChanged(PlaybackParameters playbackParameters) {

            }
        });
        // Set the surface on which the video will be displayed
        ((TiledExoPlayer) player).setVideoSurface(surface);
    }

    @Override
    public void release() {
        // Release the player if the video object has been released
        player.release();
        canReleaseSurfaceImmediately();
    }

    @Override
    public boolean canReleaseSurfaceImmediately() {
        return false;
    }

    @Override
    public void pause() {
        if (player != null) {
            player.setPlayWhenReady(false);
        }
    }

    @Override
    public void start() {
        if (player != null) {
            player.setPlayWhenReady(true);
        }
    }
}
