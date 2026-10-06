package com.techo.auth;

import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录鉴权。
 *
 * <p><b>密码只存 BCrypt 哈希</b>，不存明文。虽然这是个局域网单人应用，
 * 但明文密码文件的泄露后果是「全部日记暴露」，而哈希的代价只是一行代码。
 *
 * <p>另外做了一个简单的失败次数限制：同一来源连续错 5 次锁 5 分钟。
 * 不是为了防专业攻击（BCrypt 本身已经足够慢），而是防止有人闲着在同一个 WiFi 下试。
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    public static final String SESSION_KEY = "techo.loggedIn";

    private static final String PASSWORD_KEY = "auth.password-hash";
    private static final int MAX_FAILURES = 5;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(5);

    private final SettingRepository settings;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    /** 失败次数，key 是来源 IP。只放在内存里，重启即清空。 */
    private final Map<String, Attempt> failures = new ConcurrentHashMap<>();

    private record Attempt(int count, Instant lockedUntil) {
    }

    public AuthService(SettingRepository settings) {
        this.settings = settings;
    }

    // ---------------------------------------------------------------- 密码

    public boolean hasPassword() {
        return settings.get(PASSWORD_KEY).filter(v -> !v.isBlank()).isPresent();
    }

    public void setPassword(String rawPassword) {
        settings.put(PASSWORD_KEY, encoder.encode(rawPassword));
        log.info("登录密码已设置");
    }

    public boolean verify(String rawPassword) {
        if (rawPassword == null || rawPassword.isEmpty()) {
            return false;
        }
        return settings.get(PASSWORD_KEY)
                .map(hash -> encoder.matches(rawPassword, hash))
                .orElse(false);
    }

    // ---------------------------------------------------------------- 会话

    public boolean isLoggedIn(HttpSession session) {
        return session != null && Boolean.TRUE.equals(session.getAttribute(SESSION_KEY));
    }

    public void login(HttpSession session) {
        session.setAttribute(SESSION_KEY, Boolean.TRUE);
    }

    public void logout(HttpSession session) {
        if (session != null) {
            session.invalidate();
        }
    }

    // ---------------------------------------------------------------- 失败限制

    public boolean isLocked(String clientIp) {
        Attempt attempt = failures.get(clientIp);
        if (attempt == null || attempt.lockedUntil() == null) {
            return false;
        }
        if (Instant.now().isAfter(attempt.lockedUntil())) {
            failures.remove(clientIp);
            return false;
        }
        return true;
    }

    public void recordFailure(String clientIp) {
        failures.compute(clientIp, (ip, old) -> {
            int count = (old == null ? 0 : old.count()) + 1;
            Instant lockedUntil = count >= MAX_FAILURES ? Instant.now().plus(LOCK_DURATION) : null;
            if (lockedUntil != null) {
                log.warn("来源 {} 连续登录失败 {} 次，已锁定 {} 分钟", ip, count, LOCK_DURATION.toMinutes());
            }
            return new Attempt(count, lockedUntil);
        });
    }

    public void clearFailures(String clientIp) {
        failures.remove(clientIp);
    }

    public int remainingAttempts(String clientIp) {
        Attempt attempt = failures.get(clientIp);
        return attempt == null ? MAX_FAILURES : Math.max(0, MAX_FAILURES - attempt.count());
    }
}
