package com.easymeeting;


import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication(scanBasePackages = {"com.easymeeting"})
@MapperScan(basePackages = {"com.easymeeting.mapper"})
@EnableTransactionManagement
@EnableScheduling
@EnableAsync
public class EasymeetingApplication {
    public static void main(String[] args) {
        SpringApplication.run(EasymeetingApplication.class,args);
    }
}
