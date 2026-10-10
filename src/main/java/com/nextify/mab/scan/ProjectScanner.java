package com.nextify.mab.scan;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Walks a project directory and collects candidate {@code .java} and {@code .xml} files. */
public final class ProjectScanner {

    private static final Set<String> SKIP_DIRS = new HashSet<>(Arrays.asList(
            "target", "build", "out", "bin", "node_modules", "dist"));

    private final boolean includeTests;

    public ProjectScanner(boolean includeTests) {
        this.includeTests = includeTests;
    }

    public Result scan(final Path root) throws IOException {
        final List<Path> java = new ArrayList<>();
        final List<Path> xml = new ArrayList<>();
        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (dir.equals(root)) {
                    return FileVisitResult.CONTINUE;
                }
                String name = dir.getFileName().toString();
                if (name.startsWith(".") || SKIP_DIRS.contains(name)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                Path parent = dir.getParent();
                if (!includeTests && "test".equals(name) && parent != null && parent.getFileName() != null
                        && "src".equals(parent.getFileName().toString())) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                String name = file.getFileName().toString();
                if (name.endsWith(".java")) {
                    java.add(file);
                } else if (name.endsWith(".xml")) {
                    xml.add(file);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                return FileVisitResult.CONTINUE;
            }
        });
        Collections.sort(java);
        Collections.sort(xml);
        return new Result(java, xml);
    }

    /** Files found by a scan, sorted for deterministic output. */
    public static final class Result {
        private final List<Path> javaFiles;
        private final List<Path> xmlFiles;

        Result(List<Path> javaFiles, List<Path> xmlFiles) {
            this.javaFiles = javaFiles;
            this.xmlFiles = xmlFiles;
        }

        public List<Path> getJavaFiles() {
            return javaFiles;
        }

        public List<Path> getXmlFiles() {
            return xmlFiles;
        }
    }
}
