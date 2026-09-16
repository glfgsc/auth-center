package com.auth.center.arch;

import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * auth-center 全树单文件篇幅棘轮（构建期强制，{@code mvn test} 超基线即失败）。
 *
 * 规范要求 Service 实现类 ≤800 行。存量超限文件太多，一次清不完，故照 {@code no-explicit-any} 棘轮的范式：把当前
 * 数量钉成基线，只允许往下走。
 *
 * ⚠️ 基线只能调小，绝不能调大。新增超限文件让本测试变红是预期行为 —— 该去拆那个类，而不是抬基线。拆掉存量后同步把基线调小，锁住成果；目标最终降到 0
 * 并把规则升为「一个都不许有」。拆分范式见 {@code ARCHITECTURE.md} 的门面模式：公共接口签名不变、{@code *Impl} 保留为瘦门面，按职责抽
 * {@code @Component} 协作者落 {@code support/} 段。
 *
 * 只扫 {@code src/main/java}:篇幅规则针对生产代码的可读性,表驱动的长测试是合理的。
 */
class FileSizeRatchetTest {

    /** 单个 Java 生产类的行数上限。 */
    private static final int MAX_LINES = 800;

    /**
     * auth-center 树当前超限文件数。
     *
     * 只减不增 —— 见类注释。当前 0 个:auth-center 树整棵干净,任何新增超限文件都会立刻变红。
     */
    private static final int BASELINE = 0;

    @Test
    void javaFileSizeStaysWithinRatchet() throws IOException {
        Path root = authCenterRoot();
        List<String> oversized = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(root)) {
            for (Path p : (Iterable<Path>) stream.filter(Files::isRegularFile)::iterator) {
                String path = p.toString().replace('\\', '/');
                if (!path.endsWith(".java")) {
                    continue;
                }
                if (!path.contains("/src/main/java/") || path.contains("/target/")) {
                    continue;
                }
                long lines = countLines(p);
                if (lines > MAX_LINES) {
                    oversized.add(
                            lines + " 行  " + root.relativize(p).toString().replace('\\', '/'));
                }
            }
        }
        assertRatchet(oversized, BASELINE, "auth-center");
    }

    /**
     * 比对超限清单与基线：超了要求拆分，少了要求同步下调基线（否则改进不会被锁住）。
     *
     * @param oversized 超限文件清单（已含行数前缀）
     * @param baseline 当前基线
     * @param tree 树名（用于文案）
     */
    static void assertRatchet(List<String> oversized, int baseline, String tree) {
        if (oversized.size() == baseline) {
            return;
        }
        oversized.sort(Comparator.reverseOrder());
        String detail = String.join("\n  ", oversized);
        if (oversized.size() > baseline) {
            fail(
                    tree
                            + " 超限文件 "
                            + oversized.size()
                            + " 个 > 基线 "
                            + baseline
                            + "。新增超限文件请按职责拆分，不要抬基线"
                            + "（见 memory: feedback_file_size_limits）：\n  "
                            + detail);
        }
        fail(
                tree
                        + " 超限文件已降到 "
                        + oversized.size()
                        + " 个（基线 "
                        + baseline
                        + "）—— 请把 FileSizeRatchetTest.BASELINE 同步调小，锁住成果。当前清单：\n  "
                        + detail);
    }

    /**
     * 统计文件行数。
     *
     * @param file 目标文件
     * @return 行数
     * @throws IOException 读取失败
     */
    static long countLines(Path file) throws IOException {
        try (Stream<String> lines = Files.lines(file, StandardCharsets.UTF_8)) {
            return lines.count();
        }
    }

    /** 从 {@code user.dir} 向上定位 {@code auth-center} 目录；定位失败时退回 {@code user.dir}。 */
    private static Path authCenterRoot() {
        Path p = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        while (p != null && !"auth-center".equals(String.valueOf(p.getFileName()))) {
            p = p.getParent();
        }
        return p != null ? p : Paths.get(System.getProperty("user.dir")).toAbsolutePath();
    }
}
