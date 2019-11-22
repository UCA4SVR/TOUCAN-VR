package fr.unice.i3s.uca4svr.toucan_vr.tracking.writers;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class SlowDownLogWriter extends LogWriter {
  // Each logger must have a different ID,
  // so that creating a new logger won't override the previous one
  private static int loggerNextID = 0;

  public SlowDownLogWriter(String logFilePrefix) {
    super(logFilePrefix, "fr.unice.i3s.uca4svr.tracking.writers.SlowDownLogWriter"
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
    return String.format("%s_slowdown_%s.csv", logFilePrefix, dateFormat.format(date));
  }
}
