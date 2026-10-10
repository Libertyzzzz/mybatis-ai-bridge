package com.nextify.mab;

import com.nextify.mab.link.MapperLinker;
import com.nextify.mab.model.Diagnostic;
import com.nextify.mab.model.MapperInterface;
import com.nextify.mab.model.MapperUnit;
import com.nextify.mab.model.MapperXml;
import com.nextify.mab.parse.MapperInterfaceParser;
import com.nextify.mab.parse.MapperXmlParser;
import com.nextify.mab.scan.ProjectScanner;
import com.nextify.mab.util.Tokens;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Entry point of the library: scan → parse XML → parse interfaces → link. */
public final class Bridge {

    private final BridgeOptions options;

    public Bridge(BridgeOptions options) {
        this.options = options;
    }

    public AnalysisResult analyze(Path projectDir) throws IOException {
        Path root = projectDir.toAbsolutePath().normalize();
        ProjectScanner.Result files = new ProjectScanner(options.isIncludeTests()).scan(root);
        List<Diagnostic> diagnostics = new ArrayList<>();

        MapperXmlParser xmlParser = new MapperXmlParser(root, options.isInlineIncludes());
        List<MapperXml> xmls = xmlParser.parseAll(files.getXmlFiles(), diagnostics);

        Set<String> namespaces = new LinkedHashSet<>();
        for (MapperXml x : xmls) {
            namespaces.add(x.getNamespace());
        }
        List<MapperInterface> interfaces =
                new MapperInterfaceParser(root).parseAll(files.getJavaFiles(), namespaces, diagnostics);

        List<MapperUnit> units = filter(new MapperLinker(xmlParser).link(interfaces, xmls));
        return new AnalysisResult(root, units, diagnostics, sourceTokens(units));
    }

    private List<MapperUnit> filter(List<MapperUnit> units) {
        if (options.getMapperFilters().isEmpty()) {
            return units;
        }
        List<MapperUnit> kept = new ArrayList<>();
        for (MapperUnit u : units) {
            String simple = u.getSimpleName().toLowerCase(Locale.ROOT);
            String ns = u.getNamespace().toLowerCase(Locale.ROOT);
            for (String f : options.getMapperFilters()) {
                String needle = f.trim().toLowerCase(Locale.ROOT);
                if (!needle.isEmpty() && (ns.equals(needle) || simple.contains(needle))) {
                    kept.add(u);
                    break;
                }
            }
        }
        return kept;
    }

    private static long sourceTokens(List<MapperUnit> units) {
        Set<Path> paths = new LinkedHashSet<>();
        for (MapperUnit u : units) {
            if (u.getMapperInterface() != null) {
                paths.add(u.getMapperInterface().getFile());
            }
            for (MapperXml x : u.getXmls()) {
                paths.add(x.getFile());
            }
        }
        long total = 0;
        for (Path p : paths) {
            try {
                total += Tokens.estimate(new String(Files.readAllBytes(p), StandardCharsets.UTF_8));
            } catch (IOException ignored) {
                // Only used for statistics.
            }
        }
        return total;
    }
}
