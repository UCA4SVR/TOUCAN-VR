package fr.unice.i3s.uca4svr.toucan_vr.utils;

import org.gearvrf.GVRSceneObject;

import fr.unice.i3s.uca4svr.toucan_vr.dynamicEditing.DynamicEditingHolder;

public class RotationTool {

  public static void alignUserGazeTo(float roiDegrees, GVRSceneObject videoHolder, DynamicEditingHolder dynamicEditingHolder){

    float normalizedLastRotation = Angles.normalizeAngle(dynamicEditingHolder.lastRotation);
    float currentUserPosition = Angles.getCurrentYAngle(videoHolder.getGVRContext());
    float trigger = Angles.normalizeAngle(currentUserPosition-normalizedLastRotation);
    trigger = Angles.normalizeAngle(trigger-roiDegrees);
    if(Math.abs(trigger) > 30.0f){
      float difference = currentUserPosition - roiDegrees;
      videoHolder.getTransform().setRotationByAxis(difference, 0, 1, 0);

      //videoHolder.getTransform().setRotationByAxis(trigger, 0, 1, 0);
      dynamicEditingHolder.lastRotation = trigger;
    }


  }
}
