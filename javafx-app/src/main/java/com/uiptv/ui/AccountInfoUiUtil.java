package com.uiptv.ui;
import com.uiptv.ui.util.*;
import com.uiptv.ui.util.*;

import javafx.scene.layout.Region;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;

import static com.uiptv.util.StringUtils.isBlank;
import static com.uiptv.util.StringUtils.isNotBlank;

public final class AccountInfoUiUtil {
    private AccountInfoUiUtil() {
    }

    private static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern(DATE_TIME_PATTERN);

    public enum ExpiryState {
        OK,
        WARNING,
        EXPIRED,
        UNKNOWN
    }

    public enum StatusState {
        ACTIVE,
        SUSPENDED,
        BLOCKED,
        UNKNOWN
    }

    public record ParsedDate(String display, Instant instant) {
    }

    public static ParsedDate parseDateValue(String value) {
        if (isBlank(value)) {
            return new ParsedDate("", null);
        }
        String trimmed = value.trim();
        String numeric = trimmed.replaceAll("\\D", "");
        if (numeric.length() == 10 || numeric.length() == 13) {
            try {
                long epoch = Long.parseLong(numeric);
                if (numeric.length() == 13) {
                    epoch = epoch / 1000;
                }
                Instant instant = Instant.ofEpochSecond(epoch);
                LocalDateTime dt = LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
                return new ParsedDate(DATE_TIME_FORMATTER.format(dt), instant);
            } catch (Exception _) {
                // Fall back to raw value.
            }
        }

        List<DateTimeFormatter> dateTimeFormats = List.of(
                DATE_TIME_FORMATTER,
                DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS"),
                DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm:ss"),
                DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")
        );
        for (DateTimeFormatter formatter : dateTimeFormats) {
            try {
                LocalDateTime dt = LocalDateTime.parse(trimmed, formatter);
                Instant instant = dt.atZone(ZoneId.systemDefault()).toInstant();
                return new ParsedDate(DATE_TIME_FORMATTER.format(dt), instant);
            } catch (DateTimeParseException _) {
                // Continue.
            }
        }

        List<DateTimeFormatter> dateFormats = List.of(
                DateTimeFormatter.ofPattern("yyyy-MM-dd"),
                DateTimeFormatter.ofPattern("yyyy/MM/dd"),
                DateTimeFormatter.ofPattern("MM/dd/yyyy"),
                DateTimeFormatter.ofPattern("dd/MM/yyyy")
        );
        for (DateTimeFormatter formatter : dateFormats) {
            try {
                LocalDate date = LocalDate.parse(trimmed, formatter);
                Instant instant = date.atStartOfDay(ZoneId.systemDefault()).toInstant();
                return new ParsedDate(DateTimeFormatter.ofPattern("yyyy-MM-dd").format(date), instant);
            } catch (DateTimeParseException _) {
                // Continue.
            }
        }

        return new ParsedDate(trimmed, null);
    }

    public static String formatDate(String value) {
        return parseDateValue(value).display();
    }

    public static ExpiryState resolveExpiryState(Instant instant) {
        if (instant == null) {
            return ExpiryState.UNKNOWN;
        }
        long daysRemaining = java.time.Duration.between(Instant.now(), instant).toDays();
        if (daysRemaining < 0) {
            return ExpiryState.EXPIRED;
        }
        if (daysRemaining <= 7) {
            return ExpiryState.WARNING;
        }
        return ExpiryState.OK;
    }

    public static ExpiryState resolveExpiryState(String value) {
        ParsedDate parsed = parseDateValue(value);
        return resolveExpiryState(parsed.instant());
    }

    public static StatusState resolveStatusState(String statusText) {
        if (isBlank(statusText)) {
            return StatusState.UNKNOWN;
        }
        String normalized = statusText.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "active" -> StatusState.ACTIVE;
            case "suspended" -> StatusState.SUSPENDED;
            case "blocked" -> StatusState.BLOCKED;
            default -> StatusState.UNKNOWN;
        };
    }

    public static String colorForExpiry(ExpiryState state) {
        return switch (state) {
            case OK -> "#2e7d32";
            case WARNING -> "#f9a825";
            case EXPIRED -> "#c62828";
            case UNKNOWN -> "#9e9e9e";
        };
    }

    public static String colorForStatus(StatusState state) {
        return switch (state) {
            case ACTIVE -> "#2e7d32";
            case SUSPENDED -> "#f9a825";
            case BLOCKED -> "#c62828";
            case UNKNOWN -> "#9e9e9e";
        };
    }

    public static void applyIndicator(Region region, String color, boolean visible) {
        if (region == null) {
            return;
        }
        region.setStyle("-fx-background-color: " + color + "; -fx-background-radius: 6px;");
        region.setVisible(visible);
        region.setManaged(visible);
    }

    public static boolean hasValue(String value) {
        return isNotBlank(value);
    }
}
