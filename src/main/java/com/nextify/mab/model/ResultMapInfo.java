package com.nextify.mab.model;

import java.util.Collections;
import java.util.List;

/** Condensed view of a {@code <resultMap>}: column→property pairs and nested associations. */
public final class ResultMapInfo {

    private final String id;
    private final String type;
    private final String extendsId;
    private final List<String> mappings;

    public ResultMapInfo(String id, String type, String extendsId, List<String> mappings) {
        this.id = id;
        this.type = type;
        this.extendsId = extendsId;
        this.mappings = Collections.unmodifiableList(mappings);
    }

    public String getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    /** May be null. */
    public String getExtendsId() {
        return extendsId;
    }

    public List<String> getMappings() {
        return mappings;
    }
}
