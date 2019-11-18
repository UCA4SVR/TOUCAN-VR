package fr.unice.i3s.uca4svr.toucan_vr.tilespicker;

import android.text.TextUtils;
import android.util.Log;

import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.util.Clock;
import com.google.android.exoplayer2.util.SystemClock;
import java.util.Locale;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.lang.Exception;

import org.gearvrf.GVRPicker;
import org.gearvrf.GVRSceneObject;
import org.gearvrf.IPickEvents;
import org.gearvrf.scene_objects.GVRVideoSceneObjectPlayer;

import fr.unice.i3s.uca4svr.toucan_vr.tracking.PickedTilesTracker;
import fr.unice.i3s.uca4svr.toucan_vr.dynamicEditing.DynamicEditingHolder;

public class TilesPicker implements IPickEvents {

  private static TilesPicker tilesPicker = null;
  private Boolean[] pickedTiles;
  private boolean loggingActivated;
  private PickedTilesTracker pickedTilesTracker;
  public GVRPicker picker = null;
  public MyRunnableTilesLogging myRunnableTilesLogging;
  public GVRVideoSceneObjectPlayer<ExoPlayer> videoSceneObjectPlayer = null;
  private DynamicEditingHolder dynamicEditingHolder;

  private TilesPicker(DynamicEditingHolder dynamicEditingHolder) {
    this.dynamicEditingHolder = dynamicEditingHolder;
    /// Start pickedTiles logging thread
    this.myRunnableTilesLogging = new MyRunnableTilesLogging(this);
    Thread thread = new Thread(this.myRunnableTilesLogging, "MyTilesLogging");
    thread.start();
    //
  }
  // Additions for proper logging
  public class MyRunnableTilesLogging implements Runnable {

    private final Clock clock;
    private boolean doStop = false;
    private String strpickedTiles;
    public TilesPicker tilesPicker;
    GVRVideoSceneObjectPlayer<ExoPlayer> videoSceneObjectPlayer;
    private long curVideoTime;

    public MyRunnableTilesLogging(TilesPicker tilesPicker) {
      super();
      this.tilesPicker = tilesPicker;
      this.clock = new SystemClock();
    }

    public synchronized void doStop() {
      this.doStop = true;
    }

    private synchronized boolean keepRunning() {
      return this.doStop == false;
    }

    private void do_logging() {
      curVideoTime = this.tilesPicker.videoSceneObjectPlayer.getPlayer().getCurrentPosition();
      GVRPicker.GVRPickedObject[] pickedObjects = picker.getPicked();
      pickedTiles = new Boolean[pickedObjects.length];
      for (int i = 0; i < pickedObjects.length; i++) {
        pickedTiles[i] = pickedObjects[i] != null;
      }
      strpickedTiles = (TextUtils.join(",", pickedTiles)).replace("true","1").replace("false","0");
      tilesPicker.pickedTilesTracker.logger.error(String.format(Locale.ENGLISH, "%d,%d,%s", clock.elapsedRealtime(), curVideoTime, strpickedTiles));

      //---- adding picked tiles in holder to be read from Minimal360 and sent back to server
      dynamicEditingHolder.add_chunkIndexes_picked((int)(curVideoTime / 1000 +1));
      dynamicEditingHolder.add_pickedTiles(pickedTiles.clone());
      //------------------------
    }

    @Override
    public void run() {
      while(keepRunning()) {
        if (picker != null && tilesPicker.videoSceneObjectPlayer != null) {
          if (picker.getPicked() != null ) {
            do_logging();
          }
        }
        try {
          Thread.sleep(100L);
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
      }
    }
  }
  /// End of changes for proper logging

//  public static TilesPicker getPicker() {
//    return tilesPicker;
//  }

  public static TilesPicker getPicker(DynamicEditingHolder dynamicEditingHolder) {
    if (tilesPicker == null) {
      tilesPicker = new TilesPicker(dynamicEditingHolder);
    }

    return tilesPicker;
  }

  /*public static TilesPicker getPicker()  throws Exception {
    try {
      return tilesPicker;
    }
    catch(Exception E) {
        throw new Exception("TilesPicker does not exist when called from Pyramidal, and should", E);
    }
  }*/

  public synchronized boolean isPicked(int index) {

    System.out.print("pickedTiles length: " + pickedTiles.length + "\n");
    if (pickedTiles != null && pickedTiles.length > index) {
      return pickedTiles[index];
    } else {
      return true;
    }
  }

  @Override
  public synchronized void onPick(GVRPicker picker) {
//    GVRPicker.GVRPickedObject[] pickedObjects = picker.getPicked();
//    pickedTiles = new Boolean[pickedObjects.length];
//    for (int i = 0; i < pickedObjects.length; i++)
//      pickedTiles[i] = pickedObjects[i] != null;
//    //Logging
//    if(this.loggingActivated) {
//      this.pickedTilesTracker.track((TextUtils.join(",", pickedTiles)).replace("true","1").replace("false","0"));
//    }
    this.picker = picker;
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
