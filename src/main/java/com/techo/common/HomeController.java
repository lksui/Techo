package com.techo.common;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 根路径重定向到待办页。
 *
 * <p>这一条很实际：手机上只需要记 IP，不用记路径。
 */
@Controller
public class HomeController {

    @GetMapping("/")
    public String home() {
        return "redirect:/todo";
    }
}
