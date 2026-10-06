package com.techo.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.view.RedirectView;

/**
 * 登录、首次设置密码、修改密码、登出。
 *
 * <p>跳转统一用 <b>303 See Other</b> 而不是默认的 302。
 * 303 的语义就是「POST 处理完了，用 GET 去那个地址」，
 * 所有客户端（包括不按 302 惯例走的那些）都会正确地转成 GET。
 */
@Controller
public class AuthController {

    private static final int MIN_PASSWORD = 4;
    private static final int MAX_PASSWORD = 128;

    private final AuthService auth;
    private final boolean enabled;

    public AuthController(AuthService auth, @Value("${techo.auth.enabled:true}") boolean enabled) {
        this.auth = auth;
        this.enabled = enabled;
    }

    // ---------------------------------------------------------------- 登录

    @GetMapping("/login")
    public String loginPage(HttpSession session, Model model) {
        if (!enabled) {
            return "redirect:/todo";
        }
        if (!auth.hasPassword()) {
            return "redirect:/setup";
        }
        if (auth.isLoggedIn(session)) {
            return "redirect:/todo";
        }
        return "login";
    }

    @PostMapping("/login")
    public Object login(@RequestParam(required = false) String password,
                        HttpServletRequest request,
                        HttpSession session,
                        Model model) {

        String ip = request.getRemoteAddr();

        if (auth.isLocked(ip)) {
            model.addAttribute("error", "尝试次数过多，请 5 分钟后再试。");
            return "login";
        }
        if (!auth.verify(password)) {
            auth.recordFailure(ip);
            int left = auth.remainingAttempts(ip);
            model.addAttribute("error", left > 0
                    ? "密码不正确，还可以试 " + left + " 次。"
                    : "密码不正确，已暂时锁定 5 分钟。");
            return "login";
        }

        auth.clearFailures(ip);
        auth.login(session);
        return seeOther("/todo");
    }

    @PostMapping("/logout")
    public Object logout(HttpSession session) {
        auth.logout(session);
        return seeOther("/login");
    }

    // ---------------------------------------------------------------- 首次设置密码

    @GetMapping("/setup")
    public String setupPage() {
        if (!enabled) {
            return "redirect:/todo";
        }
        if (auth.hasPassword()) {
            return "redirect:/login";
        }
        return "setup";
    }

    @PostMapping("/setup")
    public Object setup(@RequestParam(required = false) String password,
                        @RequestParam(required = false) String confirm,
                        HttpSession session,
                        Model model) {

        if (!enabled) {
            return seeOther("/todo");
        }
        if (auth.hasPassword()) {
            return seeOther("/login");
        }

        String error = validate(password, confirm);
        if (error != null) {
            model.addAttribute("error", error);
            return "setup";
        }

        auth.setPassword(password);
        auth.login(session);
        return seeOther("/todo");
    }

    // ---------------------------------------------------------------- 修改密码

    /** htmx 调用，返回安全面板片段，不跳页。 */
    @PostMapping("/auth/password")
    public String changePassword(@RequestParam(required = false) String current,
                                 @RequestParam(required = false) String password,
                                 @RequestParam(required = false) String confirm,
                                 Model model) {

        if (!auth.verify(current)) {
            model.addAttribute("passwordError", "当前密码不正确");
        } else {
            String error = validate(password, confirm);
            if (error != null) {
                model.addAttribute("passwordError", error);
            } else {
                auth.setPassword(password);
                model.addAttribute("passwordMessage", "密码已更新，下次登录请使用新密码。");
            }
        }
        return "fragments/security-panel :: panel";
    }

    // ---------------------------------------------------------------- 工具

    private static String validate(String password, String confirm) {
        if (password == null || password.length() < MIN_PASSWORD) {
            return "密码至少 " + MIN_PASSWORD + " 位";
        }
        if (password.length() > MAX_PASSWORD) {
            return "密码不能超过 " + MAX_PASSWORD + " 位";
        }
        if (!password.equals(confirm)) {
            return "两次输入的密码不一致";
        }
        return null;
    }

    private static RedirectView seeOther(String path) {
        RedirectView view = new RedirectView(path);
        view.setStatusCode(HttpStatus.SEE_OTHER);
        return view;
    }
}
