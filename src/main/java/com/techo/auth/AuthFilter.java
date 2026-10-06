package com.techo.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * 登录拦截。
 *
 * <p>两道关卡：先把没设密码的用户推到设置页，再拦未登录的用户。
 * 静态资源（css / js）必须放行，否则连登录页都渲染不出来。
 *
 * <p><b>htmx 请求不能用 302 重定向</b>：XHR 会静默跟随重定向，
 * 把登录页的 HTML 当成片段塞进列表里，页面就烂了。
 * 改用 htmx 自己的 {@code HX-Redirect} 响应头，浏览器才会整页跳转。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class AuthFilter extends OncePerRequestFilter {

    private static final Set<String> PUBLIC_PATHS = Set.of("/login", "/setup");
    private static final String[] PUBLIC_PREFIXES = {"/css/", "/js/"};

    private final AuthService auth;
    private final boolean enabled;

    public AuthFilter(AuthService auth, @Value("${techo.auth.enabled:true}") boolean enabled) {
        this.auth = auth;
        this.enabled = enabled;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        if (!enabled) {
            chain.doFilter(request, response);
            return;
        }

        String path = request.getRequestURI();
        if (isPublic(path)) {
            chain.doFilter(request, response);
            return;
        }

        // 还没设密码：除了设置页本身，一律推过去
        if (!auth.hasPassword()) {
            deny(request, response, "/setup");
            return;
        }

        if (auth.isLoggedIn(request.getSession(false))) {
            chain.doFilter(request, response);
            return;
        }

        deny(request, response, "/login");
    }

    private void deny(HttpServletRequest request, HttpServletResponse response, String target)
            throws IOException {

        if (isHtmx(request)) {
            response.setHeader("HX-Redirect", target);
            response.setStatus(HttpServletResponse.SC_OK);
            return;
        }
        if (isApi(request)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("text/plain; charset=UTF-8");
            response.getWriter().write("请先登录");
            return;
        }
        response.sendRedirect(target);
    }

    private static boolean isPublic(String path) {
        if (PUBLIC_PATHS.contains(path)) {
            return true;
        }
        for (String prefix : PUBLIC_PREFIXES) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isApi(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/api/");
    }

    private static boolean isHtmx(HttpServletRequest request) {
        return "true".equalsIgnoreCase(request.getHeader("HX-Request"));
    }
}
