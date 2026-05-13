package com.uiptv.application;

public record CatalogVodDetailsResult(
        String name,
        String cover,
        String plot,
        String cast,
        String director,
        String genre,
        String releaseDate,
        String rating,
        String tmdb,
        String imdbUrl,
        String duration
) {
}
