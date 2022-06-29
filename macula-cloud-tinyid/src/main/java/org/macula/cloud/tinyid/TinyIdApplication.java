package org.macula.cloud.tinyid;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.ServletComponentScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * @author du_imba
 */
@EnableAsync
@SpringBootApplication
@ServletComponentScan
@EnableScheduling
public class TinyIdApplication {

    public static void main(String[] args) {
        SpringApplication.run(TinyIdApplication.class, args);
    }
}
