package com.lobox.interview.repository;

import com.lobox.interview.exception.BadRequestException;
import com.lobox.interview.repository.dto.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class IMDbRepository {

    @Value("${app.max-titles-per-genre-and-year}")
    private int TOP_N_TITLES_PER_GENRE_AND_YEAR;

    // Director and writer same titles
    private final HashMap<Integer, ArrayList<Integer>> directorWriterSameTitleIds = new HashMap<>();
    private final Set<String> directorWriterSameTitles = new HashSet<>();

    private final HashMap<String, List<Person>> personsByName = new HashMap<>();

    // This variable used to remove titles with no actor or only one actor
    private Integer lastTitleId;

    private final HashMap<Integer, ArrayList<Integer>> titlesActors = new HashMap<>();

    private final HashMap<Integer, String> titles = new HashMap<>();

    private final HashMap<String, PriorityQueue<TitleRate>> topTitles = new HashMap<>();
    private final HashMap<String, List<TopTitlesPerYear>> topTitlesPerYear = new HashMap<>();
    private final HashMap<Integer, TitleRateAndWeight> titlesWeightedRate = new HashMap<>();

    @Getter
    @Setter
    private boolean isReady;

    public boolean hasDirectorAndWriterTitleIds(int titleId) {
        return directorWriterSameTitleIds.containsKey(titleId);
    }

    public void insertDirectorWriterSameAndAliveTitle(String title) {
        directorWriterSameTitles.add(title);
    }

    public void removeDirectorWriterSameByPersonId(Integer personId) {
        directorWriterSameTitleIds.remove(personId);
    }

    public void insertDirectorWriterSameTitleId(int titleId, int personId) {
        var titlesArray = directorWriterSameTitleIds.compute(
                personId,
                (key, value) -> value == null ? new ArrayList<>() : value
        );

        titlesArray.add(titleId);
    }

    public void insertPerson(Person person) {
        personsByName.compute(
                person.fullName(),
                (key, value) -> value == null ? new ArrayList<>() : value
        ).add(person);
    }

    public List<ActorsSharedTitles> findActorsPairTitles(String actor1FullName, String actor2FullName) {
        ensureReady();

        if (actor1FullName.equals(actor2FullName))
            throw new BadRequestException("Actors names could not be equal");

        var result = new ArrayList<ActorsSharedTitles>();

        var actor1Matchings = personsByName.get(actor1FullName);
        if (actor1Matchings == null)
            throw new BadRequestException("We haven't found any actor for some of given name(s): %s".formatted(actor1FullName));

        var actor2Matchings = personsByName.get(actor2FullName);
        if (actor2Matchings == null)
            throw new BadRequestException("We haven't found any actor for some of given name(s): %s".formatted(actor2FullName));

        for (var matching1 : actor1Matchings) {
            for (var matching2 : actor2Matchings) {
                var sharedTitles = getSharedTitles(List.of(matching1.id(), matching2.id()));

                result.add(new ActorsSharedTitles(
                        matching1.toString(),
                        matching2.toString(),
                        sharedTitles
                ));
            }
        }

        return result;
    }

    public void cleanupAll() {
        cleanupJustTemporaryObjects();
        directorWriterSameTitles.clear();
        personsByName.clear();
        titlesActors.clear();
        topTitlesPerYear.clear();
        titles.clear();
        System.gc();
    }

    public void cleanupJustTemporaryObjects() {
        titlesWeightedRate.clear();
        directorWriterSameTitleIds.clear();
        topTitles.clear();
        System.gc();
    }

    private void ensureReady() {
        if (!isReady)
            throw new BadRequestException("The dataset is not imported yet!");
    }

    private ArrayList<String> getSharedTitles(List<Integer> actorIds) {
        var result = new ArrayList<String>();
        for (var entry : titlesActors.entrySet()) {
            if (entry.getValue().containsAll(actorIds)) {
                var titleId = entry.getKey();
                var title = titles.get(titleId);
                result.add(title);
            }
        }
        return result;
    }


    public List<String> getDirectorWriterSameAndAliveTitles() {
        ensureReady();

        return directorWriterSameTitles.stream().toList();
    }

    public List<TopTitlesPerYear> getTopRatedTitlesByGenrePerYear(String genre) {
        ensureReady();

        return topTitlesPerYear.get(genre.toUpperCase());
    }

    public void insertTitleIfExistsInTitlesActors(int titleId, String title) {
        if (titlesActors.containsKey(titleId)) {
            titles.put(titleId, title);
        }
    }

    public void insertTitleActor(Integer titleId, Integer actorId) {
        var actorIds = titlesActors.get(titleId);
        if (actorIds == null) {
            // remove titles with no actor or only one actor
            if (lastTitleId != null && titlesActors.get(lastTitleId).size() <= 1) {
                titlesActors.remove(lastTitleId);
            }

            actorIds = new ArrayList<>();
            titlesActors.put(titleId, actorIds);
            lastTitleId = titleId;
        }

        actorIds.add(actorId);
    }

    public void insertTitlesWeightedRate(Integer titleId, double weightedRate, float averageRating, int numberOfVotes) {
        titlesWeightedRate.put(titleId, new TitleRateAndWeight(weightedRate, averageRating, numberOfVotes));
    }

    public TitleRateAndWeight getTitlesWeightedRate(Integer titleId) {
        return titlesWeightedRate.get(titleId);
    }

    private String getTitleAndRate(TitleRate titleRate) {
        var titleAndWeight = titlesWeightedRate.get(titleRate.titleId());

        return "%s - %s (%,d)".formatted(
                titleRate.primaryTitle(),
                titleAndWeight.averageRating(),
                titleAndWeight.numberOfVotes()
        );
    }

    public void computeTopRatedTitlesByGenrePerYear() {
        for (var entry : topTitles.entrySet()) {
            var splitByUnderline = entry.getKey().split("_");
            var genre = splitByUnderline[0];
            var year = Integer.parseInt(splitByUnderline[1]);
            var titlesOfYears = topTitlesPerYear.compute(
                    genre,
                    (key, value) -> value == null ? new ArrayList<>() : value
            );

            var sortedTitles = entry.getValue().stream()
                    .sorted(Comparator.comparingDouble(r -> -r.weight()))
                    .map(this::getTitleAndRate)
                    .toList();

            titlesOfYears.add(new TopTitlesPerYear(year, sortedTitles));
        }

        // sort years by DESC
        var keys = topTitlesPerYear.keySet().toArray(String[]::new);

        for (String key : keys) {
            var sortedByYearDescending = topTitlesPerYear.get(key).stream()
                    .sorted(Comparator.comparingInt(r -> -r.year()))
                    .toList();

            topTitlesPerYear.put(key.toUpperCase(), sortedByYearDescending);
        }

    }

    public void insertTopRatedTitle(String genre, int year, Double weightedRate, int titleId, String primaryTitle) {
        var genreYear = "%s_%s".formatted(genre, year);
        var queue = topTitles.get(genreYear);
        if (queue == null) {
            queue = new PriorityQueue<>(TOP_N_TITLES_PER_GENRE_AND_YEAR, Comparator.comparingDouble(TitleRate::weight));
            topTitles.put(genreYear, queue);
        }

        if (queue.size() >= TOP_N_TITLES_PER_GENRE_AND_YEAR) {
            queue.poll();
        }

        queue.add(new TitleRate(weightedRate, titleId, primaryTitle));
    }
}
