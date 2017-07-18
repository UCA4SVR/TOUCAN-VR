package fr.unice.i3s.uca4svr.toucan_vr.tilespicker;

import android.text.TextUtils;
import android.util.Log;

import org.gearvrf.GVRPicker;
import org.gearvrf.GVRSceneObject;
import org.gearvrf.IPickEvents;

import fr.unice.i3s.uca4svr.toucan_vr.tracking.PickedTilesTracker;

public class TilesPicker implements IPickEvents {

  private static TilesPicker tilesPicker = null;
  private Boolean[] pickedTiles;
  private boolean loggingActivated;
  private PickedTilesTracker pickedTilesTracker;

  private TilesPicker() {
  }

  public static TilesPicker getPicker() {
    if (tilesPicker == null) tilesPicker = new TilesPicker();
    return tilesPicker;
  }

  public synchronized boolean isPicked(int index) {

    if (pickedTiles != null && pickedTiles.length > index) {
      return pickedTiles[index];
    } else {
      return true;
    }
  }

  @Override
  public synchronized void onPick(GVRPicker picker) {
    GVRPicker.GVRPickedObject[] pickedObjects = picker.getPicked();
    pickedTiles = new Boolean[pickedObjects.length];
    for (int i = 0; i < pickedObjects.length; i++)
      pickedTiles[i] = pickedObjects[i] != null;
    //Logging
    if(this.loggingActivated) {
      this.pickedTilesTracker.track((TextUtils.join(",", pickedTiles)).replace("true","1").replace("false","0"));
    }
  }

  @Override
  public void onNoPick(GVRPicker picker) {

  }

  @Override
  public void onEnter(GVRSceneObject sceneObj, GVRPicker.GVRPickedObject collision) {

  }

  @Override
  public void onExit(GVRSceneObject sceneObj) {

  }

  @Override
  public void onInside(GVRSceneObject sceneObj, GVRPicker.GVRPickedObject collision) {

  }

  public void refreshLogger(String logPrefix) {
    this.loggingActivated = true;
    this.pickedTilesTracker = new PickedTilesTracker(logPrefix);
  }

  public void disableLogger() {
    this.loggingActivated = false;
  }
}
