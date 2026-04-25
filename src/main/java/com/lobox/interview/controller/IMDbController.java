package com.lobox.interview.controller;

import com.lobox.interview.repository.IMDbRepository;
import com.lobox.interview.repository.dto.ActorsSharedTitles;
import com.lobox.interview.repository.dto.TopTitlesPerYear;
import com.lobox.interview.service.ImdbImportService;
import com.lobox.interview.service.ImportProgressService;
import com.lobox.interview.service.enums.ImportStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;
import java.util.List;

@RestController
@RequestMapping("/imdb")
@RequiredArgsConstructor
public class IMDbController {

    private final static String AUTO_REFRESH_META_TAG = "<meta http-equiv='refresh' content='1'>";

    private final static String STATUS_PAGE_HTML = """
            <html>
            <head><title>Import Status</title>%s</head>
            <body>
                <p style:"font-weight: bold">Import Status: %s</p>
                %s
            </body>
            </html>
            """;

    private final ImdbImportService imdbImportService;
    private final ImportProgressService importProgressService;
    private final IMDbRepository imdbRepository;

    @GetMapping("/check-requirements")
    public Collection<String> requirements() {
        return imdbImportService.checkRequirements();
    }

    @GetMapping("/import")
    public synchronized String importAll() {
        imdbImportService.startImportInBackground();

        var logsAsHtml = String.join(" ", importProgressService.getLog().stream()
                .map("<p>%s<p>"::formatted)
                .toList());

        var isProcessing = imdbImportService.getImportStatus() == ImportStatus.PROCESSING;

        return STATUS_PAGE_HTML.formatted(
                isProcessing ? AUTO_REFRESH_META_TAG : "",
                imdbImportService.getImportStatus(),
                logsAsHtml
        );
    }

    @GetMapping("/director-writer-same-and-alive")
    public List<String> getDirectorWriterSameAndAliveTitles() {
        return imdbRepository.getDirectorWriterSameAndAliveTitles();
    }

    @GetMapping("/top-rated-by-genre-per-year")
    public List<TopTitlesPerYear> getTopRatedTitlesByGenrePerYear(@RequestParam String genre) {
        return imdbRepository.getTopRatedTitlesByGenrePerYear(genre);
    }

    @GetMapping("/actors-pair-titles")
    public List<ActorsSharedTitles> getActorsPairTitles(@RequestParam String actor1, @RequestParam String actor2) {
        return imdbRepository.findActorsPairTitles(actor1, actor2);
    }
}
