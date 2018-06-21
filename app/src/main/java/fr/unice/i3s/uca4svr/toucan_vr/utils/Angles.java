package fr.unice.i3s.uca4svr.toucan_vr.utils;

import org.gearvrf.GVRContext;

import static java.lang.Math.abs;

public class Angles {

  /**
   * Normalizes a given angle from a space between -360 and 360 to a space between -180 and 180
   * @param angle Angle to be normalized
   * @return Normalized angle
   */
  public static float normalizeAngle(float angle) {
    if((angle>=-180)&&(angle<=180))
      return angle;
    else
      return -1*Math.signum(angle)*(360-abs(angle));
  }



  /**
   * Gets the current user position Y angle (in degrees). It ranges between -180 and 180
   * @return User position
   */

  public static float getCurrentYAngle(GVRContext gvrContext) {
    double angle = 0;
    float[] lookAt = gvrContext.getMainScene().getMainCameraRig().getLookAt();
    // cos = [0], sin = [2]
    double norm = Math.sqrt(lookAt[0] * lookAt[0] + lookAt[2] * lookAt[2]);
    double cos = lookAt[0] / norm;
    cos = abs(cos) > 1 ? Math.signum(cos) : cos;
    if (lookAt[2] == 0) {
      angle = Math.acos(cos);
    } else {
      angle = Math.signum(lookAt[2]) * Math.acos(cos);
    }
    //From radiant to degree + orientation
    return (float)(angle * 180 / Math.PI * -1);
  }


  /**
   * Gets the current user position X angle (in degrees). It ranges between -90 and 90
   * @return User position
   */

  public static float getCurrentXAngle(GVRContext gvrContext) {
    double angle = 0;
    float[] lookAt = gvrContext.getMainScene().getMainCameraRig().getLookAt();
    // cos = [0], sin = [2]
    double norm = Math.sqrt(lookAt[0] * lookAt[0] + lookAt[2] * lookAt[2]);
    angle = Math.atan2(lookAt[1],norm);

    //From radiant to degree + orientation
    return (float)(angle * 180 / Math.PI * -1);
  }

}
