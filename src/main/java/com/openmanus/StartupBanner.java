package com.openmanus;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * OpenManusJava 启动Banner显示类
 * 在Spring Boot启动完成后显示额外的系统信息
 */
@Component
public class StartupBanner implements CommandLineRunner {

    @Override
    public void run(String... args) {
        System.out.println();
        System.out.println("╭────────────────────────────────────────────────────────────╮");
        System.out.println("│                     OpenManusJava                          │");
        System.out.println("│                  智能思考系统启动成功!                        │");
        System.out.println("╰────────────────────────────────────────────────────────────╯");
        System.out.println();
        System.out.println("🌐 Web界面: http://localhost:8089");
        System.out.println("📚 API文档: http://localhost:8089/swagger-ui.html");
        System.out.println();
        System.out.println("💡 思考模式:");
        System.out.println("   ⚡ 快思考模式 - 直接执行，高效响应，适合简单任务");
        System.out.println("   🔍 慢思考模式 - 深度思考，适合复杂任务");
        System.out.println("   🤖 自动模式 - 智能选择最佳思考模式");
        System.out.println();
        System.out.println("☕ 基于 Java 21 + Spring Boot 3.2.0 + OpenManus AI Framework");
        System.out.println("╰────────────────────────────────────────────────────────────╯");
    }
} 
