package com.nextify.mab.model;

/** One entry of a mapper: an interface method and/or the SQL bound to it. */
public final class BoundStatement {

    public enum Source {
        /** SQL comes from the mapper XML. */
        XML,
        /** SQL comes from {@code @Select}/{@code @Insert}/... */
        ANNOTATION,
        /** SQL is built by a {@code @XxxProvider} class at runtime. */
        PROVIDER,
        /** Java {@code default} method, no SQL. */
        DEFAULT_METHOD,
        /** Abstract method without any SQL binding. */
        UNBOUND,
        /** XML statement without a matching interface method. */
        ORPHAN
    }

    private final String name;
    private final String signature;
    private final String doc;
    private final String kind;
    private final String attributes;
    private final String sql;
    private final Source source;

    public BoundStatement(String name, String signature, String doc, String kind,
                          String attributes, String sql, Source source) {
        this.name = name;
        this.signature = signature;
        this.doc = doc;
        this.kind = kind;
        this.attributes = attributes;
        this.sql = sql;
        this.source = source;
    }

    public String getName() {
        return name;
    }

    /** Java signature, or null for orphan XML statements. */
    public String getSignature() {
        return signature;
    }

    public String getDoc() {
        return doc;
    }

    /** select/insert/update/delete, or null. */
    public String getKind() {
        return kind;
    }

    /** e.g. {@code resultMap=BaseResultMap}, or null. */
    public String getAttributes() {
        return attributes;
    }

    /** Compacted SQL, or null. */
    public String getSql() {
        return sql;
    }

    public Source getSource() {
        return source;
    }
}
