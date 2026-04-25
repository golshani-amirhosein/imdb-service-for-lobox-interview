package com.lobox.interview.service;

import com.google.common.io.CountingInputStream;
import lombok.Getter;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.zip.GZIPInputStream;

public class CSVReader implements AutoCloseable {

    private final String columnSeparator;

    private final BufferedReader reader;
    private FileInputStream fileInputStream;
    private GZIPInputStream gzipInputStream;
    private CountingInputStream countingInputStream;
    private InputStreamReader inputStreamReader;

    @Getter
    private long readBytes = 0;

    @Getter
    private long lastLineReadBytes = 0;

    public CSVReader(Path path, String columnSeparator) throws IOException {
        this.columnSeparator = columnSeparator;
        var pathToString = path.toString();

        if (pathToString.endsWith(".tsv.gz")) {
            fileInputStream = new FileInputStream(path.toFile());
            countingInputStream = new CountingInputStream(fileInputStream);
            gzipInputStream = new GZIPInputStream(countingInputStream);
            inputStreamReader = new InputStreamReader(gzipInputStream);

            reader = new BufferedReader(inputStreamReader);
        } else if (pathToString.endsWith(".tsv")) {
            reader = Files.newBufferedReader(path, StandardCharsets.UTF_8);
        } else
            throw new IOException("Unrecognized File");
    }

    public Optional<String[]> readNextRecord() throws IOException {
        String line = reader.readLine();

        if (line == null)
            return Optional.empty();

        if (countingInputStream != null) {
            lastLineReadBytes = countingInputStream.getCount() - readBytes;
            readBytes = countingInputStream.getCount();
        } else {
            lastLineReadBytes = line.length();
            readBytes += lastLineReadBytes;
        }
        // splitting the line and adding its items in String[]
        String[] lineItems = line.split(columnSeparator);
        return Optional.of(lineItems);
    }

    @Override
    public void close() throws IOException {
        if (fileInputStream != null) fileInputStream.close();
        if (gzipInputStream != null) gzipInputStream.close();
        if (inputStreamReader != null) inputStreamReader.close();
        if (countingInputStream != null) countingInputStream.close();

        reader.close();
    }
}
