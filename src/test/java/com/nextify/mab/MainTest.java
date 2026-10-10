package com.nextify.mab;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MainTest {

    @Test
    void checkModeFailsWhenProblemsExist() throws Exception {
        int code = new CommandLine(new Main()).execute("--check", BridgeTest.sampleProject().toString());
        assertEquals(1, code);
    }

    @Test
    void writesMarkdownFile(@TempDir Path tmp) throws Exception {
        Path out = tmp.resolve("ctx/MYBATIS_CONTEXT.md");
        int code = new CommandLine(new Main()).execute(BridgeTest.sampleProject().toString(), "-o", out.toString());
        assertEquals(0, code);
        String md = new String(Files.readAllBytes(out), StandardCharsets.UTF_8);
        assertTrue(md.startsWith("# MyBatis Context"));
    }

    @Test
    void rejectsMissingDirectory(@TempDir Path tmp) {
        int code = new CommandLine(new Main()).execute(tmp.resolve("nope").toString());
        assertEquals(2, code);
    }
}
