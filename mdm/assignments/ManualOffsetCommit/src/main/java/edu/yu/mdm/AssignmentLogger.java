package edu.yu.mdm;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class AssignmentLogger {
    private static final String LOG_FILE = "ManualOffsetCommit.txt";
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS").withZone(ZoneId.systemDefault());

    private static AssignmentLogger instance;

    private final BufferedWriter writer;

    private AssignmentLogger() throws IOException {
        this.writer = new BufferedWriter(new FileWriter(LOG_FILE, false));
    }

    public static synchronized AssignmentLogger getInstance() {
        if (instance == null) {
            try {
                instance = new AssignmentLogger();
            } catch (IOException e) {
                throw new RuntimeException("Failed to create logger", e);
            }
        }
        return instance;
    }

    public synchronized void log(String message) {
        try {
            long nanoTime = System.nanoTime();
            long microTime = nanoTime / 1000;
            Instant now = Instant.now();
            String timestamp = TIME_FORMATTER.format(now);
            String logLine = timestamp + " " + message;

            this.writer.write(logLine);
            this.writer.newLine();
            this.writer.flush();
        } catch (Exception e) {
            System.out.println("Error: Failed to write to log file: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public synchronized void close() {
        try {
            if (writer != null) {
                this.writer.flush();
                this.writer.close();
            }
        } catch (IOException e) {
            System.err.println("ERROR: Failed to close log file: " + e.getMessage());
        }
    }

    public synchronized void flush() {
        try {
            if (writer != null) {
                writer.flush();
            }
        } catch (IOException e) {
            System.err.println("ERROR: Failed to flush log file: " + e.getMessage());
        }
    }
}
