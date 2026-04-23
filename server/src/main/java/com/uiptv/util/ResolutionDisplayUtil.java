package com.uiptv.util;

public final class ResolutionDisplayUtil {
    private static final ResolutionProfile[] PROFILES = {
            new ResolutionProfile(1280, 720, "720p"),
            new ResolutionProfile(1920, 1080, "1080p"),
            new ResolutionProfile(2560, 1440, "2K"),
            new ResolutionProfile(3840, 2160, "4K UHD"),
            new ResolutionProfile(7680, 4320, "8K UHD")
    };

    private ResolutionDisplayUtil() {
    }

    public static ResolutionDisplay normalize(int width, int height) {
        if (width <= 0 || height <= 0) {
            return new ResolutionDisplay(width, height, "");
        }
        if (height < 701) {
            return new ResolutionDisplay(width, height, "");
        }

        ResolutionProfile matched = findClosestProfile(width, height);
        if (matched == null) {
            return new ResolutionDisplay(width, height, "");
        }
        return new ResolutionDisplay(matched.width(), matched.height(), matched.label());
    }

    private static ResolutionProfile findClosestProfile(int width, int height) {
        ResolutionProfile bestMatch = null;
        int bestScore = Integer.MAX_VALUE;
        for (ResolutionProfile profile : PROFILES) {
            if (!isWithinTolerance(width, profile.width()) || !isWithinTolerance(height, profile.height())) {
                continue;
            }
            int score = Math.abs(width - profile.width()) + Math.abs(height - profile.height());
            if (score < bestScore) {
                bestScore = score;
                bestMatch = profile;
            }
        }
        return bestMatch;
    }

    private static boolean isWithinTolerance(int actual, int expected) {
        int tolerance = Math.max(24, (int) Math.round(expected * 0.03));
        return Math.abs(actual - expected) <= tolerance;
    }

    public record ResolutionDisplay(int width, int height, String label) {
        public String dimensionsText() {
            return width + "x" + height;
        }

        public String shortText() {
            return label == null || label.isBlank() ? dimensionsText() : label;
        }
    }

    private record ResolutionProfile(int width, int height, String label) {
    }
}
