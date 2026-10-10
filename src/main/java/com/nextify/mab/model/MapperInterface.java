package com.nextify.mab.model;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

/** A Java interface recognised as a MyBatis mapper. */
public final class MapperInterface {

    private final Path file;
    private final String relativePath;
    private final String fqcn;
    private final List<String> extendsTypes;
    private final boolean inheritsGenericCrud;
    private final List<MapperMethod> methods;

    public MapperInterface(Path file, String relativePath, String fqcn, List<String> extendsTypes,
                           boolean inheritsGenericCrud, List<MapperMethod> methods) {
        this.file = file;
        this.relativePath = relativePath;
        this.fqcn = fqcn;
        this.extendsTypes = Collections.unmodifiableList(extendsTypes);
        this.inheritsGenericCrud = inheritsGenericCrud;
        this.methods = Collections.unmodifiableList(methods);
    }

    public Path getFile() {
        return file;
    }

    public String getRelativePath() {
        return relativePath;
    }

    public String getFqcn() {
        return fqcn;
    }

    /** Extended types as written in source, e.g. {@code BaseMapper<User>}. */
    public List<String> getExtendsTypes() {
        return extendsTypes;
    }

    /** True when the interface extends a generic CRUD mapper (MyBatis-Plus BaseMapper, tk.mybatis Mapper, ...). */
    public boolean isInheritsGenericCrud() {
        return inheritsGenericCrud;
    }

    public List<MapperMethod> getMethods() {
        return methods;
    }
}
