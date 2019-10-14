package fr.unice.i3s.uca4svr.toucan_vr.dynamicEditing.operations.audio;

import com.google.android.exoplayer2.ExoPlayer;

import org.gearvrf.GVRSceneObject;
import org.gearvrf.scene_objects.GVRVideoSceneObjectPlayer;

import fr.unice.i3s.uca4svr.toucan_vr.mediaplayer.TiledExoPlayer;

public class MuteAudio extends AudioOperation {
  @Override
  public void activate(GVRVideoSceneObjectPlayer<ExoPlayer> player, GVRSceneObject videoHolder) {
    ((TiledExoPlayer)player.getPlayer()).setVolume(0.0f);
  }

  @Override
  public void deactivate(GVRVideoSceneObjectPlayer<ExoPlayer> player, GVRSceneObject videoHolder) {
    ((TiledExoPlayer)player.getPlayer()).setVolume(1.0f);
  }

  @Override
  public String getDebugName() {
    return "MUTE";
  }
}
