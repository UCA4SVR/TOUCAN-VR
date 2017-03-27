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

import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.SimpleExoPlayer;

import org.gearvrf.scene_objects.GVRVideoSceneObjectPlayer;

public class ExoplayerSceneObject implements GVRVideoSceneObjectPlayer<ExoPlayer> {

    private SimpleExoPlayer player;

    public ExoplayerSceneObject(SimpleExoPlayer player) {
        this.player = player;
    }

    @Override
    public ExoPlayer getPlayer() {
        return player;
    }

    @Override
    public void setSurface(Surface surface) {
        // Set the surface on which the video will be displayed
        player.setVideoSurface(surface);
    }

    @Override
    public void release() {
        // Release the player if the object is released
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
}
