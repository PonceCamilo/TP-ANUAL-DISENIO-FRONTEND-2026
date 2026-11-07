package ar.utn.donatrack.frontend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class FrontendApp {

    public static void main(String[] args) {
        SpringApplication.run(FrontendApp.class, args);
    }
}
