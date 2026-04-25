package com.lobox.interview.service;

import lombok.Getter;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ImportProgressService {

    private static final int MAX_LOG_LINES = 5;
    private static final int MB = 1024 * 1024;
    private static final int MILLI_TO_SECOND = 1000;

    private static final String IN_PROGRESS_TEMPLATE =
            "Progress: %%%.2f --- Remaining: %,d sec --- Elapsed: %,d ses --- Processing '%s' (%s/%s) --- Used Memory: %,d MB --- Total Reserved: %,d MB";

    public static final String PROGRESS_COMPLETED_TEMPLATE =
            "Progress: %%%.2f --- Elapsed: %,d ses --- Processing Completed (%s/%s) --- Used Memory: %,d MB --- Total Reserved: %,d MB";

    @Getter
    private double progressPercent;

    private long totalBytes;
    private int filesCount;
    private long totalReadBytes;
    private long lastReport;
    private long startedAt;
    private final List<String> logStack = new ArrayList<>(MAX_LOG_LINES);

    public List<String> getLog() {
        return logStack.stream().toList().reversed();
    }

    public void start(long totalBytes, int filesCount) {
        totalReadBytes = 0;
        progressPercent = 0;
        this.totalBytes = totalBytes;
        this.filesCount = filesCount;
        this.startedAt = this.lastReport = System.currentTimeMillis();
    }

    public void updateProgress(long lastReadBytes, String currentFileName, int currentFileIndex) {

        totalReadBytes += lastReadBytes;
        progressPercent = (double) totalReadBytes / totalBytes * 100;

        var isElapsedOneSecondFromLastReport = System.currentTimeMillis() > lastReport + 1000;

        if (!isElapsedOneSecondFromLastReport)
            return;

        Runtime runtime = Runtime.getRuntime();

        lastReport = System.currentTimeMillis();
        var elapsedMilliseconds = System.currentTimeMillis() - startedAt;
        var remainedMilliseconds = (int) (elapsedMilliseconds * 100 / progressPercent) - elapsedMilliseconds;
        var logMessage = IN_PROGRESS_TEMPLATE.formatted(
                progressPercent,
                remainedMilliseconds / MILLI_TO_SECOND,
                elapsedMilliseconds / MILLI_TO_SECOND,
                currentFileName,
                currentFileIndex + 1,
                filesCount,
                (runtime.totalMemory() - runtime.freeMemory()) / MB,
                runtime.totalMemory() / MB
        );

        log(logMessage);
    }

    public void complete() {
        Runtime runtime = Runtime.getRuntime();
        var elapsedMilliseconds = System.currentTimeMillis() - startedAt;
        progressPercent = 100;

        var logMessage = PROGRESS_COMPLETED_TEMPLATE.formatted(
                progressPercent,
                elapsedMilliseconds / MILLI_TO_SECOND,
                filesCount,
                filesCount,
                (runtime.totalMemory() - runtime.freeMemory()) / MB,
                runtime.totalMemory() / MB
        );

        log(logMessage);
    }

    public void log(String message) {
        if (logStack.size() == MAX_LOG_LINES)
            logStack.removeFirst();

        logStack.add(message);
        System.out.println(message);
    }
}
