/*
 * Copyright 2017 Laboratoire I3S, CNRS, Université côte d'azur
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package fr.unice.i3s.uca4svr.toucan_vr.dynamicEditing;


import java.util.ArrayList;
import java.util.List;

public class DynamicEditingHolder {

	private List<SnapChange> snapchanges;
	private boolean isDynamicEdited;
	public final long timeThreshold;
	public final double angleThreshold;
	public int nextSCMilliseconds;
    public int nextSCMicroseconds;
	public int nextSCroiDegrees;
	public int[] nextSCfoVTiles;
	public float lastRotation;

	public DynamicEditingHolder(boolean isDynamicEdited) {
		this.isDynamicEdited = isDynamicEdited;
		this.timeThreshold = 100;
		this.angleThreshold = 30;
		if(this.isDynamicEdited) {
			this.snapchanges = new ArrayList<>();
		}
	}

	public DynamicEditingHolder(boolean isDynamicEdited, double angleThreshold, long timeThreshold) {
		this.isDynamicEdited = isDynamicEdited;
		this.timeThreshold = timeThreshold;
		this.angleThreshold = angleThreshold;
		if(this.isDynamicEdited) {
			this.snapchanges = new ArrayList<>();
		}
	}


	public void add(SnapChange snapchange) {
		this.snapchanges.add(snapchange);
	}

    public boolean empty() { return snapchanges.size()==0; }

	public boolean isDynamicEdited() {
		return isDynamicEdited;
	}

	public void getNextSnapChange() {
		this.nextSCMilliseconds = snapchanges.get(0).getSCMilliseconds();
        this.nextSCMicroseconds = this.nextSCMilliseconds*1000;
		this.nextSCroiDegrees = snapchanges.get(0).getSCroiDegrees();
		this.nextSCfoVTiles = snapchanges.get(0).getSCfoVTiles();
	}

	public List<SnapChange> getSnapChanges() {
		return snapchanges;
	}

	public void advance(float lastRotation) {
		if(this.snapchanges.size()==1)
			this.isDynamicEdited = false;
		else {
			this.lastRotation = lastRotation;
			this.snapchanges.remove(0);
			getNextSnapChange();
		}
	}
}
