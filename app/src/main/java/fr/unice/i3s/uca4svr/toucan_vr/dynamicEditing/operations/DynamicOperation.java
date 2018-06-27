package fr.unice.i3s.uca4svr.toucan_vr.dynamicEditing.operations;

import com.google.android.exoplayer2.ExoPlayer;

import org.gearvrf.GVRContext;
import org.gearvrf.GVRSceneObject;
import org.gearvrf.scene_objects.GVRVideoSceneObjectPlayer;

import fr.unice.i3s.uca4svr.toucan_vr.dynamicEditing.DynamicEditingHolder;
import fr.unice.i3s.uca4svr.toucan_vr.tracking.DynamicOperationsTracker;

import static java.lang.Math.abs;

/**
 * General dynamic operation implementation.
 * Stores the timing system and the abstract method that each concrete dynamic operation has to implement and specify.
 *
 * @author Julien Lemaire
 */
public abstract class DynamicOperation {
  private int milliseconds;
  private boolean millisecondsFlag;
  protected DynamicEditingHolder dynamicEditingHolder;

  public DynamicOperation(DynamicEditingHolder dynamicEditingHolder) {
    this.millisecondsFlag = false;
    this.dynamicEditingHolder = dynamicEditingHolder;
  }

  public int getMilliseconds() {
    return this.milliseconds;
  }

  public long getMicroseconds() {
    return this.milliseconds*1000;
  }

	public void setMilliseconds(int milliseconds) {
		this.milliseconds = milliseconds;
		this.millisecondsFlag = true;
	}

	public boolean isWellDefined() {
    return this.millisecondsFlag;
  }

  public boolean isReady(long currentTime) {
    return abs(this.milliseconds - currentTime) < dynamicEditingHolder.timeThreshold;
  }

  public abstract void activate(GVRVideoSceneObjectPlayer<ExoPlayer> player, GVRSceneObject videoHolder);

  public abstract boolean hasToBeTriggeredInContext(GVRContext gvrContext);

  public abstract void logIn(DynamicOperationsTracker tracker, long executionTime);

  public abstract int computeIdealTileIndex(int selectedIndex, int adaptationSetIndex, long nextChunkStartTimeUs);
}
