/**
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

import android.os.Environment;
import android.util.Log;

import org.gearvrf.GVRContext;
import org.gearvrf.GVRTransform;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import org.slf4j.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.FileAppender;

/**
 * Logs the pitch, yaw, roll angles from the headtransform of the main camera rig
 * from the context given at initialization. Logging is done into a file which name is
 * TAG_datatime.csv where TAG is given as a parameter of the constructor and datetime is formated
 * as yyyy_MM_dd_HH_mm_ss.
 * The files is csv formated, with 4 entries on each line:
 * frameNumber, pitch (or X) rotation, yaw (or Y) rotation, roll (or Z) rotation
 * Rotation are expressed as angles in degree.
 * The logging happens each time the <code>track</code> method is called, and the frame
 * number recorded is the one given as a parameter to the function.
 * The file is located under the External Storage Public Directory in a directory name toucan/logs.
 *
 * @author Romaric Pighetti
 */
public class HeadMotionTracker {

    // Each logger must have a different ID,
    // so that creating a new logger won't override the previous one
    private static int loggerNextID = 0;

    // The GearVR framework context from which we're logging the head motion
    private GVRContext mContext;

    private Logger mLogger;

    /**
     * Initialize a HeadMotionTracker, that will record the angles of the headtransform
     * from the main camera of the given context to a file name logFilePrefix_date.csv.
     * Be aware that tracking is done by calling the <code>track</code> method every time
     * and entry is needed.
     * @param context The GVRContext from which the mainCamera movements must be tracked.
     * @param logFilePrefix The prefix for the log file name
     */
    public HeadMotionTracker(GVRContext context, String logFilePrefix) {
        this.mContext = context;

        String logFilePath = Environment.getExternalStoragePublicDirectory("toucan/logs/")
                + File.separator
                + createLogFileName(logFilePrefix);
        Log.d("HeadMotionTracking", logFilePath);
        // logFilePath = mContext.getContext().getFileStreamPath(logFilePath).getAbsolutePath();

        // Initialize and configure a new logger in logback
        LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
        PatternLayoutEncoder encoder1 = new PatternLayoutEncoder();
        encoder1.setContext(lc);
        encoder1.setPattern("%msg%n");
        encoder1.start();

        FileAppender<ILoggingEvent> fileAppender = new FileAppender<>();
        fileAppender.setContext(lc);
        fileAppender.setFile(logFilePath);
        fileAppender.setEncoder(encoder1);
        fileAppender.start();

        // getting the instanceof the logger
        mLogger = LoggerFactory.getLogger("fr.unice.i3s.uca4svr.tracking.HeadMotionTracker"
                + loggerNextID++);
        // I know the logger is from logback, this is the implementation i'm using below slf4j API.
        ((ch.qos.logback.classic.Logger) mLogger).addAppender(fileAppender);
    }

    /**
     * Outputs a track record to the log file.
     * The frameTime argument is used as a timestamp.
     * It can be a frame number or whatever time value you see fit.
     * @param frameTime The timestamps of the record in the log file
     */
    public void track(float frameTime) {
        GVRTransform headTransform = mContext.getMainScene().getMainCameraRig().getHeadTransform();
        String rotationsString = String.format(Locale.ENGLISH, "%1f,%2$.0f,%3$.0f,%4$.0f",
                frameTime, headTransform.getRotationPitch(), headTransform.getRotationYaw(),
                headTransform.getRotationRoll());
        mLogger.error(rotationsString);
    }

    /**
     * Builds the name of the logfile by appending the date to the logFilePrefix
     * @param logFilePrefix the prefix for the log file
     * @return the name of the log file
     */
    private String createLogFileName(String logFilePrefix) {
        DateFormat dateFormat = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss");
        Date date = new Date();
        String logFilePath = String.format("%s_%s.csv", logFilePrefix, dateFormat.format(date));
        return logFilePath;
    }
}
