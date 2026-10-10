package com.nextify.mab.parse;

import com.nextify.mab.model.Diagnostic;
import com.nextify.mab.model.MapperXml;
import com.nextify.mab.model.ResultMapInfo;
import com.nextify.mab.model.SqlStatement;
import com.nextify.mab.util.Paths2;
import org.w3c.dom.Element;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Parses MyBatis mapper XML files. Never touches the network (external DTDs are not loaded). */
public final class MapperXmlParser {

    private static final Set<String> STATEMENT_TAGS =
            new HashSet<>(Arrays.asList("select", "insert", "update", "delete"));

    private final Path root;
    private final boolean inlineIncludes;
    private final DocumentBuilder builder;
    /** All {@code <sql>} fragments, keyed by {@code namespace.id}. */
    private final Map<String, Element> fragments = new HashMap<>();

    public MapperXmlParser(Path root, boolean inlineIncludes) {
        this.root = root;
        this.inlineIncludes = inlineIncludes;
        this.builder = newBuilder();
    }

    public List<MapperXml> parseAll(List<Path> files, List<Diagnostic> diagnostics) {
        // Pass 1: parse documents and collect fragments (includes may cross files).
        Map<Path, Element> roots = new LinkedHashMap<>();
        for (Path file : files) {
            String rel = Paths2.relative(root, file);
            byte[] bytes;
            try {
                bytes = Files.readAllBytes(file);
            } catch (IOException e) {
                diagnostics.add(Diagnostic.warn(rel, "cannot read file: " + e.getMessage()));
                continue;
            }
            if (!new String(bytes, StandardCharsets.UTF_8).contains("<mapper")) {
                continue;
            }
            Element rootEl;
            try {
                rootEl = builder.parse(new ByteArrayInputStream(bytes)).getDocumentElement();
            } catch (SAXException | IOException e) {
                diagnostics.add(Diagnostic.warn(rel, "cannot parse mapper XML: " + e.getMessage()));
                continue;
            }
            if (!"mapper".equals(rootEl.getTagName())) {
                continue;
            }
            String ns = rootEl.getAttribute("namespace").trim();
            if (ns.isEmpty()) {
                diagnostics.add(Diagnostic.warn(rel, "<mapper> has no namespace attribute"));
                continue;
            }
            roots.put(file, rootEl);
            for (Element child : SqlRenderer.childElements(rootEl)) {
                if ("sql".equals(child.getTagName())) {
                    fragments.put(ns + "." + child.getAttribute("id").trim(), child);
                }
            }
        }

        // Pass 2: render statements.
        SqlRenderer renderer = new SqlRenderer(fragments, inlineIncludes);
        List<MapperXml> result = new ArrayList<>();
        for (Map.Entry<Path, Element> entry : roots.entrySet()) {
            Element rootEl = entry.getValue();
            String rel = Paths2.relative(root, entry.getKey());
            String ns = rootEl.getAttribute("namespace").trim();
            Map<String, SqlStatement> statements = new LinkedHashMap<>();
            List<ResultMapInfo> resultMaps = new ArrayList<>();
            for (Element child : SqlRenderer.childElements(rootEl)) {
                String tag = child.getTagName();
                if (STATEMENT_TAGS.contains(tag)) {
                    String id = child.getAttribute("id").trim();
                    SqlStatement st = new SqlStatement(id, tag,
                            attr(child, "parameterType"), attr(child, "resultType"), attr(child, "resultMap"),
                            renderer.render(child, ns));
                    if (statements.put(id, st) != null) {
                        diagnostics.add(Diagnostic.error(rel + "#" + id,
                                "duplicate statement id (MyBatis fails at startup)"));
                    }
                } else if ("resultMap".equals(tag)) {
                    resultMaps.add(new ResultMapInfo(child.getAttribute("id").trim(),
                            child.getAttribute("type").trim(), attr(child, "extends"), describeMappings(child)));
                }
            }
            result.add(new MapperXml(entry.getKey(), rel, ns, statements, resultMaps));
        }
        return result;
    }

    /**
     * Renders a {@code <script>...</script>} annotation value using the same rules as XML statements.
     *
     * @return rendered SQL, or null if the script is not well-formed XML
     */
    public String renderScript(String script, String namespace) {
        try {
            Element el = builder.parse(new InputSource(new StringReader(script))).getDocumentElement();
            return new SqlRenderer(fragments, inlineIncludes).render(el, namespace);
        } catch (SAXException | IOException e) {
            return null;
        }
    }

    static List<String> describeMappings(Element parent) {
        List<String> out = new ArrayList<>();
        for (Element c : SqlRenderer.childElements(parent)) {
            switch (c.getTagName()) {
                case "id":
                case "result":
                case "idArg":
                case "arg":
                    String property = c.getAttribute("property");
                    out.add(columnToProperty(c.getAttribute("column"),
                            property.isEmpty() ? c.getAttribute("name") : property));
                    break;
                case "constructor":
                    out.addAll(describeMappings(c));
                    break;
                case "association":
                case "collection":
                    out.add(describeNested(c));
                    break;
                case "discriminator":
                    out.add("discriminator(" + c.getAttribute("column") + ")");
                    break;
                default:
                    break;
            }
        }
        return out;
    }

    private static String describeNested(Element c) {
        boolean collection = "collection".equals(c.getTagName());
        String type = collection && !c.getAttribute("ofType").isEmpty() ? c.getAttribute("ofType") : c.getAttribute("javaType");
        StringBuilder sb = new StringBuilder(c.getAttribute("property")).append(": ");
        sb.append(type.isEmpty() ? "?" : simpleName(type));
        if (collection) {
            sb.append("[]");
        }
        if (!c.getAttribute("resultMap").isEmpty()) {
            sb.append(" (resultMap=").append(c.getAttribute("resultMap")).append(')');
        }
        if (!c.getAttribute("select").isEmpty()) {
            sb.append(" (select=").append(c.getAttribute("select"))
                    .append(", column=").append(c.getAttribute("column")).append(')');
        }
        List<String> nested = describeMappings(c);
        if (!nested.isEmpty()) {
            sb.append(" {").append(String.join(", ", nested)).append('}');
        }
        return sb.toString();
    }

    private static String columnToProperty(String column, String property) {
        if (column.isEmpty()) {
            return property;
        }
        if (property.isEmpty() || column.equals(property)) {
            return column;
        }
        return column + "→" + property;
    }

    private static String simpleName(String type) {
        int dot = type.lastIndexOf('.');
        return dot < 0 ? type : type.substring(dot + 1);
    }

    private static String attr(Element el, String name) {
        String v = el.getAttribute(name).trim();
        return v.isEmpty() ? null : v;
    }

    private static DocumentBuilder newBuilder() {
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setValidating(false);
            f.setNamespaceAware(false);
            f.setIgnoringComments(true);
            f.setXIncludeAware(false);
            f.setExpandEntityReferences(false);
            f.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            f.setFeature("http://xml.org/sax/features/external-general-entities", false);
            f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            DocumentBuilder b = f.newDocumentBuilder();
            b.setErrorHandler(new ErrorHandler() {
                @Override
                public void warning(SAXParseException e) {
                }

                @Override
                public void error(SAXParseException e) throws SAXException {
                    throw e;
                }

                @Override
                public void fatalError(SAXParseException e) throws SAXException {
                    throw e;
                }
            });
            return b;
        } catch (ParserConfigurationException e) {
            throw new IllegalStateException("XML parser not available", e);
        }
    }
}
