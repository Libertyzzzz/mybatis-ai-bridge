package com.nextify.mab.model;

/** A method declared on a mapper interface. */
public final class MapperMethod {

    private final String name;
    private final String signature;
    private final String doc;
    private final String annotation;
    private final String annotationSql;
    private final boolean defaultMethod;

    /**
     * @param annotation    simple name of the SQL annotation ({@code Select}, {@code UpdateProvider}, ...) or null
     * @param annotationSql raw SQL from the annotation, or the annotation source for providers; null if none
     */
    public MapperMethod(String name, String signature, String doc,
                        String annotation, String annotationSql, boolean defaultMethod) {
        this.name = name;
        this.signature = signature;
        this.doc = doc;
        this.annotation = annotation;
        this.annotationSql = annotationSql;
        this.defaultMethod = defaultMethod;
    }

    public String getName() {
        return name;
    }

    public String getSignature() {
        return signature;
    }

    /** First sentence of the Javadoc, or null. */
    public String getDoc() {
        return doc;
    }

    public String getAnnotation() {
        return annotation;
    }

    public String getAnnotationSql() {
        return annotationSql;
    }

    public boolean isDefaultMethod() {
        return defaultMethod;
    }

    public boolean isProvider() {
        return annotation != null && annotation.endsWith("Provider");
    }

    /** {@code select}/{@code insert}/{@code update}/{@code delete} derived from the annotation, or null. */
    public String getAnnotationKind() {
        if (annotation == null) {
            return null;
        }
        String base = isProvider() ? annotation.substring(0, annotation.length() - "Provider".length()) : annotation;
        return base.toLowerCase(java.util.Locale.ROOT);
    }
}
