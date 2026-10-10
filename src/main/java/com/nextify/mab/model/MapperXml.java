package com.nextify.mab.model;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** A parsed mapper XML file. */
public final class MapperXml {

    private final Path file;
    private final String relativePath;
    private final String namespace;
    private final Map<String, SqlStatement> statements;
    private final List<ResultMapInfo> resultMaps;

    public MapperXml(Path file, String relativePath, String namespace,
                     Map<String, SqlStatement> statements, List<ResultMapInfo> resultMaps) {
        this.file = file;
        this.relativePath = relativePath;
        this.namespace = namespace;
        this.statements = Collections.unmodifiableMap(statements);
        this.resultMaps = Collections.unmodifiableList(resultMaps);
    }

    public Path getFile() {
        return file;
    }

    public String getRelativePath() {
        return relativePath;
    }

    public String getNamespace() {
        return namespace;
    }

    /** Statements keyed by id, in document order. */
    public Map<String, SqlStatement> getStatements() {
        return statements;
    }

    public List<ResultMapInfo> getResultMaps() {
        return resultMaps;
    }
}
