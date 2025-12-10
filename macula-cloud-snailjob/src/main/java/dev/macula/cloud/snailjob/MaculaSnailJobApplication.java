package dev.macula.cloud.snailjob;

import com.aizuda.snailjob.server.SnailJobServerApplication;
import com.aizuda.snailjob.server.common.rpc.server.grpc.GrpcServer;
import lombok.Generated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.ServletContextInitializer;
import org.springframework.boot.web.servlet.server.ServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.web.client.RestTemplate;

import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * {@code MaculaSnailJobApplication} is SnailJob服务端启动类
 *
 * @author Rain
 * @since 2025/12/10 10:38
 */
@SpringBootApplication(
    scanBasePackages = {"com.aizuda.snailjob.server.starter.*"}
)
@EnableTransactionManagement(
    proxyTargetClass = true
)
public class MaculaSnailJobApplication {
    @Generated
    private static final Logger log = LoggerFactory.getLogger(MaculaSnailJobApplication.class);

    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));
        SpringApplication.run(SnailJobServerApplication.class, args);
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Bean
    public ApplicationRunner nettyStartupChecker(GrpcServer grpcServer, ServletWebServerFactory serverFactory) {
        return (args) -> {
            boolean started = grpcServer.isStarted();

            for (int waitCount = 0; !started && waitCount < 100; started = grpcServer.isStarted()) {
                log.info("--------> snail-job server is staring....");
                TimeUnit.MILLISECONDS.sleep(100L);
                ++waitCount;
            }

            if (!started) {
                log.error("--------> snail-job server startup failure.");
                serverFactory.getWebServer(new ServletContextInitializer[0]).stop();
                SpringApplication.exit(SpringApplication.run(SnailJobServerApplication.class, new String[0]),
                    new ExitCodeGenerator[0]);
            }

        };
    }
}
