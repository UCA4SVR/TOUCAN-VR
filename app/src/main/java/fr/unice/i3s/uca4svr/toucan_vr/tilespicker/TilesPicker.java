package fr.unice.i3s.uca4svr.toucan_vr.tilespicker;

import android.util.Log;

import org.gearvrf.GVRContext;
import org.gearvrf.GVRFrustumPicker;
import org.gearvrf.GVRPicker;
import org.gearvrf.GVRScene;

public class TilesPicker {

    public static GVRFrustumPicker picker;
    private static int[] picked = new int[9];

    public TilesPicker(GVRContext context, GVRScene scene) {
        this.picker = new GVRFrustumPicker(context,scene);
        this.picker.setFrustum(60,1,49,50);
    }

    public static void tilesPicked() {
        if(picker!=null) {
            GVRPicker.GVRPickedObject[] pickedObjects = picker.getPicked();
            if(pickedObjects!=null) {
                for (GVRPicker.GVRPickedObject pickedObject : pickedObjects) {
                    if(pickedObject!=null) {
                        Log.e("SRD", (String) pickedObject.getHitObject().getTag());

                    }
                }
            } else {
                Log.e("SRD","null list");
            }
        } else {
            Log.e("SRD","null picker");
        }
    }


}
