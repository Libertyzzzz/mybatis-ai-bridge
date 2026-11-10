package com.nextify.mab;

import com.nextify.mab.model.BoundStatement;
import com.nextify.mab.model.BoundStatement.Source;
import com.nextify.mab.model.Diagnostic;
import com.nextify.mab.model.MapperUnit;
import com.nextify.mab.render.MarkdownRenderer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BridgeTest {

    private static AnalysisResult result;

    static Path sampleProject() throws Exception {
        return Paths.get(BridgeTest.class.getResource("/sample-project").toURI());
    }

    @BeforeAll
    static void analyze() throws Exception {
        result = new Bridge(BridgeOptions.defaults()).analyze(sampleProject());
    }

    @Test
    void detectsMappersAndIgnoresOtherInterfaces() {
        List<String> names = new ArrayList<>();
        for (MapperUnit u : result.getUnits()) {
            names.add(u.getSimpleName());
        }
        assertEquals(Arrays.asList("CommonMapper", "OrderMapper", "UserMapper"), names);
    }

    @Test
    void rendersDynamicSqlCompactlyWithIncludesInlined() {
        BoundStatement s = entry("UserMapper", "findUsers");
        assertEquals(Source.XML, s.getSource());
        assertEquals("resultMap=BaseResultMap", s.getAttributes());
        assertEquals(String.join("\n",
                "SELECT id, user_name, status, dept_id, created_at FROM t_user",
                "<where>",
                "  <if test=\"name != null and name != ''\">AND user_name LIKE CONCAT('%', #{name}, '%')</if>",
                "  <if test=\"status != null\">AND status = #{status}</if>",
                "</where>",
                "ORDER BY created_at DESC",
                "<if test=\"limit != null\">LIMIT #{limit}</if>"), s.getSql());
    }

    @Test
    void decodesCdataAndKeepsSignatureAnnotations() {
        BoundStatement s = entry("UserMapper", "selectWithDept");
        assertTrue(s.getSql().endsWith("WHERE u.id = #{id} AND u.status < 9"), s.getSql());
        assertEquals("User selectWithDept(@Param(\"id\") Long id)", s.getSignature());
        assertEquals("Load a user together with its department.", s.getDoc());
    }

    @Test
    void readsAnnotationSql() {
        BoundStatement count = entry("UserMapper", "countByStatus");
        assertEquals(Source.ANNOTATION, count.getSource());
        assertEquals("select", count.getKind());
        assertEquals("SELECT COUNT(*) FROM t_user WHERE status = #{status}", count.getSql());

        BoundStatement script = entry("OrderMapper", "findByUser");
        assertEquals(String.join("\n",
                "SELECT * FROM t_order",
                "<where>",
                "  <if test=\"userId != null\">AND user_id = #{userId}</if>",
                "</where>"), script.getSql());

        BoundStatement provider = entry("OrderMapper", "search");
        assertEquals(Source.PROVIDER, provider.getSource());
        assertTrue(provider.getSql().contains("OrderSqlProvider.class"));
    }

    @Test
    void classifiesDefaultUnboundAndOrphanEntries() {
        assertEquals(Source.DEFAULT_METHOD, entry("UserMapper", "exists").getSource());
        assertEquals(Source.UNBOUND, entry("UserMapper", "unboundMethod").getSource());
        BoundStatement orphan = entry("UserMapper", "legacyCleanup");
        assertEquals(Source.ORPHAN, orphan.getSource());
        assertNull(orphan.getSignature());

        // Verify overloaded default method is categorized as DEFAULT_METHOD, not XML
        MapperUnit userMapper = null;
        for (MapperUnit u : result.getUnits()) {
            if ("UserMapper".equals(u.getSimpleName())) {
                userMapper = u;
                break;
            }
        }
        int findUsersCount = 0;
        int defaultOverloadCount = 0;
        for (BoundStatement e : userMapper.getEntries()) {
            if ("findUsers".equals(e.getName())) {
                if (e.getSource() == Source.XML) {
                    findUsersCount++;
                } else if (e.getSource() == Source.DEFAULT_METHOD) {
                    defaultOverloadCount++;
                }
            }
        }
        assertEquals(1, findUsersCount);
        assertEquals(1, defaultOverloadCount);
    }

    @Test
    void reportsDiagnostics() {
        List<String> lines = new ArrayList<>();
        for (Diagnostic d : result.getDiagnostics()) {
            lines.add(d.toString());
        }
        assertTrue(lines.contains("[WARN] UserMapper.unboundMethod: no SQL found (no XML statement with this id, "
                + "no SQL annotation)"), lines.toString());
        assertTrue(lines.contains("[WARN] UserMapper#legacyCleanup: XML statement has no matching method in "
                + "com.example.mapper.UserMapper"), lines.toString());
        assertTrue(lines.stream().anyMatch(l -> l.startsWith("[INFO] CommonMapper:")), lines.toString());
        assertTrue(result.hasActionableDiagnostics());
    }

    @Test
    void rendersMarkdown() {
        String md = new MarkdownRenderer(true).render(result);
        assertTrue(md.contains("## UserMapper"));
        assertTrue(md.contains("### `List<User> findUsers(@Param(\"name\") String name, "
                + "@Param(\"status\") Integer status)`"));
        assertTrue(md.contains("- extends: `BaseMapper<User>` — inherited generic CRUD methods are not listed"));
        assertTrue(md.contains("- resultMap `BaseResultMap` → `com.example.entity.User`: id, user_name→userName, status"));
        assertTrue(md.contains("dept: Dept {dept_id→id, dept_name→name}"));
        assertTrue(md.contains("## Diagnostics"));
        assertFalse(md.contains("UserService"));
        assertFalse(md.contains("list users"), "XML comments must be stripped");
        assertFalse(md.contains("fuzzy match"), "SQL comments must be stripped");
    }

    @Test
    void filtersMappers() throws Exception {
        AnalysisResult filtered = new Bridge(BridgeOptions.defaults()
                .mapperFilters(Collections.singletonList("order"))).analyze(sampleProject());
        assertEquals(1, filtered.getUnits().size());
        assertEquals("OrderMapper", filtered.getUnits().get(0).getSimpleName());
    }

    @Test
    void canKeepIncludesUninlined() throws Exception {
        AnalysisResult raw = new Bridge(BridgeOptions.defaults().inlineIncludes(false)).analyze(sampleProject());
        for (MapperUnit u : raw.getUnits()) {
            for (BoundStatement e : u.getEntries()) {
                if (e.getName().equals("findUsers")) {
                    assertTrue(e.getSql().contains("<include refid=\"Base_Column_List\"/>"), e.getSql());
                    return;
                }
            }
        }
        throw new AssertionError("findUsers not found");
    }

    @Test
    void normalizesSqlPreservingStringsWithDashes() {
        String sql = "SELECT * FROM t_user WHERE code = '--' AND flag = 'a--b''c' -- this is comment\nAND 1=1";
        String normalized = com.nextify.mab.parse.SqlRenderer.normalize(sql);
        assertEquals("SELECT * FROM t_user WHERE code = '--' AND flag = 'a--b''c' AND 1=1", normalized);
    }

    private static BoundStatement entry(String mapper, String name) {
        for (MapperUnit u : result.getUnits()) {
            if (u.getSimpleName().equals(mapper)) {
                for (BoundStatement e : u.getEntries()) {
                    if (e.getName().equals(name)) {
                        return e;
                    }
                }
            }
        }
        throw new AssertionError(mapper + "." + name + " not found");
    }
}
