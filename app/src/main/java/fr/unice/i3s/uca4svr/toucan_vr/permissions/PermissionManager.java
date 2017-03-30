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

import android.app.Activity;
import android.content.pm.PackageManager;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.SparseArray;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

/**
 * This class allows to manage permissions requests on behalf of an activity.
 * Useful when the activity depends on a variety of components, each requiring its own set of
 * permissions.
 *
 * The best way to use it is to create the object in the onCreate method of the activity and
 * pass it to each created component that needs to ask permissions.
 * The activity must override onRequestPermissionsResult and forward the call to the permission
 * manager onRequestPermissionsResult method for it to work properly.
 *
 * Each permission can be requested only a certain number of times (3 by default) to the user.
 * If not granted after those retries, it will be silently denied, calling the callbacks directly.
 *
 * When asking for permissions, the provided listener's callback is guaranteed to be called only once.
 * Meaning that if other requests are made for the same group of permissions, you won't be notified
 * even if the result is not the same. You will have to make a new request yourself to get a new
 * notification.
 */
public class PermissionManager {

    private final int MAX_RETRIES;

    // The activity for which this permission manager works
    private final Activity activity;
    private final ArrayList<Set<String>> permissionRequests = new ArrayList<>();
    private final SparseArray<ArrayList<RequestPermissionResultListener>> currentListeners
            = new SparseArray<>();
    private final HashMap<String, Integer> numRetries = new HashMap<>();

    /**
     * Creates a new PermissionManager for the specified activity.
     * The default maximum number of retries (3) is used.
     *
     * @param activity The activity for which this manager works
     */
    public PermissionManager(@NonNull Activity activity) {
        this(activity, 5);
    }

    /**
     * Creates a new PermissionManager for the specified activity.
     *
     * @param activity The activity for which this manager works
     * @param maxRetries The maximum number of time a permission can be asked before being
     *                   automatically denied if it has not been granted by the user previously.
     */
    public PermissionManager(@NonNull Activity activity, int maxRetries) {
        this.activity = activity;
        this.MAX_RETRIES = maxRetries;
    }

    /**
     * Method called to request a predefined set of permissions associated with one of the Ids
     * provided in the class.
     *
     * If you add an Id to the class, you must come here to define the set of permissions associated
     * to it.
     *
     * @param requestedPermissions The required set of permissions
     * @param listener The listener which is called once the permissions has been required
     */
    public synchronized int requestPermissions(Set<String> requestedPermissions, RequestPermissionResultListener listener) {
        int requestID = -1;

        // Checking if this exact set of permissions has already been asked and getting its
        // callID (index in the array), else create an entry in the array.
        for (Set<String> permissions : permissionRequests) {
            if (permissions.containsAll(requestedPermissions) &&
                requestedPermissions.containsAll(permissions)) {
                requestID = permissionRequests.indexOf(permissions);
                break;
            }
        }

        if (requestID < 0) {
            permissionRequests.add(requestedPermissions);
            requestID = permissionRequests.size()-1;
        }

        // Updating the list of listeners and requesting the permissions.
        // Do not modify unless you know what you are doing.
        if (requestedPermissions != null && requestedPermissions.size() > 0) {
            ArrayList<RequestPermissionResultListener> listeners =
                    currentListeners.get(requestID);
            if (listeners == null){
                listeners = new ArrayList<>();
                currentListeners.put(requestID, listeners);
            }

            listeners.add(listener);

            if (listeners.size() == 1) {
                requestPermission(requestedPermissions, requestID);
            }

        }

        return requestID;
    }

    public boolean isPermissionGranted(Set<String> requestedPermissions) {
        // Permission is already granted if all requested permissions are granted
        boolean permissionGranted = true;
        for (String permission : requestedPermissions) {
            permissionGranted &= ContextCompat.checkSelfPermission(
                    activity.getApplicationContext(), permission)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return permissionGranted;
    }

    /**
     * To be called ONLY by the holding activity inside its own onRequestPermissionsResult method,
     * forwarding all the parameters.
     * @param requestCode forwarded parameter from the caller
     * @param permissions forwarded parameter from the caller
     * @param grantResults forwarded parameter from the caller
     */
    public synchronized void onRequestPermissionsResult(int requestCode,
                                                        @NonNull String[] permissions,
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
    private void requestPermission(Set<String> requestedPermissions, int requestID) {

        // Permission is already granted if all requested permissions are granted
        if (!isPermissionGranted(requestedPermissions)) {
            // Permission are not granted, lets check how many times they have been asked already
            // We won't update the number of retries here because we may exit without asking them
            // if one has already been asked too much.
            for (String permission : requestedPermissions) {
                Integer retries = numRetries.get(permission);
                if (retries == null) {
                    retries = 0;
                    numRetries.put(permission, retries);
                }
                if (retries >= MAX_RETRIES) {
                    // At least one permission has been required too much, deny everything and stop
                    reportStatus(requestID, PackageManager.PERMISSION_DENIED);
                    return;
                }
            }

            // We can request the permissions, lets first increment the number of retries.
            for (String permission : requestedPermissions) {
                Integer retries = numRetries.get(permission);
                numRetries.put(permission, retries+1);
            }
            ActivityCompat.requestPermissions(activity,
                    requestedPermissions.toArray(new String[requestedPermissions.size()]),
                    requestID);
        } else {
            reportStatus(requestID, PackageManager.PERMISSION_GRANTED);
        }
    }

    /**
     * Reports the status of a request to all the listeners concerned.
     * Listeners are removed from the list once they have been notified.
     * @param requestID the concerned request ID
     * @param status the status, either PERMISSION_GRANTED or PERMISSION_DENIED
     */
    private void reportStatus(int requestID, int status) {
        ArrayList<RequestPermissionResultListener> listeners = currentListeners.get(requestID);
        if (listeners != null) {
            while (listeners.size() > 0) {
                listeners.get(0).onPermissionRequestDone(requestID, status);
                listeners.remove(0);
            }
        }
    }
}
