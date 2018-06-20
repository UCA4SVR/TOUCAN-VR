/*
 * Copyright 2017 Laboratoire I3S, CNRS, Université côte d'azur
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package fr.unice.i3s.uca4svr.toucan_vr.dynamicEditing.operations;

import org.gearvrf.GVRContext;
import org.gearvrf.GVRSceneObject;

import fr.unice.i3s.uca4svr.toucan_vr.dynamicEditing.DynamicEditingHolder;
import fr.unice.i3s.uca4svr.toucan_vr.utils.Angles;

import static java.lang.Math.abs;

public class SnapChange extends DynamicOperation {

	private int roiDegrees;
	private int[] foVTiles;
	private boolean roiDegreesFlag;
	private boolean foVTilesFlag;

	public SnapChange(DynamicEditingHolder dynamicEditingHolder) {
	  super(dynamicEditingHolder);
		this.roiDegreesFlag = this.foVTilesFlag = false;
	}

	public void setRoiDegrees(int roiDegrees) {
		this.roiDegrees = roiDegrees;
		this.roiDegreesFlag = true;
	}

	public void setFoVTiles(int[] foVTiles) {
		this.foVTiles = foVTiles;
		this.foVTilesFlag = true;
	}

	@Override
	public boolean isWellDefined() {
		return super.isWellDefined() && this.roiDegreesFlag && this.foVTilesFlag;
	}

  @Override
  public void activate(GVRSceneObject videoHolder, GVRContext gvrContext) {
    float currentAngle = getCurrentYAngle(gvrContext);
    float difference = currentAngle - roiDegrees;
    videoHolder.getTransform().setRotationByAxis(difference, 0, 1, 0);
    dynamicEditingHolder.advance(difference);
  }

  @Override
  public boolean hasToBeTriggeredInContext(GVRContext gvrContext) {
    //Check if a SnapChange is needed
    float currentAngle = getCurrentYAngle(gvrContext);
    float trigger = computeTrigger(dynamicEditingHolder.lastRotation, currentAngle, roiDegrees);
    return trigger > dynamicEditingHolder.angleThreshold;
  }

  public int getSCroiDegrees() {
		return this.roiDegrees;
	}

	public int[] getSCfoVTiles() {
		return this.foVTiles;
	}

  private float getCurrentYAngle(GVRContext gvrContext) {
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

  private float computeTrigger(float lastRotation, float currentUserPosition, float nextSCroiDegrees) {
    float normalizedLastRotation = Angles.normalizeAngle(lastRotation);
    float trigger = Angles.normalizeAngle(currentUserPosition-normalizedLastRotation);
    trigger = Angles.normalizeAngle(trigger-nextSCroiDegrees);
    return abs(trigger);
  }
}
