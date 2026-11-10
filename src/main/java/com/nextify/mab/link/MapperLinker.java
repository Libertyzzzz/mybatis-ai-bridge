package com.nextify.mab.link;

import com.nextify.mab.model.BoundStatement;
import com.nextify.mab.model.BoundStatement.Source;
import com.nextify.mab.model.Diagnostic;
import com.nextify.mab.model.MapperInterface;
import com.nextify.mab.model.MapperMethod;
import com.nextify.mab.model.MapperUnit;
import com.nextify.mab.model.MapperXml;
import com.nextify.mab.model.SqlStatement;
import com.nextify.mab.parse.MapperXmlParser;
import com.nextify.mab.parse.SqlRenderer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Joins mapper interfaces with XML statements by namespace + method name, and reports mismatches. */
public final class MapperLinker {

    private final MapperXmlParser xmlParser;

    public MapperLinker(MapperXmlParser xmlParser) {
        this.xmlParser = xmlParser;
    }

    public List<MapperUnit> link(List<MapperInterface> interfaces, List<MapperXml> xmls) {
        Map<String, MapperInterface> byFqcn = new LinkedHashMap<>();
        for (MapperInterface mi : interfaces) {
            if (!byFqcn.containsKey(mi.getFqcn())) {
                byFqcn.put(mi.getFqcn(), mi);
            }
        }
        Map<String, List<MapperXml>> byNamespace = new LinkedHashMap<>();
        for (MapperXml x : xmls) {
            List<MapperXml> list = byNamespace.get(x.getNamespace());
            if (list == null) {
                list = new ArrayList<>();
                byNamespace.put(x.getNamespace(), list);
            }
            list.add(x);
        }

        Set<String> namespaces = new TreeSet<>(byFqcn.keySet());
        namespaces.addAll(byNamespace.keySet());
        List<MapperUnit> units = new ArrayList<>();
        for (String ns : namespaces) {
            List<MapperXml> list = byNamespace.get(ns);
            units.add(buildUnit(ns, byFqcn.get(ns), list == null ? Collections.<MapperXml>emptyList() : list));
        }
        units.sort(Comparator.comparing(MapperUnit::getSimpleName).thenComparing(MapperUnit::getNamespace));
        return units;
    }

    private MapperUnit buildUnit(String ns, MapperInterface iface, List<MapperXml> xmls) {
        String simple = ns.substring(ns.lastIndexOf('.') + 1);
        List<Diagnostic> diags = new ArrayList<>();
        List<BoundStatement> entries = new ArrayList<>();

        Map<String, SqlStatement> statements = new LinkedHashMap<>();
        for (MapperXml x : xmls) {
            for (SqlStatement st : x.getStatements().values()) {
                if (statements.put(st.getId(), st) != null) {
                    diags.add(Diagnostic.error(simple + "#" + st.getId(),
                            "statement id defined in more than one XML file of the same namespace"));
                }
            }
        }

        Set<String> used = new HashSet<>();
        if (iface != null) {
            for (MapperMethod m : iface.getMethods()) {
                String loc = simple + "." + m.getName();
                if (m.isDefaultMethod() && m.getAnnotation() == null) {
                    entries.add(new BoundStatement(m.getName(), m.getSignature(), m.getDoc(),
                            null, null, null, Source.DEFAULT_METHOD));
                    continue;
                }
                SqlStatement st = statements.get(m.getName());
                if (st != null) {
                    used.add(m.getName());
                    if (m.getAnnotation() != null) {
                        diags.add(Diagnostic.error(loc, "bound both in XML and by @" + m.getAnnotation()
                                + " (MyBatis fails at startup)"));
                    }
                    entries.add(new BoundStatement(m.getName(), m.getSignature(), m.getDoc(), st.getKind(),
                            xmlAttributes(st), st.getSql(), Source.XML));
                } else if (m.getAnnotation() != null) {
                    boolean provider = m.isProvider();
                    entries.add(new BoundStatement(m.getName(), m.getSignature(), m.getDoc(), m.getAnnotationKind(),
                            null, provider ? m.getAnnotationSql() : annotationSql(m.getAnnotationSql(), ns),
                            provider ? Source.PROVIDER : Source.ANNOTATION));
                } else {
                    entries.add(new BoundStatement(m.getName(), m.getSignature(), m.getDoc(),
                            null, null, null, Source.UNBOUND));
                    diags.add(Diagnostic.warn(loc, "no SQL found (no XML statement with this id, no SQL annotation)"
                            + (xmls.isEmpty() ? "; no mapper XML found for this namespace" : "")));
                }
            }
        }

        for (SqlStatement st : statements.values()) {
            if (used.contains(st.getId())) {
                continue;
            }
            entries.add(new BoundStatement(st.getId(), null, null, st.getKind(),
                    xmlAttributes(st), st.getSql(), Source.ORPHAN));
            if (iface != null) {
                diags.add(Diagnostic.warn(simple + "#" + st.getId(),
                        "XML statement has no matching method in " + iface.getFqcn()));
            }
        }

        if (iface == null) {
            diags.add(Diagnostic.info(simple, "no interface source found for namespace " + ns
                    + " (it may live in a dependency, or the namespace has a typo)"));
        }
        return new MapperUnit(ns, iface, xmls, entries, diags);
    }

    private String annotationSql(String raw, String ns) {
        String trimmed = raw.trim();
        if (trimmed.startsWith("<script>")) {
            String rendered = xmlParser.renderScript(trimmed, ns);
            if (rendered != null) {
                return rendered;
            }
        }
        return SqlRenderer.normalize(trimmed);
    }

    private static String xmlAttributes(SqlStatement st) {
        List<String> parts = new ArrayList<>();
        if (st.getParameterType() != null) {
            parts.add("parameterType=" + st.getParameterType());
        }
        if (st.getResultType() != null) {
            parts.add("resultType=" + st.getResultType());
        }
        if (st.getResultMap() != null) {
            parts.add("resultMap=" + st.getResultMap());
        }
        return parts.isEmpty() ? null : String.join(" · ", parts);
    }
}
