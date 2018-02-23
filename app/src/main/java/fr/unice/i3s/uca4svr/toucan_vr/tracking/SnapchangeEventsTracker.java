/*
 * Copyright 2017 Université Nice Sophia Antipolis (member of Université Côte d'Azur), CNRS
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.FileAppender;

/**
 * It tracks if the snapchange is executed or not (because the user was looking at the ROI when the snapchange occurred)
 * Each entry in the csv file will have:
 *  - the timestamp of the snapchange
 *  - the timestamp at which the snapchange is activated
 *  - the angle in degrees fixed after the snapchange
 *  - the current user's angle
 *  - boolean representing if the snapchange has been executed or not
 */
public class SnapchangeEventsTracker {

    // Each logger must have a different ID,
    // so that creating a new logger won't override the previous one
    private static int loggerNextID = 0;

    private final Logger logger;

    /**
     * Main constructor of the tracker.
     * It creates a log file named logFilePrefix_date.csv.
     * Be aware that tracking is done by calling the <code>track</code> method in the onStep function.
     *
     * @param logFilePrefix The prefix for the log file name
     */
    public SnapchangeEventsTracker(String logFilePrefix) {

        String logFilePath = Environment.getExternalStoragePublicDirectory("toucan/logs/")
                + File.separator
                + createLogFileName(logFilePrefix);

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
        logger = LoggerFactory.getLogger("fr.unice.i3s.uca4svr.tracking.SnapchangeEventsTracker"
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
        return String.format("%s_snapchange_%s.csv", logFilePrefix, dateFormat.format(date));
    }

    /**
     * Outputs a track record to the log file.
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
    public void track(long expectedTS, long currentTS, float expectedAngle, float currentAngle, boolean activated) {
            logger.error(String.format(Locale.ENGLISH, "%d,%d,%f,%f,%b",
                    expectedTS, currentTS,
                    expectedAngle, currentAngle, activated));
    }
}
