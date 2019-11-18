package fr.unice.i3s.uca4svr.toucan_vr.utils;

import org.junit.Test;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

public class AnglesTest {

  @Test
  public void getDifferenceOnExtremeTest() {
    float diff = Angles.getDifference(180,-180);
    assertEquals(0f, diff);

    diff = Angles.getDifference(180,-180);
    assertEquals(0f,diff);

    diff = Angles.getDifference(0,-0);
    assertEquals(0f, diff);
  }


  /**
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
   */
  @Test
  public void getDifferenceGivesTheSmallestRotationTest() {
    float diff = Angles.getDifference(-100,100);
    assertEquals(-160f,diff);

    diff = Angles.getDifference(50,-50);
    assertEquals(-100f,diff);

    diff = Angles.getDifference(-90,90);
    assertEquals(180f,diff);

    diff = Angles.getDifference(90,-90);
    assertEquals(-180f,diff);

    diff = Angles.getDifference(-137, 90);
    assertEquals(-133f,diff);
  }

  @Test
  public void isAngleInAngleFieldNormalTest() {
    int currentAngle = 150;
    int middleAngle = 150;
    int degreesOfFreedom = 50;
    boolean result = Angles.isAngleInAngleField(currentAngle,middleAngle,degreesOfFreedom);
    assertTrue(result);

    currentAngle = 125;
    result = Angles.isAngleInAngleField(currentAngle,middleAngle,degreesOfFreedom);
    assertTrue(result);

    currentAngle = 124;
    result = Angles.isAngleInAngleField(currentAngle,middleAngle,degreesOfFreedom);
    assertFalse(result);

    currentAngle = 175;
    result = Angles.isAngleInAngleField(currentAngle,middleAngle,degreesOfFreedom);
    assertTrue(result);

    currentAngle = 176;
    result = Angles.isAngleInAngleField(currentAngle,middleAngle,degreesOfFreedom);
    assertFalse(result);
  }

  @Test
  public void isAngleInAngleFieldOnExtremesTest() {
    int currentAngle = 180;
    int middleAngle = 180;
    int degreesOfFreedom = 60; // min 150 deg, max -150
    boolean result = Angles.isAngleInAngleField(currentAngle,middleAngle,degreesOfFreedom);
    assertTrue(result);

    currentAngle = 150;
    result = Angles.isAngleInAngleField(currentAngle,middleAngle,degreesOfFreedom);
    assertTrue(result);

    currentAngle = 149;
    result = Angles.isAngleInAngleField(currentAngle,middleAngle,degreesOfFreedom);
    assertFalse(result);

    currentAngle = -150;
    result = Angles.isAngleInAngleField(currentAngle,middleAngle,degreesOfFreedom);
    assertTrue(result);

    currentAngle = -149;
    result = Angles.isAngleInAngleField(currentAngle,middleAngle,degreesOfFreedom);
    assertFalse(result);


    //From middle angle 0
    middleAngle = 0;
    currentAngle = -30;
    result = Angles.isAngleInAngleField(currentAngle,middleAngle,degreesOfFreedom);
    assertTrue(result);

    currentAngle = -31;
    result = Angles.isAngleInAngleField(currentAngle,middleAngle,degreesOfFreedom);
    assertFalse(result);

    currentAngle = 30;
    result = Angles.isAngleInAngleField(currentAngle,middleAngle,degreesOfFreedom);
    assertTrue(result);

    currentAngle = 31;
    result = Angles.isAngleInAngleField(currentAngle,middleAngle,degreesOfFreedom);
    assertFalse(result);
  }

  @Test
  public void addDegreesOnExtremesTest() {
    int startAngle = 150;
    int degreesToAdd = 50;
    int result = (int) Angles.addDegrees(startAngle,degreesToAdd);
    assertEquals(result,-160);

    startAngle = 180;
    degreesToAdd = 1;
    result = (int) Angles.addDegrees(startAngle,degreesToAdd);
    assertEquals(-179,result);
  }

  @Test
  public void subtractDegreesOnExtremesTest() {
    int startAngle = -150;
    int degreesToSubtract = 50;
    int result = (int) Angles.subtractDegrees(startAngle,degreesToSubtract);
    assertEquals(160, result);

    startAngle = 180;
    degreesToSubtract = 1;
    result = (int) Angles.subtractDegrees(startAngle,degreesToSubtract);
    assertEquals(179, result);
  }

  @Test
  public void isToTheRightTest() {
    boolean result = Angles.isMoreToTheRight(0,180);
    assertFalse(result);

    result = Angles.isMoreToTheRight(-180,179);
    assertTrue(result);

    result = Angles.isMoreToTheRight(-180,1);
    assertTrue(result);

    result = Angles.isMoreToTheRight(-180,-1);
    assertFalse(result);

    result = Angles.isMoreToTheRight(-90,91);
    assertTrue(result);
  }
}
