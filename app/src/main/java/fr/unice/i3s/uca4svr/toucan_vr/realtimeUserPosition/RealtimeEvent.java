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

package fr.unice.i3s.uca4svr.toucan_vr.realtimeUserPosition;

public class RealtimeEvent {
  public long timestamp;
  public boolean playing;
  public long videoTime;
  public float headW;
  public float headX;
  public float headY;
  public float headZ;
  public float x;
  public float y;
  public float z;
  public int isPlaying;
  public float lastDynamicOpTriggered;
  public float snapAngle;
  public boolean dynamic;
  public boolean start = false;
  public int nb_snaps_triggered;
  public float last_possible_snap_time;
  public float proba_trigger;
  public float qualityFoV;
  public int chunkIndex;

}
