package net.armesto.service2;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@Configuration
@EnableAutoConfiguration
@EnableDiscoveryClient
@RestController
public class Application {

    private static final Logger logger = LoggerFactory.getLogger(Application.class);

    @Value("${spring.cloud.consul.discovery.instanceId}")
    private String instanceId;

    @RequestMapping("/users")
    public List<String> home() {
        logger.info(instanceId);
        List<String> users = new ArrayList<String>();
        users.add(0, "Alice");
        users.add(0, "Bob");
        return users;
    }

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

}
