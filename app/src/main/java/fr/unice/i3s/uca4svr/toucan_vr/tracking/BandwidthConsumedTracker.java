/*
 * Copyright 2017 Laboratoire I3S, CNRS, Université côte d'azur
 *
 * Author: Romaric Pighetti
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

import android.os.AsyncTask;
import android.os.Environment;
import android.util.Log;

import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.TransferListener;
import com.google.android.exoplayer2.util.Clock;
import com.google.android.exoplayer2.util.SystemClock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.FileAppender;
import fr.unice.i3s.uca4svr.toucan_vr.PlayerActivity;

/**
 * Tracks the bandwidth consumed during the video playback.
 */
public class BandwidthConsumedTracker implements TransferListener<Object> {

  // Each logger must have a different ID,
  // so that creating a new logger won't override the previous one
  private static int loggerNextID = 0;

  private final Logger logger;

  private long totalBytesConsumed = 0;
  private boolean firstTransfer = true;
  private int transferIndex = 0;

  private final Clock clock;
  private long lastRecordTime;


  /**
   * Initialize a {@link BandwidthConsumedTracker}, that will record the consumed
   * bandwidth during playback to a file name logFilePrefix_bandwidth_date.csv.
   *
   * @param logFilePrefix The prefix for the log file name
   */
  public BandwidthConsumedTracker(String logFilePrefix) {

    clock = new SystemClock();
    lastRecordTime = -1000;

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
    logger = LoggerFactory.getLogger("fr.unice.i3s.uca4svr.tracking.BandwidthConsumedTracker"
            + loggerNextID++);
    // I know the logger is from logback, this is the implementation i'm using below slf4j API.
    ((ch.qos.logback.classic.Logger) logger).addAppender(fileAppender);
  }

  /**
   * Builds the name of the logfile by appending the date to the logFilePrefix
   *
   * @param logFilePrefix the prefix for the log file
   * @return the name of the log file
   */
  private String createLogFileName(String logFilePrefix) {
    DateFormat dateFormat = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss", Locale.US);
    Date date = new Date();
    return String.format("%s_bandwidth_%s.csv", logFilePrefix, dateFormat.format(date));
  }

  @Override
  public void onTransferStart(Object source, DataSpec dataSpec) {
    transferIndex++;
    if (firstTransfer) {
      logger.error(String.format(Locale.ENGLISH, "%d, %d, %d",
              transferIndex, clock.elapsedRealtime(), totalBytesConsumed));
      firstTransfer = false;
    }
  }

  /**
   * Records every 16 milliseconds (or more) the bandwidth consumed by the application,
   * together with the system clock and the number of files transferred up to that point.
   */
  @Override
  public void onBytesTransferred(Object source, int bytesTransferred) {
    totalBytesConsumed += bytesTransferred;
    long currentTime = clock.elapsedRealtime();
    if (currentTime - lastRecordTime > 16) {
      lastRecordTime = currentTime;
      logger.error(String.format(Locale.ENGLISH, "%d, %d, %d",
              transferIndex, currentTime, totalBytesConsumed));
    }

    //Works but conflicts with the one used for tiles qualities in OurChunkSampleStream, couldnt figure out how to run multiple AsyncTasks
    class BandwidthTask extends AsyncTask<String, Integer, Void> {
      @Override
      protected Void doInBackground(String... strings) {
        String urlParameters = "bwConsumed=" + strings[0].split(",")[0]+
          "&filesTransferred=" + strings[0].split(",")[1]+
          "&currentTime="+strings[0].split(",")[2];

        String fullURI = PlayerActivity.serverIPAddress /*+ ":8080"*/ + "/bandwidth" ;

        if (fullURI.length() > 0 ) {
          HttpURLConnection urlc;
          try {
            urlc = (HttpURLConnection) (new URL(fullURI).openConnection());
          } catch (IOException e) {
            Log.e("push","Error during push on .bandwidth\n",e);
            return null;
          }
          try {
            byte[] postData = urlParameters.getBytes(StandardCharsets.UTF_8);
            urlc.setDoOutput(true);
            urlc.setInstanceFollowRedirects(false);
            urlc.setRequestMethod("POST");
            urlc.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            urlc.setRequestProperty("charset", "utf-8");
            urlc.setRequestProperty("Content-Length", Integer.toString(postData.length));
            urlc.setUseCaches(false);
            try (DataOutputStream wr = new DataOutputStream(urlc.getOutputStream())) {
              wr.write(postData);
            }

            urlc.connect();
            if (urlc.getResponseCode() != 200) return null;
          } catch (IOException e) {
            Log.e("push","Error during push on .bandwidth\n",e);
            return null;
          } finally {
            urlc.disconnect();
          }
        } else {
//            Log.e("push","Error during push on .infos\n",e);
          return null;
        }
        return null;
      }
    }
    //new BandwidthTask().execute(""+totalBytesConsumed+","+transferIndex+","+currentTime);
  }

  @Override
  public void onTransferEnd(Object source) {

  }
}
