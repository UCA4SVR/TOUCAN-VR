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
package fr.unice.i3s.uca4svr.toucan_vr.permissions;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.util.SparseArray;

import java.util.ArrayList;

/**
 * This class is a singleton made to manage permissions in single activity apps.
 * Gear VR applications are such application and this is why we developed this.
 *
 * The createManager method allow the creation of the manager.
 * It must be called early in the initialization process of the activity (onCreate) to ensure
 * a smoother run.
 *
 * The requestPermission function is called each time a permission is needed.
 * Instances of RequestPermissionResultListener are used as callbacks for the objects asking for
 * permissions.
 *
 * The single activity of the application must override the onRequestPermissionsResult method and
 * call PermissionManager.onRequestPermissionResult forwarding all the parameter.
 *
 * Permission requests are based on IDs defined defined in the class directly.
 * Meaning you'll need to modify the class code if you want to add a new permission set that is
 * not present.
 */
public class PermissionManager {

    private static final String LOG_TAG = "TCN:Perm";

    // Permission requests IDs
    // Requests read and write permissions to external storage
    public static final int PERMISSIONS_REQUEST_EXTERNAL_STORAGE = 1;


    // Internal purpose variables below this point
    private static PermissionManager permissionManager;
    private static SparseArray<ArrayList<RequestPermissionResultListener>> currentListeners
            = new SparseArray<>();

    private Activity activity;

    /**
     * Creates the instance of the manager if activity is not null and an instance does not exist
     * already. Does nothing otherwise.
     *
     * @param activity The main (and only) activity off the application
     */
    public static void createManager(Activity activity) {
        if (activity != null && permissionManager == null) {
            permissionManager = new PermissionManager(activity);
        } else {
            Log.e(LOG_TAG, "Try to create a log manager while one already " +
                    "exists or using a null activity.");
            Log.e(LOG_TAG, "Continue doing nothing, this may produce errors, please " +
                    "verify and correct your app.");
        }
    }

    /**
     * Method called to request a predefined set of permissions associated with one of the Ids
     * provided in the class.
     *
     * If you add an Id to the class, you must come here to define the set of permissions associated
     * to it.
     *
     * @param requestID The id of the required set of permissions
     * @param listener The listener which is called once the permission has been required
     */
    public static void requestPermission(int requestID, RequestPermissionResultListener listener) {
        String[] requestedPermissions = null;

        // populate the requested permission for your group of permissions here
        switch (requestID) {
            case PERMISSIONS_REQUEST_EXTERNAL_STORAGE: {
                requestedPermissions = new String[2];
                requestedPermissions[0] = Manifest.permission.READ_EXTERNAL_STORAGE;
                requestedPermissions[1] = Manifest.permission.WRITE_EXTERNAL_STORAGE;
            }
        }

        // Updating the list of listeners and requesting the permissions.
        // Do not modify unless you know what you are doing.
        if (requestedPermissions != null && requestedPermissions.length > 0) {
            ArrayList<RequestPermissionResultListener> listeners =
                    currentListeners.get(requestID);
            if (listeners == null){
                listeners = new ArrayList<>();
                currentListeners.put(requestID, listeners);
            }
            listeners.add(listener);
            requestPermission(requestedPermissions, requestID);
        }
    }

    /**
     * To be called ONLY by the main (and only) activity of the app inside its own
     * onRequestPermissionsResult method, forwarding all the parameters.
     * @param requestCode forwarded parameter
     * @param permissions forwarded parameter
     * @param grantResults forwarded parameter
     */
    public static void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                                  @NonNull int[] grantResults) {
        // Returns a granted flag only if all the requested permissions are granted
        if (grantResults.length > 0) {
            boolean permissionsGranted = true;
            for (int result : grantResults) {
                permissionsGranted &= result == PackageManager.PERMISSION_GRANTED;
            }
            if (permissionsGranted) {
                reportStatus(requestCode, PackageManager.PERMISSION_GRANTED);
                return;
            }
        }
        reportStatus(requestCode, PackageManager.PERMISSION_DENIED);
    }

    /**
     * The actual request for the permission to the android system.
     * @param requestedPermissions The array of permission to be asked for.
     * @param requestID The callID for the request
     */
    private static void requestPermission(String[] requestedPermissions, int requestID) {

        // Permission is already granted if all requested permissions are granted
        boolean permissionGranted = true;
        for (String permission : requestedPermissions) {
            permissionGranted &= ContextCompat.checkSelfPermission(
                    permissionManager.activity.getApplicationContext(), permission)
                    == PackageManager.PERMISSION_GRANTED;
        }

        if (!permissionGranted) {

            // Should we show an explanation?
            // Yes if it is needed for at least one of the permissions
            boolean needExplanation = false;
            for (String permission : requestedPermissions) {
                needExplanation |= ActivityCompat.shouldShowRequestPermissionRationale(
                        permissionManager.activity, permission);
            }

            if (needExplanation) {

                // Show an explanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.
                // TODO: improve by showing an explanation
                ActivityCompat.requestPermissions(permissionManager.activity,
                        requestedPermissions, requestID);

            } else {
                // No explanation needed, we can request the permission.
                ActivityCompat.requestPermissions(permissionManager.activity,
                        requestedPermissions, requestID);
            }
        } else {
            reportStatus(requestID, PackageManager.PERMISSION_GRANTED);
        }
    }

    private static void reportStatus(int requestID, int status) {
        ArrayList<RequestPermissionResultListener> listeners = currentListeners.get(requestID);
        if (listeners != null) {
            while (listeners.size() > 0) {
                listeners.get(0).onPermissionRequestDone(requestID, status);
                listeners.remove(0);
            }
        }
    }

    private PermissionManager(Activity activity) {
        this.activity = activity;
    }
}
