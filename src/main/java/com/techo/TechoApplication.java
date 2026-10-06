package com.techo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 手账应用入口。
 */
@SpringBootApplication
@EnableScheduling   // 数据库快照的定时任务
public class TechoApplication {

    public static void main(String[] args) {
        SpringApplication.run(TechoApplication.class, args);
    }
}
