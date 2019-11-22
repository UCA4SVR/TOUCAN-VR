package fr.unice.i3s.uca4svr.toucan_vr.dynamicEditing.operations;

import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.PlaybackParameters;

import org.gearvrf.GVRContext;
import org.gearvrf.GVRSceneObject;
import org.gearvrf.scene_objects.GVRVideoSceneObjectPlayer;

import java.util.Optional;
import java.util.function.Consumer;

import fr.unice.i3s.uca4svr.toucan_vr.dynamicEditing.DynamicEditingHolder;
import fr.unice.i3s.uca4svr.toucan_vr.dynamicEditing.operations.audio.AudioOperation;
import fr.unice.i3s.uca4svr.toucan_vr.tracking.DynamicOperationsTracker;
import fr.unice.i3s.uca4svr.toucan_vr.tracking.HeadMotionTracker;

public class SlowDown extends DynamicOperation {

  private float slowDownFactor;
  private long realStartTime;
  private long endTimeMs;
  private AudioOperation audioOp;

  public SlowDown(DynamicEditingHolder dynamicEditingHolder, float slowDownFactor) {
    super(dynamicEditingHolder);
    this.slowDownFactor = slowDownFactor;
    endTimeMs = -1;
  }

  public SlowDown(DynamicEditingHolder dynamicEditingHolder) {
    this(dynamicEditingHolder, 0f);
  }

  public void setSlowDownFactor(float slowDownFactor) {
    this.slowDownFactor = slowDownFactor;
  }

  public void setEndTimeMs(long endTimeMs) {
    this.endTimeMs = endTimeMs;
  }

  public void setAudioOperation(AudioOperation audioOp) {
    this.audioOp = audioOp;
  }

  public Optional<AudioOperation> getAudioOperation() {
    return Optional.ofNullable(audioOp);
  }

  @Override
  public boolean isWellDefined() {
    return super.isWellDefined();
  }

  @Override
  public void activate(GVRVideoSceneObjectPlayer<ExoPlayer> player, final GVRSceneObject videoHolder, HeadMotionTracker headMotionTracker, DynamicOperationsTracker tracker) {
    this.realStartTime = player.getPlayer().getCurrentPosition();

    PlaybackParameters playbackParameters = new PlaybackParameters(1/slowDownFactor, 1/slowDownFactor);
    player.getPlayer().setPlaybackParameters(playbackParameters);

    Optional<AudioOperation> audioOperation = getAudioOperation();
    if (audioOperation.isPresent()) {
      audioOperation.get().activate(player, videoHolder);
    }

    if (endTimeMs > 0) {
      while (player.getPlayer().getCurrentPosition() < endTimeMs); // wait to reach end
      player.getPlayer().setPlaybackParameters(PlaybackParameters.DEFAULT);

      if (audioOperation.isPresent()) {
        audioOperation.get().deactivate(player, videoHolder);
      }

    }

    dynamicEditingHolder.advance();
  }

  @Override
  public boolean hasToBeTriggeredInContext(GVRContext gvrContext) {
    return true;
  }

  @Override
  public void logIn(DynamicOperationsTracker tracker, long executionTime) {
    tracker.trackSlowDown(getMilliseconds(), realStartTime, slowDownFactor, endTimeMs, executionTime,
      this.getAudioOperation().isPresent() ? this.getAudioOperation().get().getDebugName() : "DEFAULT");
  }

  @Override
  public int computeIdealTileIndex(int selectedIndex, int adaptationSetIndex, long nextChunkStartTimeUs) {
    return selectedIndex;
  }
}
