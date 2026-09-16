package com.auth.center.arch;

import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * auth-center 全树源码「禁止内容」硬门禁（构建期强制，{@code mvn test} 违规即失败）。
 *
 * {@code BackendWideGuardTest} 只上溯到 {@code bi-backend} 目录，覆盖不到 {@code auth-center/}；本门禁按源文本扫描
 * {@code auth-center/} 下全部 Java 模块（{@code auth-center-server} + {@code auth-center-sdk}）的 {@code
 * src/main}。零外部依赖（仅 JDK + junit-jupiter），离线可跑，不加载 Spring 上下文。规则与 bi 侧同名门禁逐条对齐（见 {@code
 * ARCHITECTURE.md §3.5}）：
 *
 *   - 禁友商品牌名 —— 代码 / 注释 / 文案不得出现同类产品品牌。
 *   - 禁静默吞异常 —— ① 空 catch 块 {@code catch(...) {}}；② 以 {@code ignored} 命名 catch 参数。两者都须至少
 *       {@code log.trace/debug/warn} 并把参数改名为 {@code e}。
 */
class ForbiddenContentGuardTest {

    /** 友商品牌名（大写专有名词 + Power BI）—— 与 bi 侧门禁清单保持一致。 */
    private static final Pattern BRAND =
            Pattern.compile(
                    "\\b(Tableau|Looker|Vanna|Metabase|Qlik|ThoughtSpot|Sisense|Holistics)\\b|Power ?BI");

    /** 空 catch 块：catch 头之后花括号内只有空白。 */
    private static final Pattern EMPTY_CATCH =
            Pattern.compile("catch\\s*\\([^)]*\\)\\s*\\{\\s*\\}");

    /** 以 {@code ignored} 命名的 catch 参数 —— 静默吞异常的意图信号。 */
    private static final Pattern IGNORED_CATCH =
            Pattern.compile("catch\\s*\\(\\s*[\\w.]+(?:\\s*\\|\\s*[\\w.]+)*\\s+ignored\\s*\\)");

    /** 品牌名扫描覆盖的文本文件后缀（代码 + 配置文案）。 */
    /** 注释行的起始标记 —— 匹配后取其右侧正文,避免 javadoc 起始符自身被当成 markdown 加粗。 */
    private static final Pattern COMMENT_MARKER = Pattern.compile("\\s*(?:/\\*\\*|/\\*|\\*|//)");

    /** 注释里的装饰性 HTML 标记。{@code <pre>} 与 {@code <code>} 有结构意义,不在其列。 */
    private static final Pattern DECORATIVE_TAG =
            Pattern.compile("</?(?:p|b|i|em|strong|ul|ol|li|h[1-6])>");

    /**
     * markdown 加粗的起标记。只认「前面不是 {@code /} 或 {@code *}、后面紧跟字母或数字」的那种,
     * 故路径通配、掩码、乘法,以及讲述标记本身的散文都不命中;不要求同行闭合,跨行的加粗一样拦得住。
     */
    private static final Pattern MD_BOLD = Pattern.compile("(?<![/*])\\*\\*(?=[\\p{L}\\p{N}])");

    private static final List<String> TEXT_SUFFIXES =
            List.of(".java", ".txt", ".sql", ".yml", ".yaml", ".json", ".properties");

    /**
     * 代码 / 注释 / 文案禁出现同类产品品牌名，覆盖 auth-center 全树。
     *
     * @throws IOException 扫描源码失败
     */
    @Test
    void sourceContainsNoCompetitorBrandNamesAuthCenterWide() throws IOException {
        List<String> violations = new ArrayList<>();
        scanMainSources(
                (rel, text) -> {
                    String[] lines = text.split("\n", -1);
                    for (int i = 0; i < lines.length; i++) {
                        Matcher m = BRAND.matcher(lines[i]);
                        if (m.find()) {
                            violations.add(rel + ":" + (i + 1) + " 含友商品牌「" + m.group() + "」");
                        }
                    }
                });
        failIfAny(
                violations, "auth-center 全树禁出现友商品牌名（见 memory: feedback_no_competitor_brand_names）");
    }

    /**
     * 禁静默吞异常（空 catch 块 / {@code ignored} 参数名），覆盖 auth-center 全树。
     *
     * @throws IOException 扫描源码失败
     */
    @Test
    void sourceContainsNoSilentCatchAuthCenterWide() throws IOException {
        List<String> violations = new ArrayList<>();
        scanMainSources(
                (rel, text) -> {
                    if (!rel.endsWith(".java")) {
                        return;
                    }
                    if (EMPTY_CATCH.matcher(text).find()) {
                        violations.add(rel + " 含空 catch 块 {}（静默吞异常）");
                    }
                    if (IGNORED_CATCH.matcher(text).find()) {
                        violations.add(rel + " 含 catch(... ignored)（静默吞异常，应改名 e + log.trace/warn）");
                    }
                });
        failIfAny(violations, "auth-center 全树禁止静默吞异常（见 memory: feedback_no_fallback）");
    }

    /**
     * 违规非空则以排序后的清单失败。
     *
     * @param violations 违规条目
     * @param title 失败标题
     */
    /**
     * 注释里不得出现排版标记。
     *
     * 段落标记不是人写的:google-java-format 会在每个空行段落前自动插入一个,并把中文按列宽硬折。本模块
     * pom 的 {@code <formatJavadoc>false</formatJavadoc>} 一旦被删,下一次 {@code spotless:apply} 就会把
     * 标记插回上千个文件 —— 实测发生过一次。本用例连同 {@link #javadocReflowMustStayOffInSpotless} 一起
     * 把守:一个查症状,一个查成因。
     *
     * @throws IOException 扫描源码失败
     */
    @Test
    void noDecorativeMarkupInComments() throws IOException {
        List<String> violations = new ArrayList<>();
        scanMainSources(
                (rel, text) -> {
                    if (!rel.endsWith(".java")) {
                        return;
                    }
                    String[] lines = text.split("\\n", -1);
                    for (int i = 0; i < lines.length; i++) {
                        Matcher marker = COMMENT_MARKER.matcher(lines[i]);
                        if (!marker.lookingAt()) {
                            continue;
                        }
                        String body = lines[i].substring(marker.end());
                        Matcher tag = DECORATIVE_TAG.matcher(body);
                        if (tag.find()) {
                            violations.add(rel + ":" + (i + 1) + " 注释含装饰标签「" + tag.group() + "」");
                        } else if (MD_BOLD.matcher(body).find()) {
                            violations.add(rel + ":" + (i + 1) + " 注释含 markdown 加粗");
                        }
                    }
                });
        failIfAny(violations, "注释只交事实,不带排版标记");
    }

    /**
     * spotless 不得重排 javadoc —— 重排会插回段落标记并按列宽硬折中文。
     *
     * @throws IOException 读取 pom 失败
     */
    @Test
    void javadocReflowMustStayOffInSpotless() throws IOException {
        List<String> violations = new ArrayList<>();
        Path pom = authCenterRoot().resolve("pom.xml");
        if (!Files.exists(pom)) {
            violations.add("定位不到 auth-center/pom.xml,门禁无法生效");
        } else if (!Files.readString(pom, StandardCharsets.UTF_8)
                .contains("<formatJavadoc>false</formatJavadoc>")) {
            violations.add(
                    "auth-center/pom.xml 的 googleJavaFormat 缺 <formatJavadoc>false</formatJavadoc>");
        }
        failIfAny(violations, "spotless 的 javadoc 重排必须保持关闭");
    }

    private static void failIfAny(List<String> violations, String title) {
        if (!violations.isEmpty()) {
            Collections.sort(violations);
            fail(title + "，违规 " + violations.size() + " 处：\n  " + String.join("\n  ", violations));
        }
    }

    /**
     * 从 {@code user.dir} 向上定位 {@code auth-center} 目录；定位失败时退回 {@code user.dir}。
     *
     * @return auth-center 树根目录
     */
    private static Path authCenterRoot() {
        Path p = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        while (p != null && !"auth-center".equals(String.valueOf(p.getFileName()))) {
            p = p.getParent();
        }
        return p != null ? p : Paths.get(System.getProperty("user.dir")).toAbsolutePath();
    }

    /**
     * 遍历 auth-center 树下全部 Java 模块 {@code src/main}（排除 {@code target}）的文本文件。
     *
     * @param visitor 文件回调（相对路径, 全文）
     * @throws IOException 遍历 / 读取失败
     */
    private static void scanMainSources(TextVisitor visitor) throws IOException {
        Path root = authCenterRoot();
        try (Stream<Path> stream = Files.walk(root)) {
            for (Path p : (Iterable<Path>) stream.filter(Files::isRegularFile)::iterator) {
                String path = p.toString().replace('\\', '/');
                if (!path.contains("/src/main/") || path.contains("/target/")) {
                    continue;
                }
                if (TEXT_SUFFIXES.stream().noneMatch(path::endsWith)) {
                    continue;
                }
                visitor.visit(
                        root.relativize(p).toString().replace('\\', '/'),
                        Files.readString(p, StandardCharsets.UTF_8));
            }
        }
    }

    @FunctionalInterface
    private interface TextVisitor {
        void visit(String relativePath, String text) throws IOException;
    }
}
