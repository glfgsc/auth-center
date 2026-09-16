package com.auth.center.security.crypto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@link CryptoService} 密钥文件回退的行为固定 —— 配置里没有主密钥时,密钥从密钥目录取:文件在就读、不在就生成并落盘。
 *
 * 钉住的是「重启后仍能解开上次的密文」这条:密钥若每次启动重生成,已落库的渠道凭据就全部失效,而这一类失效不在启动期报错,只在下一次投递时才暴露。
 */
class CryptoServiceKeyFileTest {

    @TempDir Path keyDir;

    private CryptoService boot() {
        CryptoService svc = new CryptoService("", keyDir.toString());
        svc.init();
        return svc;
    }

    @Test
    void 未配置主密钥时生成密钥文件并可加解密() {
        CryptoService svc = boot();

        assertTrue(Files.exists(keyDir.resolve(CryptoService.KEY_FILE)), "应在密钥目录生成主密钥文件");

        String cipher = svc.encrypt("webhook-secret");
        assertTrue(svc.isEncrypted(cipher));
        assertEquals("webhook-secret", svc.decrypt(cipher));
    }

    @Test
    void 重启后沿用同一密钥文件解得开旧密文() throws IOException {
        Path keyFile = keyDir.resolve(CryptoService.KEY_FILE);

        String cipher = boot().encrypt("webhook-secret");
        String keyAfterFirst = Files.readString(keyFile, StandardCharsets.UTF_8);

        // 第二次启动:密钥文件已在,应读它而不是另生成一把
        CryptoService second = boot();

        assertEquals(
                keyAfterFirst, Files.readString(keyFile, StandardCharsets.UTF_8), "已存在的密钥文件不该被覆盖");
        assertEquals("webhook-secret", second.decrypt(cipher), "重启后须解得开上次的密文");
    }

    @Test
    void 配置里的主密钥优先于密钥文件() {
        String fileKeyCipher = boot().encrypt("same-plaintext");

        CryptoService configured = new CryptoService("explicit-master-key", keyDir.toString());
        configured.init();
        String configuredCipher = configured.encrypt("same-plaintext");

        assertNotEquals(fileKeyCipher, configuredCipher, "两把密钥不该导出同一密文");
        assertEquals("same-plaintext", configured.decrypt(configuredCipher));
    }

    @Test
    void 空密钥文件视同没有并重新生成() throws IOException {
        Path keyFile = keyDir.resolve(CryptoService.KEY_FILE);
        Files.writeString(keyFile, "   ", StandardCharsets.UTF_8);

        CryptoService svc = boot();

        assertFalse(Files.readString(keyFile, StandardCharsets.UTF_8).isBlank(), "空密钥文件应被重新写入");
        assertEquals("v", svc.decrypt(svc.encrypt("v")));
    }

    @Test
    void 密钥目录不可用时不阻断启动只在调用时报错() throws IOException {
        // 拿一个已存在的普通文件当密钥目录, createDirectories 必失败
        Path occupied = keyDir.resolve("occupied");
        Files.writeString(occupied, "x", StandardCharsets.UTF_8);

        CryptoService svc = new CryptoService("", occupied.toString());
        svc.init(); // 启动不受影响 —— 这里不抛才是本例的重点

        assertThrows(IllegalStateException.class, () -> svc.encrypt("v"), "无密钥时须 fail-closed");
    }
}
