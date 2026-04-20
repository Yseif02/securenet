package com.securenet.common;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

/**
 * Single-line log formatter for all SecureNet services.
 *
 * <p>Output format:
 * <pre>
 * 2026-04-19 00:45:13.123  INFO  [APIGateway] ROUTING: userId=abc...
 * 2026-04-19 00:45:13.456  WARN  [APIGateway] SERVICE INCIDENT: ...
 * </pre>
 *
 * <p>Registered in each service's per-run logging.properties file written
 * by start-platform.sh.
 */
public class LogFormatter extends Formatter {

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
                             .withZone(ZoneId.systemDefault());

    @Override
    public String format(LogRecord record) {
        String timestamp = DATE_FMT.format(Instant.ofEpochMilli(record.getMillis()));
        String level = record.getLevel().getName();
        String message = formatMessage(record);

        StringBuilder sb = new StringBuilder(128);
        sb.append(timestamp)
          .append("  ")
          .append(String.format("%-7s", level))
          .append("  ")
          .append(message)
          .append(System.lineSeparator());

        // Append stack trace if a throwable was logged
        if (record.getThrown() != null) {
            Throwable t = record.getThrown();
            sb.append("  EXCEPTION: ").append(t).append(System.lineSeparator());
            for (StackTraceElement el : t.getStackTrace()) {
                sb.append("    at ").append(el).append(System.lineSeparator());
            }
        }

        return sb.toString();
    }
}
