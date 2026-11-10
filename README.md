# mybatis-ai-bridge

<p align="center">
  <b>Bridging the gap between Java MyBatis Mapper interfaces and XML SQL for AI coding assistants.</b><br>
  <sub>Compact, LLM-friendly MyBatis context generator & static linter built for Cursor / Claude Code / Copilot / Windsurf</sub>
</p>

<p align="center">
  <a href="https://github.com/Libertyzzzz/mybatis-ai-bridge/actions"><img src="https://img.shields.io/badge/build-passing-brightgreen.svg" alt="Build Status"></a>
  <a href="https://www.oracle.com/java/"><img src="https://img.shields.io/badge/Java-8%20%7C%2017%20%7C%2021-orange.svg" alt="Java 8+"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-MIT-blue.svg" alt="License"></a>
  <a href="https://nextify.cn"><img src="https://img.shields.io/badge/sponsor-NEXTIFY-6366f1.svg" alt="NEXTIFY"></a>
</p>

<p align="center">
  <a href="#-why-mybatis-ai-bridge"><b>English</b></a> · <a href="#-为什么需要它-why-mybatis-ai-bridge"><b>简体中文</b></a>
</p>

---

## 💡 Why `mybatis-ai-bridge`?

When building Java projects with **MyBatis / MyBatis-Plus** using AI coding assistants like **Cursor**, **Claude Code**, **GitHub Copilot**, or **Windsurf**, developers constantly hit a structural bottleneck:

1. **Physical Separation of Interfaces & SQL**: Java mapper interfaces live in `src/main/java/.../UserMapper.java`, while the actual SQL statements are buried thousands of lines away in `src/main/resources/mapper/UserMapper.xml`.
2. **Context Blindness & Schema Hallucinations**: Either the AI sees the Java interface signature without knowing the underlying SQL, or it pulls bloated multi-thousand-line XML files into the context window—burning massive amounts of tokens and still hallucinating column names.
3. **Silent Drift & Orphaned Code**: `<select>` statements remain in XML long after their Java methods were deleted (orphan SQL), new interface methods miss their XML bindings, or duplicate statement IDs lurk until the application crashes at startup.

**`mybatis-ai-bridge` solves this through pure static analysis** (no application startup, no database connection, zero external service dependencies). It scans your project, **links every Mapper interface method with its XML or annotation SQL**, and condenses everything into a single, ultra-compact, LLM-optimized `MYBATIS_CONTEXT.md`—complete with built-in static diagnostics.

---

## ✨ Key Features

- ⚡️ **AST-Level Bidirectional Linking**: Powered by JavaParser and a fast, XXE-safe DOM parser to accurately match Java interface methods with XML `<select|insert|update|delete>` statements.
- ✂️ **Extreme Token Compression (70%–85% Smaller)**:
  - Strips XML boilerplate, namespaces, redundant attributes, and all XML/SQL comments (while preserving optimizer hints like `/*+ INDEX(...) */`);
  - Automatically inlines `<include refid="..."/>` fragments across files;
  - Keeps dynamic SQL tags (`<where>`, `<if>`, `<foreach>`) intact—LLMs understand them natively;
  - Summarizes verbose `<resultMap>` definitions into single-line `column→property` mappings.
- 🧩 **Full Annotation & Hybrid Support**:
  - Standard XML mappers;
  - Annotation SQL (`@Select`, `@Insert`, `@Update`, `@Delete`), including multi-line string concatenation and string arrays;
  - Dynamic XML scripts in annotations (`@Select("<script>...</script>")`);
  - Runtime SQL Providers (`@SelectProvider`, etc.).
- 🛡️ **MyBatis-Plus & Generic Mapper Aware**: Automatically detects `BaseMapper<T>`, `MPJBaseMapper<T>`, and `tk.mybatis` inheritance so inherited generic CRUD methods don't clutter your business SQL context.
- 🔍 **Built-in Static Linter (`--check`)**:
  - Detects **Unbound Methods** (declared in Java interface, missing in XML/annotations);
  - Detects **Orphan Statements** (defined in XML, no matching method in Java interface);
  - Detects **Duplicate Statement IDs** (across XML files or dual XML + annotation bindings that crash MyBatis at startup);
  - Ships as a single self-contained JAR, ready for CI pipelines or Git pre-commit hooks.

---

## 🚀 Quick Start

### 1. Build the Fat JAR

Clone the repository and package it using the included Maven Wrapper (compatible with Java 8+):

```bash
git clone https://github.com/Libertyzzzz/mybatis-ai-bridge.git
cd mybatis-ai-bridge
./mvnw clean package -DskipTests
```

This produces a standalone executable JAR in `target/mybatis-ai-bridge-0.1.0.jar` (~1.8 MB, all dependencies included).

### 2. Run on Your Java Project

```bash
# Navigate to your Spring Boot / MyBatis project root
cd /path/to/your-java-project

# Scan the project and generate MYBATIS_CONTEXT.md
java -jar /path/to/mybatis-ai-bridge-0.1.0.jar .
```

Example terminal output:
```text
Wrote /path/to/your-java-project/MYBATIS_CONTEXT.md: 18 mappers, 142 statements, ~9400 tokens (sources ~48000 tokens, 80% smaller)
2 issue(s) found — see the Diagnostics section or run with --check
```

### 3. Feed It to Your AI Assistant

Once `MYBATIS_CONTEXT.md` is generated:
- **Cursor / Windsurf**: Reference it directly in chat via `@MYBATIS_CONTEXT.md Help me trace the order status query and add a date range filter`.
- **Claude Code / CLI Agents**: The agent automatically indexes the file and immediately understands every mapper signature, result mapping, and SQL query without hunting through XML directories.

---

## 📊 Before & After

### Before (Scattered across directories & verbose XML)

**`UserMapper.java`**
```java
@Mapper
public interface UserMapper extends BaseMapper<User> {
    List<User> findUsers(@Param("name") String name, @Param("status") Integer status);
}
```

**`UserMapper.xml`** *(buried under `src/main/resources/mapper/`)*
```xml
<mapper namespace="com.example.mapper.UserMapper">
    <sql id="Base_Column_List">id, user_name, status, dept_id, created_at</sql>
    <select id="findUsers" resultMap="BaseResultMap">
        SELECT <include refid="Base_Column_List"/>
        FROM t_user
        <where>
            <if test="name != null and name != ''">
                AND user_name LIKE CONCAT('%', #{name}, '%')
            </if>
        </where>
    </select>
</mapper>
```

### After (`MYBATIS_CONTEXT.md` generated by `mybatis-ai-bridge`)

````markdown
## UserMapper

- namespace: `com.example.mapper.UserMapper`
- interface: `src/main/java/com/example/mapper/UserMapper.java`
- extends: `BaseMapper<User>` — inherited generic CRUD methods are not listed
- xml: `src/main/resources/mapper/UserMapper.xml`

### `List<User> findUsers(@Param("name") String name, @Param("status") Integer status)`

`select` · resultMap=BaseResultMap · xml

```sql
SELECT id, user_name, status, dept_id, created_at FROM t_user
<where>
  <if test="name != null and name != ''">AND user_name LIKE CONCAT('%', #{name}, '%')</if>
</where>
```
````

---

## 🛠️ CLI Options

```text
Usage: mybatis-ai-bridge [-hV] [--check] [--include-tests]
                         [--no-inline-include] [--no-result-maps] [-o=<output>]
                         [-m=NAME[,NAME...]]... [PROJECT_DIR]

Parameters & Options:
      [PROJECT_DIR]         Project root to scan (default: current directory '.').
  -o, --output=<output>     Output file, relative to PROJECT_DIR; '-' for stdout (default: MYBATIS_CONTEXT.md).
  -m, --mapper=NAME[,NAME...]
                            Only include matching mappers (simple-name substring or full namespace). Repeatable.
      --check               Only report diagnostics; exit code 1 if any ERROR/WARN is found (for CI / git hooks).
      --no-inline-include   Keep <include refid=.../> instead of inlining fragments.
      --no-result-maps      Omit resultMap summaries.
      --include-tests       Also scan src/test directories.
  -h, --help                Show this help message and exit.
  -V, --version             Print version information and exit.
```

### Common Examples

```bash
# 1. Export only order- and payment-related mappers
java -jar mybatis-ai-bridge.jar -m order,payment .

# 2. Write directly to stdout and copy to clipboard (macOS)
java -jar mybatis-ai-bridge.jar -o - . | pbcopy

# 3. Run as a linter in CI or a Git pre-commit hook
java -jar mybatis-ai-bridge.jar --check .
```

---

## 🧪 Static Diagnostics Rules

| Level | Issue Type | Description |
| :--- | :--- | :--- |
| **ERROR** | Duplicate Statement ID | Same ID defined twice in XML, or bound in both XML and `@Select`/`@Insert`/etc. (causes MyBatis startup failure). |
| **WARN** | Orphan SQL (`ORPHAN`) | Statement exists in XML, but no matching method exists in the Java interface (dead/legacy SQL). |
| **WARN** | Unbound Method (`UNBOUND`) | Abstract method declared on the Mapper interface without any XML statement or SQL annotation (throws `BindingException` at runtime). |
| **INFO** | Missing Interface Source | Mapper XML found without a local Java interface source file (e.g., interface lives in an external dependency JAR). |

---
---

# 简体中文说明

<p align="center">
  <b>专为 Cursor / Claude Code / Copilot / Windsurf 打造的 MyBatis 紧凑上下文生成与静态诊断工具</b>
</p>

## 💡 为什么需要它？(Why `mybatis-ai-bridge`?)

在日常使用 **Cursor**、**Claude Code**、**GitHub Copilot** 或各类 AI 编程助手开发 Java 项目时，**MyBatis / MyBatis-Plus** 架构常常面临严重的“割裂痛点”：

1. **接口与 SQL 物理隔离**：Java 接口在 `src/main/java/.../UserMapper.java`，而 SQL 语句躺在几千行之外的 `src/main/resources/mapper/UserMapper.xml` 中。
2. **AI 上下文抓瞎与字段幻觉**：AI 读到了 Java 接口签名，却看不到对应的具体 SQL；或者为了看一条 SQL，把几千行的臃肿 XML 塞进上下文窗口，消耗巨额 Token，还容易产生数据库字段名幻觉。
3. **隐蔽失效与孤立代码**：XML 中写了 `<select>`，但 Java 接口方法早被删除（孤立 SQL）；或 Java 接口新加了方法，XML 却漏写了实现，甚至同一 ID 在多处重复定义，只有在项目启动时才会崩溃。

**`mybatis-ai-bridge` 解决的就是这个问题**：
它通过纯静态语法分析（无需启动应用、无需配置数据库连接），**自动扫描并双向链接所有的 Mapper 接口与 SQL 实现**，并将整个模块浓缩为一份极具紧凑度、专为 LLM 优化的 `MYBATIS_CONTEXT.md`，同时自带轻量静态体检。

---

## ✨ 核心特性 (Features)

- ⚡️ **AST 级双向关联**：基于 JavaParser 与安全 DOM 解析器，精准匹配 Java 接口方法与 XML `<select|insert|update|delete>`。
- ✂️ **Token 极致压缩（节省 70%~85%）**：
  - 自动剔除 XML 命名空间、冗余属性与所有 SQL/XML 注释（同时智能保留 `/*+ INDEX(...) */` 等数据库优化器 Hint 及字符串字面量内的 `--`）；
  - 自动将跨文件的 `<include refid="..."/>` 片段直接内联展开；
  - 保留完整的动态 SQL 逻辑标签（`<where>`, `<if>`, `<foreach>`，LLM 能原生理解）；
  - 将冗长的 `<resultMap>` 浓缩为单行 `column→property` 映射摘要。
- 🧩 **全场景注解支持**：
  - 支持标准 XML 映射；
  - 支持注解 SQL（`@Select`, `@Insert`, `@Update`, `@Delete`），支持多行字符串拼接与数组语法；
  - 支持动态 XML 脚本注解（`@Select("<script>...</script>")`）；
  - 支持 SQL Provider（`@SelectProvider` 等）。
- 🛡️ **MyBatis-Plus / 泛型 Mapper 友好**：自动识别 `BaseMapper<T>`、`MPJBaseMapper<T>` 等继承关系，避免重复列出由框架生成的泛型 CRUD，并正确区分 Java `default` 重载方法，让 AI 聚焦在真实业务 SQL 上。
- 🔍 **内置静态体检 (`--check`)**：
  - 检测未绑定 SQL 的接口方法（Unbound Methods）；
  - 检测无对应 Java 接口的幽灵 SQL（Orphan Statements）；
  - 检测同一个 ID 在 XML 和注解中重复定义的启动崩溃冲突；
  - 零外部运行时依赖、单 Jar 分发，可直接作为 Git Hook 或 CI 拦截检查。

---

## 🚀 快速上手 (Quick Start)

### 1. 构建工具包

克隆本仓库并使用内置 Maven Wrapper 构建（兼容 Java 8+）：

```bash
git clone https://github.com/Libertyzzzz/mybatis-ai-bridge.git
cd mybatis-ai-bridge
./mvnw clean package -DskipTests
```

构建完成后将在 `target/` 目录下生成独立的可执行 Jar 包：`mybatis-ai-bridge-0.1.0.jar`（约 1.8MB，包含所有依赖）。

### 2. 在你的 Java 项目中运行

```bash
# 进入你的业务 Spring Boot / MyBatis 项目根目录
cd /path/to/your-java-project

# 运行扫描并生成 AI 上下文文件（默认生成 MYBATIS_CONTEXT.md）
java -jar /path/to/mybatis-ai-bridge-0.1.0.jar .
```

终端输出示例：
```text
Wrote /path/to/your-java-project/MYBATIS_CONTEXT.md: 18 mappers, 142 statements, ~9400 tokens (sources ~48000 tokens, 80% smaller)
2 issue(s) found — see the Diagnostics section or run with --check
```

### 3. 给 AI 助手使用

生成 `MYBATIS_CONTEXT.md` 后，你可以：
- **Cursor / Windsurf**：直接在对话中输入 `@MYBATIS_CONTEXT.md 请帮我排查一下订单状态流转的查询逻辑`；
- **Claude Code / CLI 智能体**：智能体会自动索引该文件，精准获取方法签名、参数注解与 SQL 的关联，再也不用手动去 XML 里翻找。

---

## 🛠️ 命令行参数 (CLI Options)

```text
Usage: mybatis-ai-bridge [-hV] [--check] [--include-tests]
                         [--no-inline-include] [--no-result-maps] [-o=<output>]
                         [-m=NAME[,NAME...]]... [PROJECT_DIR]

参数说明：
  [PROJECT_DIR]         项目根目录路径（默认为当前目录 '.'）
  -o, --output=<path>   输出文件名（默认: MYBATIS_CONTEXT.md；指定 '-' 输出到标准输出）
  -m, --mapper=<name>   仅导出指定的 Mapper（支持简单名称模糊匹配或完整包名）
      --check           仅执行诊断检查模式，如有 ERROR/WARN 则退出码为 1（适用于 CI / Pre-commit）
      --no-inline-include
                        保持 <include refid=.../> 原样，不进行内联展开
      --no-result-maps  在生成的 Markdown 中省略 resultMap 映射清单
      --include-tests   同时扫描 src/test 目录（默认仅扫描生产代码）
  -h, --help            显示帮助信息
  -V, --version         打印版本信息
```

---

## 🧪 静态体检规则 (Diagnostics)

| 级别 | 问题类型 | 说明 |
| :--- | :--- | :--- |
| **ERROR** | 重复 ID 定义 | 同一命名空间下存在重名语句，或同一方法同时配置了 XML 和注解，导致 MyBatis 启动崩溃 |
| **WARN** | 孤立 SQL (`ORPHAN`) | XML 中写了 SQL，但对应的 Java 接口里没有该方法（历史遗留垃圾代码） |
| **WARN** | 未绑定方法 (`UNBOUND`) | Java 接口声明了抽象方法，但没有配置 XML 也无 SQL 注解，调用将抛出 `BindingException` |
| **INFO** | 缺失接口源码 | 仅发现 XML 映射文件，未匹配到本地 Java 接口（可能是公共模块或二方包中的类） |

---

## 🏢 About & Sponsor (关于作者与 NEXTIFY)

Developed and maintained by **cassian**.
- Website: [NEXTIFY (https://nextify.cn)](https://nextify.cn)
- Repository: [GitHub - Libertyzzzz/mybatis-ai-bridge](https://github.com/Libertyzzzz/mybatis-ai-bridge)

Issues and Pull Requests are welcome! If this tool helps your workflow, please give it a ⭐️ **Star**!

---

## 📄 License (开源协议)

Licensed under the [MIT License](LICENSE).
