package fr.unice.i3s.uca4svr.toucan_vr.permissions;

/**
 * Listener holding the callback method called when requesting permission using the
 * PermissionManager singleton.
 */
public interface RequestPermissionResultListener {

    /**
     *
     * @param requestID The id of the request made to the manager.
     * @param result either PERMISSION_GRANTED or PERMISSION_DENIED from PackageManager.
     */
    void onPermissionRequestDone(int requestID, int result);
}
