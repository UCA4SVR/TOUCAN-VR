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

	private static DynamicEditingHolder dynamicEditingHolder = null;
	private List<SnapChange> snapchanges;
	public int nextSCMilliseconds;
	public int nextSCroiDegrees;
	public int[] nextSCfoVTiles;
	private ArrayList<Float> doneRotations;

	private DynamicEditingHolder() {
		this.snapchanges = new ArrayList<>();
		this.doneRotations = new ArrayList<>();
	}

	public static DynamicEditingHolder getDynamicEditingHolder() {
		if (dynamicEditingHolder==null) dynamicEditingHolder = new DynamicEditingHolder();
		return dynamicEditingHolder;
	}

	public void add(SnapChange snapchange) {
		this.snapchanges.add(snapchange);
	}

    public boolean empty() { return snapchanges.size()==0; }


	public void getNextSC() {
		this.nextSCMilliseconds = snapchanges.get(0).getSCMilliseconds();
		this.nextSCroiDegrees = snapchanges.get(0).getSCroiDegrees();
		this.nextSCfoVTiles = snapchanges.get(0).getSCfoVTiles();
	}

	public boolean advance() {
		if(this.snapchanges.size()==1)
			return false;
		else {
			this.snapchanges.remove(0);
			getNextSC();
			return true;
		}
	}

	public void addRotation(float degrees) {
		this.doneRotations.add(degrees);
	}

	public float getRotationDisplacement() {
		return 0;
	}
}
