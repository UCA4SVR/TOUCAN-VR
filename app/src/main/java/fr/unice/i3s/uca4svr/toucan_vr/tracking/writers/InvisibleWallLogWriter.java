package fr.unice.i3s.uca4svr.toucan_vr.tracking.writers;

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
import fr.unice.i3s.uca4svr.toucan_vr.tracking.writers.LogWriter;

/**
 * Writes invisibleWall execution log into a specific logger (leading to a specific csv file).
 *
 * @author Antoine Dezarnaud
 */
public class InvisibleWallLogWriter extends LogWriter {

  // Each logger must have a different ID,
  // so that creating a new logger won't override the previous one
  private static int loggerNextID = 0;

  public InvisibleWallLogWriter(String logFilePrefix) {
    super(logFilePrefix, "fr.unice.i3s.uca4svr.tracking.writers.InvisibleWallLogWriter"
      + loggerNextID++);
  }
  /**
   * Builds the name of the logfile by appending the date to the logFilePrefix
   *
   * @param logFilePrefix the prefix for the log file
   * @return the name of the log file
   */
  @Override
  protected String createLogFileName(String logFilePrefix) {
    DateFormat dateFormat = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss", Locale.US);
    Date date = new Date();
    return String.format("%s_invisibleWall_%s.csv", logFilePrefix, dateFormat.format(date));
  }

}
