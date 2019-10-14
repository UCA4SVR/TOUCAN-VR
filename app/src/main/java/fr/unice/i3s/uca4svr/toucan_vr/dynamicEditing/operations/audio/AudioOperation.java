package fr.unice.i3s.uca4svr.toucan_vr.dynamicEditing.operations.audio;

import com.google.android.exoplayer2.ExoPlayer;

import org.gearvrf.GVRSceneObject;
import org.gearvrf.scene_objects.GVRVideoSceneObjectPlayer;

public abstract class AudioOperation {
  public abstract void activate(GVRVideoSceneObjectPlayer<ExoPlayer> player, GVRSceneObject videoHolder);
  public abstract void deactivate(GVRVideoSceneObjectPlayer<ExoPlayer> player, GVRSceneObject videoHolder);

  public abstract String getDebugName();
}
