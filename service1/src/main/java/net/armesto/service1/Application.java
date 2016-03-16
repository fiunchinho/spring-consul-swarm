package net.armesto.service1;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.netflix.feign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.inject.Inject;
import java.util.List;

@Configuration
@EnableAutoConfiguration
@EnableDiscoveryClient
@RestController
@EnableFeignClients
public class Application {

    private static final Logger logger = LoggerFactory.getLogger(Application.class);

    @Inject
    private Service2Client service2;

    @Value("${spring.cloud.consul.discovery.instanceId}")
    private String instanceId;

    @RequestMapping("/users")
    public List<String> home() {
        logger.info(instanceId);
        return service2.getUsers();
    }

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

}