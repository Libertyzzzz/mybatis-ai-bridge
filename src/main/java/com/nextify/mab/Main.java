package com.nextify.mab;

import com.nextify.mab.model.Diagnostic;
import com.nextify.mab.render.MarkdownRenderer;
import com.nextify.mab.util.Tokens;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/** Command line entry point. */
@Command(name = "mybatis-ai-bridge",
        mixinStandardHelpOptions = true,
        version = "mybatis-ai-bridge 0.1.0",
        description = "Links MyBatis mapper interfaces with their XML/annotation SQL and writes a compact, "
                + "LLM-friendly Markdown context file.")
public final class Main implements Callable<Integer> {

    @Parameters(index = "0", arity = "0..1", defaultValue = ".", paramLabel = "PROJECT_DIR",
            description = "Project root to scan (default: current directory).")
    private Path projectDir;

    @Option(names = {"-o", "--output"}, defaultValue = "MYBATIS_CONTEXT.md",
            description = "Output file, relative to PROJECT_DIR; '-' for stdout (default: ${DEFAULT-VALUE}).")
    private String output;

    @Option(names = {"-m", "--mapper"}, split = ",", paramLabel = "NAME",
            description = "Only include matching mappers (simple-name substring or full namespace). Repeatable.")
    private List<String> mappers = new ArrayList<>();

    @Option(names = "--no-inline-include", description = "Keep <include refid=.../> instead of inlining fragments.")
    private boolean noInlineInclude;

    @Option(names = "--no-result-maps", description = "Omit resultMap summaries.")
    private boolean noResultMaps;

    @Option(names = "--include-tests", description = "Also scan src/test directories.")
    private boolean includeTests;

    @Option(names = "--check",
            description = "Only report diagnostics; exit code 1 if any ERROR/WARN is found (for CI / git hooks).")
    private boolean check;

    public static void main(String[] args) {
        System.exit(new CommandLine(new Main()).execute(args));
    }

    @Override
    public Integer call() throws Exception {
        PrintStream out = utf8(System.out);
        PrintStream err = utf8(System.err);
        Path root = projectDir.toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            err.println("Not a directory: " + root);
            return 2;
        }

        BridgeOptions options = BridgeOptions.defaults()
                .inlineIncludes(!noInlineInclude)
                .includeTests(includeTests)
                .mapperFilters(mappers);
        AnalysisResult result = new Bridge(options).analyze(root);
        List<Diagnostic> diagnostics = result.getDiagnostics();

        if (check) {
            for (Diagnostic d : diagnostics) {
                out.println(d);
            }
            out.println(result.getUnits().size() + " mappers, " + result.getStatementCount() + " statements, "
                    + diagnostics.size() + " diagnostics");
            out.flush();
            return result.hasActionableDiagnostics() ? 1 : 0;
        }

        String markdown = new MarkdownRenderer(!noResultMaps).render(result);
        if ("-".equals(output)) {
            out.print(markdown);
            out.flush();
        } else {
            Path target = root.resolve(output).normalize();
            if (target.getParent() != null) {
                Files.createDirectories(target.getParent());
            }
            Files.write(target, markdown.getBytes(StandardCharsets.UTF_8));
            long outTokens = Tokens.estimate(markdown);
            long srcTokens = result.getSourceTokens();
            String ratio = srcTokens > 0 ? String.format(" (sources ~%d tokens, %d%% smaller)",
                    srcTokens, Math.max(0, Math.round(100.0 * (srcTokens - outTokens) / srcTokens))) : "";
            err.println("Wrote " + target + ": " + result.getUnits().size() + " mappers, "
                    + result.getStatementCount() + " statements, ~" + outTokens + " tokens" + ratio);
        }
        long actionable = 0;
        for (Diagnostic d : diagnostics) {
            if (d.isActionable()) {
                actionable++;
            }
        }
        if (actionable > 0) {
            err.println(actionable + " issue(s) found — see the Diagnostics section or run with --check");
        }
        err.flush();
        return 0;
    }

    private static PrintStream utf8(PrintStream stream) throws UnsupportedEncodingException {
        return new PrintStream(stream, true, StandardCharsets.UTF_8.name());
    }
}
