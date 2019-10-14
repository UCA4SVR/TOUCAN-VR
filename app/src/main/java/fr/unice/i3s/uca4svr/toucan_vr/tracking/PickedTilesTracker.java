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

import android.os.Environment;

import com.google.android.exoplayer2.util.Clock;
import com.google.android.exoplayer2.util.SystemClock;

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
 * The class is used to log the tiles that are picked
 * The file is composed by (1+number of tiles) columns:
 *      1- the system time
 *      2- 1 if the tile number 1 is picked, 0 otherwise
 *      3- 1 if the tile number 2 is picked, 0 otherwise
 *      .
 *      .
 *      .
 *      (# of tiles)- 1 if the tile number (# of tiles) is picked, 0 otherwise
 * The file is located under the External Storage Public Directory in a directory name toucan/logs.
 */
public class PickedTilesTracker {

    // Each logger must have a different ID,
    // so that creating a new logger won't override the previous one
    private static int loggerNextID = 0;

    public final Logger logger;

    private final Clock clock;

    /**
     * Initialize a Picked TilesTracker, that will record picked tiles to a file name logFilePrefix_date.csv.
     *
     * @param logFilePrefix The prefix for the log file name
     */
    public PickedTilesTracker(String logFilePrefix) {

        clock = new SystemClock();

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
        logger = LoggerFactory.getLogger("fr.unice.i3s.uca4svr.tracking.PickedTilesTracker"
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
        return String.format("%s_pickedTiles_%s.csv", logFilePrefix, dateFormat.format(date));
    }

    /**
     * Outputs a track record to the log file.
     * The same clock reference is used as for every tracker.
     */
//    public void track(String pickedTiles) {
//        logger.error(String.format(Locale.ENGLISH, "%d,%s", clock.elapsedRealtime(), pickedTiles));
//    }
}
