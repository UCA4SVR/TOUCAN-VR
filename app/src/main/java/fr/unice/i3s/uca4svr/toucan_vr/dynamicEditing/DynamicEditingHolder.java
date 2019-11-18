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

import org.gearvrf.GVRContext;
import org.gearvrf.GVRSceneObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import fr.unice.i3s.uca4svr.toucan_vr.dynamicEditing.operations.DynamicOperation;
import fr.unice.i3s.uca4svr.toucan_vr.dynamicEditing.operations.SlowDown;
import fr.unice.i3s.uca4svr.toucan_vr.dynamicEditing.operations.SnapChange;
import fr.unice.i3s.uca4svr.toucan_vr.dynamicEditing.operations.Stop;

public class DynamicEditingHolder {

	static public Queue<DynamicOperation> operations;
	private boolean isDynamicEdited;
	public final long timeThreshold;
	public final double angleThreshold;
	public float lastRotation;
  //-- to http send quality in FoV
  static public List<Integer> chunkIndexes_picked = new ArrayList<Integer>();
  static public List<Integer> chunkIndexes_quals = new ArrayList<Integer>();
  static public List<Boolean[]> pickedTiles = new ArrayList<Boolean[]>();
  static public List<Integer[]> qualityTiles = new ArrayList<Integer[]>();
  private Integer[] vec_quals;
  private int chunk_ind_prev = 0;
  //-------

  public DynamicEditingHolder(boolean isDynamicEdited, int numberOfTiles) {
		this.isDynamicEdited = isDynamicEdited;
		this.timeThreshold = 100;
		this.angleThreshold = 30; //0; //30; Todo: REMOVED for check 16/10/2019
		if(this.isDynamicEdited) {
			this.operations = new LinkedList<>();
		}
    this.vec_quals = new Integer[numberOfTiles];
		for (int i=0; i<vec_quals.length; i=i+1){
		  vec_quals[i] = -1;
    }
	}

	public DynamicEditingHolder(boolean isDynamicEdited, double angleThreshold, long timeThreshold, int numberOfTiles) {
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

  //-------To http send qual-----
	public void add_chunkIndexes_picked(int index) {
    this.chunkIndexes_picked.add(index);
  }
  public void add_pickedTiles(Boolean[] picked_tiles) {
    this.pickedTiles.add(picked_tiles);
  }
  /*public void add_chunkIndexes_quals(int chunk_ind) {
    this.chunkIndexes_quals.add(chunk_ind);
  }*/

  public void add_qualityTiles(int chunk_ind, int tile_ind, int qual) { //synchronized

    if (chunk_ind > chunk_ind_prev) {
      for (int i=0; i<vec_quals.length; i=i+1){
        vec_quals[i] = -1;
      }
      chunk_ind_prev = chunk_ind;
    }

    vec_quals[tile_ind] = qual;

    Boolean yet_to_fill = false;
    for (int i=0; i<vec_quals.length; i=i+1){
      if (vec_quals[i] == -1) {
        yet_to_fill = true;
        break;
      }
    }

    if (!yet_to_fill){
//      System.out.print("chunk index: "+chunk_ind+ ", vec_quals: " + vec_quals[0] + vec_quals[1] + vec_quals[2] + vec_quals[3] + vec_quals[4] + vec_quals[5] + vec_quals[6] + vec_quals[7] + vec_quals[8] + "\n");
      this.qualityTiles.add(vec_quals.clone());
      this.chunkIndexes_quals.add(chunk_ind);
    }

  }
  public List<Integer> get_chunkIndexes_picked() {
    return this.chunkIndexes_picked;
  }
  public List<Boolean[]> get_pickedTiles() {
    return this.pickedTiles;
  }
  public List<Integer> get_chunkIndexes_quals() {
    return this.chunkIndexes_quals;
  }
  public List<Integer[]> get_qualityTiles() {
    return this.qualityTiles;
  }
  //---------------


	public boolean empty() { return operations.size()==0; }

	public boolean isDynamicEdited() {
		return isDynamicEdited;
	}

	public void changeOperations(int index, DynamicOperation newOp){
    ((LinkedList<DynamicOperation>) operations).remove(index);
    ((LinkedList<DynamicOperation>) operations).add(index, newOp);
  }

	public List<DynamicOperation> getOperations() {
		return (LinkedList<DynamicOperation>)operations;
	}

	public DynamicOperation getCurrentOperation() {
	  return operations.peek();
  }

	public void advance(float lastRotation) {
		if(this.operations.size()==1) {
      this.isDynamicEdited = false;
      this.lastRotation = lastRotation;
    } else {
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

/*  public void setChunkIndex(int index) {
    this.chunkIndex = index;
  }

  public int getChunkIndex() {
    return this.chunkIndex;
  }

  public void setQualityFoV(int qual) {
    this.qualityFoV = qual;
  }

  public float getQualityFoV() {
    return this.qualityFoV;
  }*/
}
