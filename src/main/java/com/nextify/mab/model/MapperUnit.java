package com.nextify.mab.model;

import java.util.Collections;
import java.util.List;

/** A namespace with its interface (if found), XML file(s) and linked statements. */
public final class MapperUnit {

    private final String namespace;
    private final MapperInterface mapperInterface;
    private final List<MapperXml> xmls;
    private final List<BoundStatement> entries;
    private final List<Diagnostic> diagnostics;

    public MapperUnit(String namespace, MapperInterface mapperInterface, List<MapperXml> xmls,
                      List<BoundStatement> entries, List<Diagnostic> diagnostics) {
        this.namespace = namespace;
        this.mapperInterface = mapperInterface;
        this.xmls = Collections.unmodifiableList(xmls);
        this.entries = Collections.unmodifiableList(entries);
        this.diagnostics = Collections.unmodifiableList(diagnostics);
    }

    public String getNamespace() {
        return namespace;
    }

    public String getSimpleName() {
        int dot = namespace.lastIndexOf('.');
        return dot < 0 ? namespace : namespace.substring(dot + 1);
    }

    /** May be null when only XML exists. */
    public MapperInterface getMapperInterface() {
        return mapperInterface;
    }

    public List<MapperXml> getXmls() {
        return xmls;
    }

    public List<BoundStatement> getEntries() {
        return entries;
    }

    public List<Diagnostic> getDiagnostics() {
        return diagnostics;
    }

    /** Number of entries carrying SQL. */
    public int getStatementCount() {
        int n = 0;
        for (BoundStatement e : entries) {
            if (e.getSql() != null) {
                n++;
            }
        }
        return n;
    }
}
