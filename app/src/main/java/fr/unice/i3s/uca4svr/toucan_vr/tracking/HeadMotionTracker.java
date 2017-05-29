/*
 * Copyright 2017 Laboratoire I3S, CNRS, Université côte d'azur
 *
 *  Author: Romaric Pighetti
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

import com.google.android.exoplayer2.util.Clock;
import com.google.android.exoplayer2.util.SystemClock;

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
 * Logs the pitch, yaw, roll angles from the HeadTransform of the main camera rig
 * from the context given at initialization. Logging is done into a file which name is
 * TAG_datetime.csv where TAG is given as a parameter of the constructor and datetime is formatted
 * as yyyy_MM_dd_HH_mm_ss.
 * The files is csv formatted, with 5 entries on each line:
 * the system clock, the playback position, the pitch (or X), yaw (or Y) and roll (or Z) rotation
 * Rotations are expressed as angles in degrees.
 * The logging happens each time the <code>track</code> method is called.
 * The file is located under the External Storage Public Directory in a directory name toucan/logs.
 *
 * @author Romaric Pighetti
 */
public class HeadMotionTracker {

    // Each logger must have a different ID,
    // so that creating a new logger won't override the previous one
    private static int loggerNextID = 0;

    private final Logger logger;

    private final Clock clock;

    /**
     * Initialize a HeadMotionTracker, that will record the angles of the HeadTransform
     * from the main camera of the given context to a file name logFilePrefix_date.csv.
     * Be aware that tracking is done by calling the <code>track</code> method every time
     * and entry is needed.
     *
     * @param logFilePrefix The prefix for the log file name
     */
    public HeadMotionTracker(String logFilePrefix) {

        clock = new SystemClock();

        String logFilePath = Environment.getExternalStoragePublicDirectory("toucan/logs/")
                + File.separator
                + createLogFileName(logFilePrefix);
        Log.d("HeadMotionTracking", logFilePath);
        // logFilePath = context.getContext().getFileStreamPath(logFilePath).getAbsolutePath();

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
        logger = LoggerFactory.getLogger("fr.unice.i3s.uca4svr.tracking.HeadMotionTracker"
                + loggerNextID++);
        // I know the logger is from logback, this is the implementation i'm using below slf4j API.
        ((ch.qos.logback.classic.Logger) logger).addAppender(fileAppender);
    }

    /**
     * Builds the name of the logfile by appending the date to the logFilePrefix
     * @param logFilePrefix the prefix for the log file
     * @return the name of the log file
     */
    private String createLogFileName(String logFilePrefix) {
        DateFormat dateFormat = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss", Locale.US);
        Date date = new Date();
        return String.format("%s_headMotion_%s.csv", logFilePrefix, dateFormat.format(date));
    }

    /**
     * Outputs a track record to the log file.
     * The same clock reference is used as for every tracker. Also the playback position is recorded.
     *
     * @param context The GearVR framework context from which we're logging the head motion
     * @param playbackPosition The current position in the video playback
     */
    public void track(GVRContext context, long playbackPosition) {
        GVRTransform headTransform = context.getMainScene().getMainCameraRig().getHeadTransform();
        String rotationsString = String.format(Locale.ENGLISH, "%1d,%2d,%3$.0f,%4$.0f,%5$.0f",
                clock.elapsedRealtime(), playbackPosition, headTransform.getRotationPitch(),
                headTransform.getRotationYaw(), headTransform.getRotationRoll());
        logger.error(rotationsString);
    }
}
