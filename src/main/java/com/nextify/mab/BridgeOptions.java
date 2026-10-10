package com.nextify.mab;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Options controlling what {@link Bridge} analyses. */
public final class BridgeOptions {

    private boolean inlineIncludes = true;
    private boolean includeTests = false;
    private List<String> mapperFilters = new ArrayList<>();

    public static BridgeOptions defaults() {
        return new BridgeOptions();
    }

    public boolean isInlineIncludes() {
        return inlineIncludes;
    }

    public BridgeOptions inlineIncludes(boolean value) {
        this.inlineIncludes = value;
        return this;
    }

    public boolean isIncludeTests() {
        return includeTests;
    }

    public BridgeOptions includeTests(boolean value) {
        this.includeTests = value;
        return this;
    }

    public List<String> getMapperFilters() {
        return Collections.unmodifiableList(mapperFilters);
    }

    /** Keep only mappers whose simple name or namespace matches (case-insensitive, substring of simple name allowed). */
    public BridgeOptions mapperFilters(List<String> filters) {
        this.mapperFilters = filters == null ? new ArrayList<String>() : new ArrayList<>(filters);
        return this;
    }
}
