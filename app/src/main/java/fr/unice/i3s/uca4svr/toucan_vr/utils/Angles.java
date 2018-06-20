package fr.unice.i3s.uca4svr.toucan_vr.utils;

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

}
