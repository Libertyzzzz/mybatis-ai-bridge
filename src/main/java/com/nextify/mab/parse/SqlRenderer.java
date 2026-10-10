package com.nextify.mab.parse;

import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Turns a MyBatis statement element into compact text: dynamic tags are kept (LLMs understand them),
 * {@code <include>} fragments are inlined, comments and redundant whitespace are removed.
 */
public final class SqlRenderer {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern LINE_COMMENT = Pattern.compile("--[^\\r\\n]*");
    /** Block comments, but keep optimizer hints such as {@code /*+ INDEX(t idx) *}{@code /}. */
    private static final Pattern BLOCK_COMMENT = Pattern.compile("/\\*(?!\\+).*?\\*/", Pattern.DOTALL);
    private static final int INLINE_LIMIT = 120;
    private static final String INDENT = "  ";

    private final Map<String, Element> fragments;
    private final boolean inlineIncludes;

    SqlRenderer(Map<String, Element> fragments, boolean inlineIncludes) {
        this.fragments = fragments;
        this.inlineIncludes = inlineIncludes;
    }

    /** Strips SQL comments and collapses whitespace. */
    public static String normalize(CharSequence text) {
        String s = BLOCK_COMMENT.matcher(text).replaceAll(" ");
        s = LINE_COMMENT.matcher(s).replaceAll(" ");
        return WHITESPACE.matcher(s).replaceAll(" ").trim();
    }

    String render(Element statement, String namespace) {
        StringBuilder out = new StringBuilder();
        renderChildren(statement, namespace, 0, out, new ArrayDeque<String>());
        return out.toString().trim();
    }

    private void renderChildren(Node parent, String namespace, int depth, StringBuilder out, Deque<String> stack) {
        StringBuilder text = new StringBuilder();
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            short type = n.getNodeType();
            if (type == Node.TEXT_NODE || type == Node.CDATA_SECTION_NODE) {
                text.append(' ').append(n.getNodeValue());
                continue;
            }
            if (type != Node.ELEMENT_NODE) {
                continue;
            }
            Element el = (Element) n;
            if ("include".equals(el.getTagName()) && inlineIncludes) {
                String key = resolveFragmentKey(el.getAttribute("refid").trim(), namespace);
                Element fragment = key == null ? null : fragments.get(key);
                if (fragment != null && !stack.contains(key)) {
                    if (!hasElementChildren(fragment)) {
                        // Plain-text fragment (e.g. a column list): merge into the surrounding line.
                        text.append(' ').append(fragment.getTextContent());
                    } else {
                        flush(text, depth, out);
                        stack.push(key);
                        renderChildren(fragment, namespaceOf(key), depth, out, stack);
                        stack.pop();
                    }
                    continue;
                }
            }
            flush(text, depth, out);
            renderElement(el, namespace, depth, out, stack);
        }
        flush(text, depth, out);
    }

    private void renderElement(Element el, String namespace, int depth, StringBuilder out, Deque<String> stack) {
        String tag = el.getTagName();
        if (!hasElementChildren(el)) {
            String body = normalize(el.getTextContent());
            if (body.isEmpty()) {
                line(out, depth, openTag(el, true));
                return;
            }
            String open = openTag(el, false);
            String close = "</" + tag + ">";
            if (depth * INDENT.length() + open.length() + body.length() + close.length() <= INLINE_LIMIT) {
                line(out, depth, open + body + close);
            } else {
                line(out, depth, open);
                line(out, depth + 1, body);
                line(out, depth, close);
            }
            return;
        }
        line(out, depth, openTag(el, false));
        renderChildren(el, namespace, depth + 1, out, stack);
        line(out, depth, "</" + tag + ">");
    }

    private String resolveFragmentKey(String refid, String namespace) {
        if (refid.isEmpty()) {
            return null;
        }
        if (refid.indexOf('.') >= 0 && fragments.containsKey(refid)) {
            return refid;
        }
        return namespace + "." + refid;
    }

    private static String namespaceOf(String fragmentKey) {
        return fragmentKey.substring(0, fragmentKey.lastIndexOf('.'));
    }

    private static void flush(StringBuilder text, int depth, StringBuilder out) {
        if (text.length() == 0) {
            return;
        }
        String s = normalize(text);
        text.setLength(0);
        if (!s.isEmpty()) {
            line(out, depth, s);
        }
    }

    private static void line(StringBuilder out, int depth, String s) {
        for (int i = 0; i < depth; i++) {
            out.append(INDENT);
        }
        out.append(s).append('\n');
    }

    private static String openTag(Element el, boolean selfClose) {
        StringBuilder sb = new StringBuilder("<").append(el.getTagName());
        NamedNodeMap attrs = el.getAttributes();
        for (int i = 0; i < attrs.getLength(); i++) {
            Node a = attrs.item(i);
            sb.append(' ').append(a.getNodeName()).append("=\"").append(a.getNodeValue()).append('"');
        }
        return sb.append(selfClose ? "/>" : ">").toString();
    }

    static boolean hasElementChildren(Node node) {
        for (Node n = node.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n.getNodeType() == Node.ELEMENT_NODE) {
                return true;
            }
        }
        return false;
    }

    static List<Element> childElements(Node node) {
        List<Element> list = new ArrayList<>();
        for (Node n = node.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n.getNodeType() == Node.ELEMENT_NODE) {
                list.add((Element) n);
            }
        }
        return list;
    }
}
