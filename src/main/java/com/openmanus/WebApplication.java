package com.openmanus;

import com.openmanus.infra.config.OpenManusProperties;
import com.openmanus.infra.config.DotenvLoader;
import com.openmanus.infra.sandbox.SandboxClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;

import java.util.Map;

/**
 * OpenManus Main Application Class
 * OpenManus main application entrypoint using runtime-first AI framework wiring.
 */
@SpringBootApplication(scanBasePackages = "com.openmanus")
@EnableConfigurationProperties(OpenManusProperties.class)
public class WebApplication {

    private static final Logger logger = LoggerFactory.getLogger(WebApplication.class);
    private static final String STARTING_LOG = "Starting OpenManus-Java";
    private static final String STARTED_LOG = "OpenManus-Java started successfully";

    public static void main(String[] args) {
        logger.info(STARTING_LOG);
        DotenvLoader.loadFromWorkingDirectory();
        SpringApplication application = new SpringApplication(WebApplication.class);
        application.setDefaultProperties(Map.of("server.port", "8089"));
        application.run(args);
        logger.info(STARTED_LOG);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        logger.info(STARTING_LOG);
        logger.info(STARTED_LOG);
    }

    /**
     * Configure sandbox client
     */
    @Bean
    public SandboxClient sandboxClient(OpenManusProperties properties) {
        return new SandboxClient(properties);
    }
}
