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

import android.Manifest;
import android.content.pm.PackageManager;

import org.gearvrf.GVRContext;
import org.gearvrf.GVRMain;
import org.gearvrf.GVRMesh;
import org.gearvrf.GVRScene;
import org.gearvrf.scene_objects.GVRSphereSceneObject;
import org.gearvrf.scene_objects.GVRVideoSceneObject;
import org.gearvrf.scene_objects.GVRVideoSceneObject.GVRVideoType;
import org.gearvrf.scene_objects.GVRVideoSceneObjectPlayer;

import java.util.HashSet;
import java.util.Set;

import fr.unice.i3s.uca4svr.toucan_vr.permissions.PermissionManager;
import fr.unice.i3s.uca4svr.toucan_vr.permissions.RequestPermissionResultListener;
import fr.unice.i3s.uca4svr.toucan_vr.tracking.HeadMotionTracker;

public class Minimal360Video extends GVRMain implements RequestPermissionResultListener {
    // The associated android context
    private final PermissionManager permissionManager;

    // The GVRContext associated with the scene.
    // Needed by the headMotionTracker
    private GVRContext gvrContext;

    private final GVRVideoSceneObjectPlayer<?> videoSceneObjectPlayer;

    // The head motion tracker to log head motions
    private HeadMotionTracker headMotionTracker;
    // stores the current number of times onStep has been called, used as the frame number for the
    // head motion logging for now.
    private float currentFrame;

    Minimal360Video(GVRVideoSceneObjectPlayer<?> videoSceneObjectPlayer,
                    PermissionManager permissionManager) {
        this.videoSceneObjectPlayer = videoSceneObjectPlayer;
        this.permissionManager = permissionManager;
    }

    /** Called when the activity is first created. */
    @Override
    public void onInit(GVRContext gvrContext) {
        this.gvrContext = gvrContext;
        currentFrame = 0;

        GVRScene scene = gvrContext.getMainScene();

        // create sphere / mesh
        GVRSphereSceneObject sphere = new GVRSphereSceneObject(gvrContext, 72, 144, false);
        GVRMesh mesh = sphere.getRenderData().getMesh();
        sphere.getTransform().setScale(100f, 100f, 100f);

        // create video scene
        GVRVideoSceneObject video = new GVRVideoSceneObject( gvrContext, mesh,
                videoSceneObjectPlayer, GVRVideoType.MONO );
        video.setName( "video" );

        // apply video to scene
        scene.addSceneObject( video );

        // The HeadMotionTracker needs permission to write to external storage.
        // Lets check that now or ask for it if necessary
        Set<String> permissions = new HashSet<>();
        permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE);

        // Only one permission request is done in this component. No need to save the callID to
        // discriminate different permission requests, the callback will be called for these
        // particular permissions only.
        permissionManager.requestPermissions(permissions, this);
    }

    private void initHeadMotionTracker() {
        // Give the context to the logger so that it has access to the camera
        headMotionTracker = new HeadMotionTracker(gvrContext, "karate");
    }


    @Override
    public void onStep() {
        // logging with frame number for now
        if (headMotionTracker != null) {
            headMotionTracker.track(currentFrame);
        }
        currentFrame++;
    }

    @Override
    public void onPermissionRequestDone(int requestID, int result) {
        if (result == PackageManager.PERMISSION_GRANTED && headMotionTracker == null) {
            initHeadMotionTracker();
        }
    }
}
