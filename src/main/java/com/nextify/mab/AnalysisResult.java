package com.nextify.mab;

import com.nextify.mab.model.Diagnostic;
import com.nextify.mab.model.MapperUnit;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Output of {@link Bridge#analyze(Path)}. */
public final class AnalysisResult {

    private final Path root;
    private final List<MapperUnit> units;
    private final List<Diagnostic> parseDiagnostics;
    private final long sourceTokens;

    public AnalysisResult(Path root, List<MapperUnit> units, List<Diagnostic> parseDiagnostics, long sourceTokens) {
        this.root = root;
        this.units = Collections.unmodifiableList(units);
        this.parseDiagnostics = Collections.unmodifiableList(parseDiagnostics);
        this.sourceTokens = sourceTokens;
    }

    public Path getRoot() {
        return root;
    }

    public List<MapperUnit> getUnits() {
        return units;
    }

    /** Parse-level diagnostics followed by per-mapper diagnostics. */
    public List<Diagnostic> getDiagnostics() {
        List<Diagnostic> all = new ArrayList<>(parseDiagnostics);
        for (MapperUnit u : units) {
            all.addAll(u.getDiagnostics());
        }
        return all;
    }

    public boolean hasActionableDiagnostics() {
        for (Diagnostic d : getDiagnostics()) {
            if (d.isActionable()) {
                return true;
            }
        }
        return false;
    }

    public int getStatementCount() {
        int n = 0;
        for (MapperUnit u : units) {
            n += u.getStatementCount();
        }
        return n;
    }

    /** Estimated tokens of the raw interface + XML sources that were condensed. */
    public long getSourceTokens() {
        return sourceTokens;
    }
}
