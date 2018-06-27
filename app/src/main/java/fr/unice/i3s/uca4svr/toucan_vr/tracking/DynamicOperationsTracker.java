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
package fr.unice.i3s.uca4svr.toucan_vr.tracking;

import java.util.Locale;

import fr.unice.i3s.uca4svr.toucan_vr.tracking.writers.SnapchangeLogWriter;
import fr.unice.i3s.uca4svr.toucan_vr.tracking.writers.StopLogWriter;

/**
 * Tracks every kind of dynamic operations with its own method to be called.
 *
 * @author Romaric Pighetti
 * @author Julien Lemaire
 */
public class DynamicOperationsTracker {

    private SnapchangeLogWriter scLogWriter;
    private StopLogWriter stopLogWriter;

    /**
     * Main constructor of the tracker.
     * It creates a log file named logFilePrefix_date.csv.
     * Be aware that tracking is done by calling the <code>track</code> method in the onStep function.
     *
     * @param logFilePrefix The prefix for the log files name
     */
    public DynamicOperationsTracker(String logFilePrefix) {
      scLogWriter = new SnapchangeLogWriter(logFilePrefix);
      stopLogWriter = new StopLogWriter(logFilePrefix);
    }

    /**
     * Outputs a track record to the snapchanges log file.
     * Each entry in the csv file will have:
     *  - the timestamp of the snapchange
     *  - the timestamp at which the snapchange is activated
     *  - the angle in degrees fixed after the snapchange
     *  - the current user's angle
     *  - boolean representing if the snapchange has been executed or not
     *
     * @param expectedTS  The timestamp of the snapchange in the XML file
     * @param currentTS The timestamp at which the snapchange is activated
     * @param expectedAngle The angle in degrees fixed after the snapchange
     * @param currentAngle The current user's angle
     * @param activated If the snapchange has been executed or not
     *
     */
    public void trackSnapchange(long expectedTS, long currentTS, float expectedAngle, float currentAngle, boolean activated) {
      scLogWriter.writeLine(String.format(Locale.ENGLISH, "%d,%d,%f,%f,%b",
        expectedTS, currentTS,
        expectedAngle, currentAngle, activated));
    }

  /**
   * Outputs a track record to the stops log file.
   * Each entry in the csv will have:
   *  - the timestamp of the stop
   *  - the timestamp at which the stop is activated
   *  - the expected duration of the stop in ms
   *  - the real duration of the stop in ms
   *
   * @param expectedTS the timestamp of the stop in the XML file
   * @param currentTS the timestamp at which the stop is activated
   * @param expectedDuration the duration of the stop in the XML file
   * @param actualDuration the real duration of the stop
   */
    public void trackStop(long expectedTS, long currentTS, long expectedDuration, long actualDuration) {
      stopLogWriter.writeLine(String.format(Locale.ENGLISH, "%d,%d,%d,%d",
        expectedTS, currentTS, expectedDuration, actualDuration));
    }
}
