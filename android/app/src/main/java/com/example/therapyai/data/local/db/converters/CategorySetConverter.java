package com.example.therapyai.data.local.db.converters;

import androidx.room.TypeConverter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class CategorySetConverter {

    private static final String SEPARATOR = ",,,";

    @TypeConverter
    public static String fromSet(Set<String> categories) {
        if (categories == null || categories.isEmpty()) {
            return null;
        }
        return categories.stream()
                .filter(s -> s != null && !s.trim().isEmpty())
                .collect(Collectors.joining(SEPARATOR));
    }

    @TypeConverter
    public static Set<String> toSet(String data) {
        if (data == null || data.trim().isEmpty()) {
            return new HashSet<>();
        }
        return new HashSet<>(Arrays.asList(data.split(SEPARATOR)));
    }
}