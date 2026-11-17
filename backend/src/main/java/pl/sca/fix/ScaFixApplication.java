package pl.sca.fix;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class ScaFixApplication {
    public static void main(String[] args) {
        SpringApplication.run(ScaFixApplication.class, args);
    }
}
