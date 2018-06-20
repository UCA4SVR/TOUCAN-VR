package fr.unice.i3s.uca4svr.toucan_vr.dynamicEditing.operations;

import com.google.android.exoplayer2.ExoPlayer;

import org.gearvrf.GVRContext;
import org.gearvrf.GVRSceneObject;

import fr.unice.i3s.uca4svr.toucan_vr.dynamicEditing.DynamicEditingHolder;

import static java.lang.Math.abs;

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

  public boolean isReady(ExoPlayer player) {
    return abs(this.milliseconds - player.getCurrentPosition()) < dynamicEditingHolder.timeThreshold;
  }

  public abstract void activate(GVRSceneObject videoHolder, GVRContext gvrContext);

  public abstract boolean hasToBeTriggeredInContext(GVRContext gvrContext);
}
