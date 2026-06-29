package common;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Logger {

  private static final DateTimeFormatter dtf =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

  public static void log(String tag, String message) {
    String time = dtf.format(LocalDateTime.now());
    String thread = Thread.currentThread().getName();
    System.out.println(String.format("[%s] [%s] [%s] %s", time, thread, tag, message));
  }

  public static void error(String tag, String message) {
    String time = dtf.format(LocalDateTime.now());
    String thread = Thread.currentThread().getName();
    System.err.println(String.format("[%s] [%s] [ERROR] [%s] %s", time, thread, tag, message));
  }
}
