package com.nextify.mab.parse;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.TypeParameter;
import com.nextify.mab.model.Diagnostic;
import com.nextify.mab.model.MapperInterface;
import com.nextify.mab.model.MapperMethod;
import com.nextify.mab.util.Paths2;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Finds MyBatis mapper interfaces in Java sources (pure syntax parsing, no compilation needed). */
public final class MapperInterfaceParser {

    private static final Set<String> SQL_ANNOTATIONS = new HashSet<>(Arrays.asList(
            "Select", "Insert", "Update", "Delete",
            "SelectProvider", "InsertProvider", "UpdateProvider", "DeleteProvider"));

    /** Generic CRUD base interfaces (MyBatis-Plus, MyBatis-Plus-Join, tk.mybatis). */
    private static final Set<String> GENERIC_CRUD_BASES = new HashSet<>(Arrays.asList(
            "BaseMapper", "MPJBaseMapper", "Mapper", "MySqlMapper"));

    private final Path root;
    private final JavaParser parser;

    public MapperInterfaceParser(Path root) {
        this.root = root;
        this.parser = new JavaParser(new ParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.BLEEDING_EDGE));
    }

    /**
     * @param xmlNamespaces namespaces declared in mapper XML files; interfaces with these names are always mappers
     */
    public List<MapperInterface> parseAll(List<Path> files, Set<String> xmlNamespaces, List<Diagnostic> diagnostics) {
        Set<String> xmlSimpleNames = new HashSet<>();
        for (String ns : xmlNamespaces) {
            xmlSimpleNames.add(ns.substring(ns.lastIndexOf('.') + 1));
        }
        List<MapperInterface> result = new ArrayList<>();
        for (Path file : files) {
            String rel = Paths2.relative(root, file);
            String source;
            try {
                source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
            } catch (IOException e) {
                diagnostics.add(Diagnostic.warn(rel, "cannot read file: " + e.getMessage()));
                continue;
            }
            if (!isCandidate(file, source, xmlSimpleNames)) {
                continue;
            }
            ParseResult<CompilationUnit> parsed = parser.parse(source);
            Optional<CompilationUnit> cu = parsed.getResult();
            if (!cu.isPresent() || !parsed.isSuccessful()) {
                diagnostics.add(Diagnostic.info(rel, "skipped: Java source could not be parsed"));
                continue;
            }
            for (ClassOrInterfaceDeclaration decl : cu.get().findAll(ClassOrInterfaceDeclaration.class)) {
                if (decl.isInterface()) {
                    MapperInterface mi = toMapper(file, rel, decl, xmlNamespaces);
                    if (mi != null) {
                        result.add(mi);
                    }
                }
            }
        }
        return result;
    }

    /** Cheap text pre-filter so we do not parse every Java file of a large project. */
    private static boolean isCandidate(Path file, String source, Set<String> xmlSimpleNames) {
        if (!source.contains("interface")) {
            return false;
        }
        String fileName = file.getFileName().toString();
        String simple = fileName.substring(0, fileName.length() - ".java".length());
        return xmlSimpleNames.contains(simple)
                || source.contains("org.apache.ibatis")
                || source.contains("Mapper");
    }

    private MapperInterface toMapper(Path file, String rel, ClassOrInterfaceDeclaration decl, Set<String> xmlNamespaces) {
        String fqcn = decl.getFullyQualifiedName().orElse(decl.getNameAsString());

        List<String> extendsTypes = new ArrayList<>();
        boolean genericCrud = false;
        for (ClassOrInterfaceType t : decl.getExtendedTypes()) {
            extendsTypes.add(t.asString());
            if (GENERIC_CRUD_BASES.contains(t.getNameAsString()) && t.getTypeArguments().isPresent()) {
                genericCrud = true;
            }
        }

        List<MapperMethod> methods = new ArrayList<>();
        boolean anySqlAnnotation = false;
        for (MethodDeclaration m : decl.getMethods()) {
            if (m.isStatic() || m.isPrivate()) {
                continue;
            }
            String annotation = null;
            String annotationSql = null;
            for (AnnotationExpr a : m.getAnnotations()) {
                String name = a.getName().getIdentifier();
                if (SQL_ANNOTATIONS.contains(name)) {
                    annotation = name;
                    annotationSql = name.endsWith("Provider") ? a.toString() : annotationValue(a);
                    anySqlAnnotation = true;
                    break;
                }
            }
            String doc = m.getJavadoc().map(j -> firstSentence(j.getDescription().toText())).orElse(null);
            methods.add(new MapperMethod(m.getNameAsString(), signature(m), doc, annotation, annotationSql, m.isDefault()));
        }

        boolean isMapper = xmlNamespaces.contains(fqcn)
                || decl.getAnnotationByName("Mapper").isPresent()
                || anySqlAnnotation
                || genericCrud;
        return isMapper ? new MapperInterface(file, rel, fqcn, extendsTypes, genericCrud, methods) : null;
    }

    static String signature(MethodDeclaration m) {
        StringBuilder sb = new StringBuilder();
        if (!m.getTypeParameters().isEmpty()) {
            sb.append('<');
            List<String> tps = new ArrayList<>();
            for (TypeParameter tp : m.getTypeParameters()) {
                tps.add(tp.toString());
            }
            sb.append(String.join(", ", tps)).append("> ");
        }
        sb.append(m.getType().asString()).append(' ').append(m.getNameAsString()).append('(');
        List<String> params = new ArrayList<>();
        for (Parameter p : m.getParameters()) {
            StringBuilder ps = new StringBuilder();
            for (AnnotationExpr a : p.getAnnotations()) {
                ps.append(a.toString()).append(' ');
            }
            ps.append(p.getType().asString());
            if (p.isVarArgs()) {
                ps.append("...");
            }
            ps.append(' ').append(p.getNameAsString());
            params.add(ps.toString());
        }
        return sb.append(String.join(", ", params)).append(')').toString();
    }

    private static String annotationValue(AnnotationExpr a) {
        if (a.isSingleMemberAnnotationExpr()) {
            return literal(a.asSingleMemberAnnotationExpr().getMemberValue());
        }
        if (a.isNormalAnnotationExpr()) {
            for (MemberValuePair pair : a.asNormalAnnotationExpr().getPairs()) {
                if ("value".equals(pair.getNameAsString())) {
                    return literal(pair.getValue());
                }
            }
        }
        return "";
    }

    /** Evaluates string literals, text blocks, {@code "a" + "b"} and {@code {"a", "b"}} (joined by a space, as MyBatis does). */
    private static String literal(Expression e) {
        if (e.isStringLiteralExpr()) {
            return e.asStringLiteralExpr().asString();
        }
        if (e.isTextBlockLiteralExpr()) {
            return e.asTextBlockLiteralExpr().asString();
        }
        if (e.isEnclosedExpr()) {
            return literal(e.asEnclosedExpr().getInner());
        }
        if (e.isBinaryExpr() && e.asBinaryExpr().getOperator() == BinaryExpr.Operator.PLUS) {
            return literal(e.asBinaryExpr().getLeft()) + literal(e.asBinaryExpr().getRight());
        }
        if (e.isArrayInitializerExpr()) {
            List<String> parts = new ArrayList<>();
            for (Expression v : e.asArrayInitializerExpr().getValues()) {
                parts.add(literal(v));
            }
            return String.join(" ", parts);
        }
        // Constant reference or something we cannot evaluate statically.
        return "{" + e + "}";
    }

    static String firstSentence(String text) {
        String s = text.trim();
        if (s.isEmpty()) {
            return null;
        }
        int end = s.length();
        for (String stop : new String[]{"\n", ". ", "。"}) {
            int i = s.indexOf(stop);
            if (i >= 0 && i < end) {
                end = stop.equals("\n") ? i : i + stop.trim().length();
            }
        }
        s = s.substring(0, end).trim();
        return s.length() > 160 ? s.substring(0, 157) + "..." : s;
    }
}
