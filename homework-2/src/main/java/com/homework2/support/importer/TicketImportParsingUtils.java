package com.homework2.support.importer;

import com.homework2.support.domain.Category;
import com.homework2.support.domain.DeviceType;
import com.homework2.support.domain.Priority;
import com.homework2.support.domain.TicketSource;
import com.homework2.support.domain.TicketStatus;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.stream.Collectors;

final class TicketImportParsingUtils {
    private TicketImportParsingUtils() {
    }

    static boolean hasExtension(String filename, String extension) {
        if (filename == null || extension == null) {
            return false;
        }
        return filename.toLowerCase(Locale.ROOT).endsWith(extension.toLowerCase(Locale.ROOT));
    }

    static boolean contentTypeContains(String contentType, String token) {
        if (contentType == null || token == null) {
            return false;
        }
        return contentType.toLowerCase(Locale.ROOT).contains(token.toLowerCase(Locale.ROOT));
    }

    static String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    static LocalDateTime parseOptionalDateTime(String value, String fieldName) {
        String normalized = normalizeNullable(value);
        if (normalized == null) {
            return null;
        }

        try {
            return LocalDateTime.parse(normalized);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("Invalid " + fieldName + " datetime format: " + normalized);
        }
    }

    static List<String> parseTags(String rawValue) {
        String normalized = normalizeNullable(rawValue);
        if (normalized == null) {
            return List.of();
        }

        return Arrays.stream(normalized.split("[|,;]"))
                .map(TicketImportParsingUtils::normalizeNullable)
                .filter(tag -> tag != null && !tag.isBlank())
                .collect(Collectors.toList());
    }

    static Category parseCategory(String value) {
        return parseEnum(value, Category::fromValue, "category");
    }

    static Priority parsePriority(String value) {
        return parseEnum(value, Priority::fromValue, "priority");
    }

    static TicketStatus parseStatus(String value) {
        return parseEnum(value, TicketStatus::fromValue, "status");
    }

    static TicketSource parseSource(String value) {
        return parseEnum(value, TicketSource::fromValue, "metadata.source");
    }

    static DeviceType parseDeviceType(String value) {
        return parseEnum(value, DeviceType::fromValue, "metadata.device_type");
    }

    private static <T> T parseEnum(String value, Function<String, T> parser, String fieldName) {
        String normalized = normalizeNullable(value);
        if (normalized == null) {
            return null;
        }

        try {
            return parser.apply(normalized);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid " + fieldName + " value: " + normalized);
        }
    }

    static String rootCauseMessage(Throwable throwable) {
        if (throwable == null) {
            return "Unknown error";
        }
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
