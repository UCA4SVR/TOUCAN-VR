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

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import fr.unice.i3s.uca4svr.toucan_vr.dynamicEditing.operations.DynamicOperation;
import fr.unice.i3s.uca4svr.toucan_vr.dynamicEditing.operations.SnapChange;
import fr.unice.i3s.uca4svr.toucan_vr.dynamicEditing.operations.Stop;

public class DynamicEditingHolder {

	private Queue<DynamicOperation> operations;
	private boolean isDynamicEdited;
	public final long timeThreshold;
	public final double angleThreshold;
	public float lastRotation;

	public DynamicEditingHolder(boolean isDynamicEdited) {
		this.isDynamicEdited = isDynamicEdited;
		this.timeThreshold = 100;
		this.angleThreshold = 30;
		if(this.isDynamicEdited) {
			this.operations = new LinkedList<>();
		}
	}

	public DynamicEditingHolder(boolean isDynamicEdited, double angleThreshold, long timeThreshold) {
		this.isDynamicEdited = isDynamicEdited;
		this.timeThreshold = timeThreshold;
		this.angleThreshold = angleThreshold;
		if(this.isDynamicEdited) {
			this.operations = new LinkedList<>();
		}
	}


	public void add(DynamicOperation dynamicOperation) {
		this.operations.add(dynamicOperation);
	}

	public boolean empty() { return operations.size()==0; }

	public boolean isDynamicEdited() {
		return isDynamicEdited;
	}

	public List<DynamicOperation> getOperations() {
		return (LinkedList<DynamicOperation>)operations;
	}

	public DynamicOperation getCurrentOperation() {
	  return operations.peek();
  }

	public void advance(float lastRotation) {
		if(this.operations.size()==1)
			this.isDynamicEdited = false;
		else {
			this.lastRotation = lastRotation;
			this.operations.poll();
		}
	}

	public void advance() {
    if(this.operations.size()==1)
			this.isDynamicEdited = false;
		else {
			this.operations.poll();
		}
  }
}
