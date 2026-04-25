package com.lobox.interview.service;

import com.lobox.interview.repository.IMDbRepository;
import com.lobox.interview.repository.dto.Person;
import com.lobox.interview.service.dto.ReaderConfig;
import com.lobox.interview.service.enums.ImportStatus;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@Slf4j
@RequiredArgsConstructor
@Service
public class ImdbImportService {

    @Value("${dataset.path}")
    private String DATASET_PATH;

    @Value("${dataset.empty-value}")
    private String EMPTY_VALUE;

    @Value("${dataset.column-separator}")
    private String COLUMNS_SEPARATOR;

    private static final Set<String> VALID_ACTOR_CATEGORIES = Set.of("actor", "actress");
    private static final int MIN_VALID_NUMBER_OF_VOTES = 10_000;
    private static final double IMDB_AVG_OF_ALL_TITLES = 7.0;

    private Path root;

    private final ImportProgressService importProgressService;
    private final IMDbRepository imdbRepository;

    @Getter
    private ImportStatus importStatus = ImportStatus.NOT_STARTED;

    private final List<ReaderConfig> fileReaderConsumers = new ArrayList<>();

    public Collection<String> checkRequirements() {
        var result = new ArrayList<String>();

        if (Files.exists(root)) {
            result.add("OK: Dataset path '%s' is correct".formatted(root.toString()));
        } else {
            result.add("ERR: Dataset path '%s' is not found, try to change it in application.properties".formatted(root.toString()));
        }

        // check all the required file exists
        for (var item : fileReaderConsumers) {
            if (Files.exists(item.file())) {
                result.add("OK: File '%s' is exists".formatted(item.file()));
            } else {
                result.add("ERR: File '%s' was not found - download it from 'https://datasets.imdbws.com/%s.gz' [optional: extract gz file]".formatted(item.file(), item.file().getFileName()));
            }
        }

        return result;
    }

    private Void fillDirectorAndWriterAreSameTitleIds(String[] row) {
        var tConst = row[0];
        var directors = row[1];
        var writers = row[2];

        var directorsSplit = splitAndTrim(directors, ",", false);
        var writersSplit = splitAndTrim(writers, ",", false);

        for (var director : directorsSplit) {
            for (var writer : writersSplit) {
                if (director.equals(writer)) {
                    imdbRepository.insertDirectorWriterSameTitleId(parseId(tConst), parseId(director));
                }
            }
        }
        return null;
    }

    @PostConstruct
    private void configure() {

        root = Paths.get(DATASET_PATH).toAbsolutePath().normalize();

        var titlePrincipalsFile = root.resolve("title.principals.tsv.gz");
        var titleRatingFile = root.resolve("title.ratings.tsv.gz");
        var titleCrewFile = root.resolve("title.crew.tsv.gz");
        var nameBasicsFile = root.resolve("name.basics.tsv.gz");
        var titleBasicsFile = root.resolve("title.basics.tsv.gz");

        fileReaderConsumers.add(new ReaderConfig(titlePrincipalsFile, List.of(
                this::fillActorsAndTitles
        )));

        fileReaderConsumers.add(new ReaderConfig(titleRatingFile, List.of(
                this::fillTitleRates
        )));

        fileReaderConsumers.add(new ReaderConfig(titleCrewFile, List.of(
                this::fillDirectorAndWriterAreSameTitleIds
        )));

        fileReaderConsumers.add(new ReaderConfig(nameBasicsFile, List.of(
                this::removeNotAliveWriterDirectors,
                this::fillPersons
        )));

        fileReaderConsumers.add(new ReaderConfig(titleBasicsFile, List.of(
                this::fillDirectorAndWriterAreSameTitles,
                this::fillTitlesWithMoreThanOneActor,
                this::fillTopByGenreAndYear
        )));
    }

    private long getTotalDatasetBytes() throws IOException {
        long sum = 0;

        for (var readerConfig : fileReaderConsumers)
            sum += Files.size(readerConfig.file());

        return sum;
    }

    public synchronized void startImportInBackground() {
        if (importStatus != ImportStatus.NOT_STARTED)
            return;

        var taskThread = new Thread(this::importAll);
        taskThread.start();
    }

    private void importAll() {
        importStatus = ImportStatus.PROCESSING;

        if (!prepareImport()) return;

        for (int fileIndex = 0; fileIndex < fileReaderConsumers.size(); fileIndex++) {
            var file = fileReaderConsumers.get(fileIndex);
            var filename = file.file();
            try (var namesReader = new CSVReader(root.resolve(filename), COLUMNS_SEPARATOR)) {
                Optional<String[]> record;

                // skip header
                namesReader.readNextRecord();

                while ((record = namesReader.readNextRecord()).isPresent()) {
                    for (var consumer : file.consumers()) {
                        consumer.apply(record.get());
                    }

                    importProgressService.updateProgress(namesReader.getLastLineReadBytes(), filename.getFileName().toString(), fileIndex);
                }
            } catch (Exception e) {
                handleImportFailure(e, filename);
                return;
            }
        }

        cleanup();
    }

    private void handleImportFailure(Exception e, Path filename) {
        importStatus = ImportStatus.FAILED;
        var errorMessage = "Import process failed because an error occurred while processing file '%s'".formatted(filename);
        log.error(errorMessage, e);
        importProgressService.log(errorMessage);
        importProgressService.log("Cleaning up...");
        imdbRepository.cleanupAll();
        importProgressService.log("<<< Import progress failed, You can restart the server and try again >>>");
    }

    private boolean prepareImport() {
        long totalDatasetBytes;

        try {
            totalDatasetBytes = getTotalDatasetBytes();
        } catch (IOException e) {
            importStatus = ImportStatus.FAILED;
            var logMessage = "Import process failed because could not calculate dataset file size or dataset files not found";
            importProgressService.log(logMessage);
            log.error(logMessage, e);
            return false;
        }

        importProgressService.start(totalDatasetBytes, fileReaderConsumers.size());
        return true;
    }

    private void cleanup() {
        try {
            imdbRepository.computeTopRatedTitlesByGenrePerYear();
            importProgressService.log("Cleaning up...");
            imdbRepository.cleanupJustTemporaryObjects();
            importProgressService.complete();
            imdbRepository.setReady(true);
            importStatus = ImportStatus.COMPLETED;
            importProgressService.log("<<< Ready >>>");
        } catch (Exception e) {
            importStatus = ImportStatus.FAILED;
            var logMessage = "An error occurred while cleaning up, You can restart the server and try again";
            importProgressService.log(logMessage);
            log.error(logMessage, e);
        }
    }

    private Void fillTitlesWithMoreThanOneActor(String[] row) {
        var tConst = row[0];
        var titleId = parseId(tConst);
        var primaryTitle = row[2];

        imdbRepository.insertTitleIfExistsInTitlesActors(titleId, primaryTitle);

        return null;
    }

    private Void fillPersons(String[] row) {
        var nConst = row[0];
        var primaryName = row[1];
        var birthYear = EMPTY_VALUE.equals(row[2]) ? 0 : Short.parseShort(row[2]);
        var deathYear = EMPTY_VALUE.equals(row[3]) ? 0 : Short.parseShort(row[3]);

        imdbRepository.insertPerson(new Person(parseId(nConst), primaryName, birthYear, deathYear));
        return null;
    }

    private Integer parseId(String str) {
        return Integer.parseInt(str.substring(2));
    }

    private Void fillTitleRates(String[] row) {
        var titleId = parseId(row[0]);
        var averageRating = Float.parseFloat(row[1]);
        var numberOfVotes = Integer.parseInt(row[2]);
        //var weightedRate = averageRating * Math.pow(numberOfVotes, 2);
        var imdbWeightedRate =
                ((numberOfVotes * averageRating) + (MIN_VALID_NUMBER_OF_VOTES * IMDB_AVG_OF_ALL_TITLES)) /
                        (numberOfVotes + MIN_VALID_NUMBER_OF_VOTES);
        imdbRepository.insertTitlesWeightedRate(titleId, imdbWeightedRate, averageRating, numberOfVotes);
        return null;
    }

    private Void fillTopByGenreAndYear(String[] row) {
        int titleId = parseId(row[0]);
        var primaryTitle = row[2];
        var startYear = row[5];
        var genres = row[8];
        var rateAndWeight = imdbRepository.getTitlesWeightedRate(titleId);
        if (rateAndWeight == null) {
            return null;
        }

        for (String genre : genres.split(",")) {
            if (EMPTY_VALUE.equals(startYear))
                continue;

            imdbRepository.insertTopRatedTitle(genre, Integer.parseInt(startYear), rateAndWeight.weight(), titleId, primaryTitle);
        }

        return null;
    }

    private Void fillActorsAndTitles(String[] row) {

        var tConst = row[0];
        var nConst = row[2];
        var category = row[3];

        if (VALID_ACTOR_CATEGORIES.contains(category)) {
            var titleId = parseId(tConst);
            var actorId = parseId(nConst);
            imdbRepository.insertTitleActor(titleId, actorId);
        }

        return null;
    }

    private Void fillDirectorAndWriterAreSameTitles(String[] row) {
        var tConst = row[0];
        var id = parseId(tConst);
        var primaryTitle = row[2];
        if (imdbRepository.hasDirectorAndWriterTitleIds(id)) {
            imdbRepository.insertDirectorWriterSameAndAliveTitle(primaryTitle);
        }

        return null;
    }

    public Void removeNotAliveWriterDirectors(String[] row) {
        var nConst = row[0];
        var deathYear = row[3];
        var isAlive = EMPTY_VALUE.equals(deathYear);

        if (!isAlive) {
            var personId = parseId(nConst);
            imdbRepository.removeDirectorWriterSameByPersonId(personId);
        }

        return null;
    }

    @SuppressWarnings("SameParameterValue")
    private String[] splitAndTrim(String str, String separatorRegex, boolean toUpperCase) {
        return Arrays.stream(str.split(separatorRegex))
                .map(String::trim)
                .map(r -> toUpperCase ? r.toUpperCase() : r)
                .filter(s -> StringUtils.hasText(s) && !EMPTY_VALUE.equals(s))
                .toArray(String[]::new);
    }

}
