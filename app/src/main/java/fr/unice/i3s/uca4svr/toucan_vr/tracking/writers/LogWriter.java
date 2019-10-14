package fr.unice.i3s.uca4svr.toucan_vr.tracking.writers;

import android.os.Environment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.FileAppender;

public abstract class LogWriter {

  protected Logger logger;

  public LogWriter(String logFilePrefix, String loggerReference) {

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
    logger = LoggerFactory.getLogger(loggerReference);
    // I know the logger is from logback, this is the implementation i'm using below slf4j API.
    ((ch.qos.logback.classic.Logger) logger).addAppender(fileAppender);
  }

  protected abstract String createLogFileName(String logFilePrefix);

  public void writeLine(String line) {
    logger.error(line);
  }
}
