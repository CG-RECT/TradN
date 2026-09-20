package com.tradn;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@MapperScan("com.tradn.**.mapper")
@SpringBootApplication
public class TradNApplication {
    public static void main(String[] args) {
        SpringApplication.run(TradNApplication.class, args);
    }
}
