package fr.unice.i3s.uca4svr.toucan_vr.utils;

import org.gearvrf.GVRContext;
import org.gearvrf.GVRSceneObject;

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
   * Normalizes a given angle from a space between -360 and 360 to a space between -180 and 180
   * @param angle Angle to be normalized
   * @return Normalized angle
   */
  public static float normalizeXAngle(float angle) {
    if((angle>=-90)&&(angle<=90))
      return angle;
    else
      return -1*Math.signum(angle)*(180-abs(angle));
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
    double norm = Math.sqrt(lookAt[1] * lookAt[1] + lookAt[2] * lookAt[2]);
    double cos = lookAt[1] / norm;
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
   * Gives the angle to apply to go from startAngle to endAngle
   *
   *                   *  -90 *
   *                *            *
   *              *                *
   *             *                  *
   *             0              -180/+180
   *             *                  *
   *              *                *
   *               *             *
   *                  *  90   *
   *
   *     Positive direction is counter clock wise
   *
   * @param startAngle the angle where the rotation starts
   * @param endAngle the angle where the rotation ends
   */
  public static float getDifference(float startAngle, float endAngle) {
    float result = -subtractDegrees(startAngle,endAngle);
    if(result == -0f){
      return 0f;
    }else{
      return result;
    }
  }

  /**
   *
   * @param angleToCheck the angle to check
   * @param middleAngle the middle of the angle field
   * @param freedomDegrees the size of the angle field
   * @return true if the angleToCheck is between middleAngle - (freedomDegrees/2) and  middleAngle + (freedomDegrees/2), false otherwise
   */
  public static boolean isAngleInAngleField(float angleToCheck, float middleAngle, int freedomDegrees) {
    float diff = (angleToCheck - middleAngle + 180 + 360) % 360 - 180;
    return diff <= freedomDegrees/2 && diff>= -freedomDegrees/2;
  }

  /**
   * Add degreesToAdd to angle
   * @param angle the start angle
   * @param degreesToAdd the number of degrees to add to the angle
   * @return the result angle of angle + degreesToAdd between 180 & -180 degrees
   */
  public static float addDegrees(float angle, float degreesToAdd) {
    float result = angle + degreesToAdd;
    result += (result>180) ? -360 : (result<-180) ? 360 : 0;
    return result;
  }


  /**
   * Subtract degreesToAdd to angle
   * @param angle the start angle
   * @param degreesToSubtract the number of degrees to subtract to the angle
   * @return the result angle of angle - degreesToAdd between 180 & -180 degrees
   */
  public static float subtractDegrees(float angle, float degreesToSubtract) {
    float result = angle - degreesToSubtract;
    result += (result>180) ? -360 : (result<-180) ? 360 : 0;
    return result;
  }

  public static float getSphereAngle(GVRSceneObject videoHolder){
    return Angles.getCurrentYAngle(videoHolder.getGVRContext()) - videoHolder.getTransform().getRotationYaw();
  }

  /**
   *
   * @param centerAngle the aimed angle
   * @param angleToCheck the current angle
   * @return true if the angle is more close by rotating to the right (rotating clockwise), false otherwise
   */
  public static boolean isMoreToTheRight(float centerAngle, float angleToCheck) {
    return getDifference(centerAngle,angleToCheck)  < 0;
  }
}
