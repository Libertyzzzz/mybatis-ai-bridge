package com.nextify.mab.util;

import java.nio.file.Path;

/** Path helpers. */
public final class Paths2 {

    private Paths2() {
    }

    /** Path of {@code file} relative to {@code root}, always with forward slashes. */
    public static String relative(Path root, Path file) {
        Path rel;
        try {
            rel = root.relativize(file);
        } catch (IllegalArgumentException e) {
            rel = file;
        }
        return rel.toString().replace('\\', '/');
    }
}
