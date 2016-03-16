package net.armesto.service1;

import org.springframework.cloud.netflix.feign.FeignClient;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import java.util.List;

@FeignClient(name = "service2", configuration = FeignConfiguration.class)
public interface Service2Client {
    @GET
    @Path("/users")
    List<String> getUsers();
}