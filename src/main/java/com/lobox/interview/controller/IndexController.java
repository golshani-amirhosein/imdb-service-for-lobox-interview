package com.lobox.interview.controller;

import com.lobox.interview.service.ImdbImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class IndexController {

    private final static String INDEX_PAGE_HTML = """
            <html>
            <body>
                <p>Welcome to The Special IMDb Service!</p>
                <p><a href="/imdb/check-requirements">0. Check the Requirements</a></p>
                <p><a href="/imdb/import">1. Import (Status: %s)</a></p>
                <p><a href="/imdb/director-writer-same-and-alive">2. All the titles in which both director and writer are the same person and he/she is still alive</a></p>
                <p><a href="/imdb/actors-pair-titles?actor1=Dominic%%20Purcell&actor2=Wentworth%%20Miller">3. Give Two actors and get all the titles in which both of them played at (e.g. Dominic Purcell & Wentworth Miller)</a></p>
                <p><a href="/imdb/top-rated-by-genre-per-year?genre=Romance">4. Give a genre and get best titles on each year for that genre based on number of votes and rating (e.g. Romance)</a></p>
                <p><a href="/statistics/requests-count">5. Count how many HTTP requests you received in this application since the last startup</a></p>
            </body>
            </html>
            """;

    private final ImdbImportService imdbImportService;

    @GetMapping
    public String index() {
        return INDEX_PAGE_HTML.formatted(imdbImportService.getImportStatus());
    }
}
