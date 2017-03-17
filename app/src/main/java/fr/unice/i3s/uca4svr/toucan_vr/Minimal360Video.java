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
 * Displaying head rotations in the debug log
 * Copyright 2017 Laboratoire I3S, CNRS, Université côte d'azur
 * Author: Romaric Pighetti
 */

package fr.unice.i3s.uca4svr.toucan_vr;

import org.gearvrf.GVRContext;
import org.gearvrf.GVRMain;
import org.gearvrf.GVRMesh;
import org.gearvrf.GVRScene;
import org.gearvrf.GVRTransform;
import org.gearvrf.scene_objects.GVRSphereSceneObject;
import org.gearvrf.scene_objects.GVRVideoSceneObject;
import org.gearvrf.scene_objects.GVRVideoSceneObject.GVRVideoType;
import org.gearvrf.scene_objects.GVRVideoSceneObjectPlayer;

import java.util.Locale;

import fr.unice.i3s.uca4svr.toucan_vr.tracking.HeadMotionTracker;

public class Minimal360Video extends GVRMain
{
    // Tag to be  used when logging
    private static final String TAG = "Minimal360Video";

    // The head motion tracker to log head motions
    private HeadMotionTracker mHeadMotionTracker;
    // stores the current number of times onStep has been called, used as the frame number for the
    // head motion logging for now.
    private float mCurrentFrame;

    Minimal360Video(GVRVideoSceneObjectPlayer<?> player) {
        mPlayer = player;
    }

    /** Called when the activity is first created. */
    @Override
    public void onInit(GVRContext gvrContext) {
        GVRScene scene = gvrContext.getMainScene();

        // create sphere / mesh
        GVRSphereSceneObject sphere = new GVRSphereSceneObject(gvrContext, 72, 144, false);
        GVRMesh mesh = sphere.getRenderData().getMesh();
        sphere.getTransform().setScale(100f, 100f, 100f);

        // create video scene
        GVRVideoSceneObject video = new GVRVideoSceneObject( gvrContext, mesh, mPlayer, GVRVideoType.MONO );
        video.setName( "video" );

        // apply video to scene
        scene.addSceneObject( video );

        // Save the context as an instance varaible and not the scene object.
        // The scene object contained in the context may change (a new instance may be created),
        // but the context itself won't change. This allows to retrieve correct information in the
        // onStep method.
        mHeadMotionTracker = new HeadMotionTracker(gvrContext, "karate");
        mCurrentFrame = 0;
    }


    @Override
    public void onStep() {
        // logging with frame number for now
        mHeadMotionTracker.track(mCurrentFrame++);
    }

    private final GVRVideoSceneObjectPlayer<?> mPlayer;
}
