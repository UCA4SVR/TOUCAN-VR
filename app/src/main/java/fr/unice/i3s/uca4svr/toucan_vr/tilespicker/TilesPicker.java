package fr.unice.i3s.uca4svr.toucan_vr.tilespicker;

import android.text.TextUtils;
import android.util.Log;

import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.util.Clock;
import com.google.android.exoplayer2.util.SystemClock;
import java.util.Locale;

import org.gearvrf.GVRPicker;
import org.gearvrf.GVRSceneObject;
import org.gearvrf.IPickEvents;
import org.gearvrf.scene_objects.GVRVideoSceneObjectPlayer;

import fr.unice.i3s.uca4svr.toucan_vr.tracking.PickedTilesTracker;

public class TilesPicker implements IPickEvents {

  private static TilesPicker tilesPicker = null;
  private Boolean[] pickedTiles;
  private boolean loggingActivated;
  private PickedTilesTracker pickedTilesTracker;
  public GVRPicker picker = null;
  public MyRunnableTilesLogging myRunnableTilesLogging;
  public GVRVideoSceneObjectPlayer<ExoPlayer> videoSceneObjectPlayer = null;

  private TilesPicker() {
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
    }

    @Override
    public void run() {
      while(keepRunning()) {
        if (picker != null && tilesPicker.videoSceneObjectPlayer != null) {
          do_logging();
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
