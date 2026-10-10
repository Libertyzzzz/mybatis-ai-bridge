package com.nextify.mab.model;

/** A {@code <select|insert|update|delete>} element from a mapper XML, with its SQL already compacted. */
public final class SqlStatement {

    private final String id;
    private final String kind;
    private final String parameterType;
    private final String resultType;
    private final String resultMap;
    private final String sql;

    public SqlStatement(String id, String kind, String parameterType, String resultType, String resultMap, String sql) {
        this.id = id;
        this.kind = kind;
        this.parameterType = parameterType;
        this.resultType = resultType;
        this.resultMap = resultMap;
        this.sql = sql;
    }

    public String getId() {
        return id;
    }

    public String getKind() {
        return kind;
    }

    /** May be null. */
    public String getParameterType() {
        return parameterType;
    }

    /** May be null. */
    public String getResultType() {
        return resultType;
    }

    /** May be null. */
    public String getResultMap() {
        return resultMap;
    }

    public String getSql() {
        return sql;
    }
}
