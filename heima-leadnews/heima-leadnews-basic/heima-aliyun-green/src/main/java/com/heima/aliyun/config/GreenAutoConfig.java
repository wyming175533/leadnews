package com.heima.aliyun.config;

import com.heima.aliyun.template.GreenImageScan;
import com.heima.aliyun.template.GreenTextScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(GreenConfigProperties.class)
public class GreenAutoConfig {

    @Bean
    public GreenImageScan greenImageScan(GreenConfigProperties greenConfigProperties){
        return new GreenImageScan(greenConfigProperties);
    }

    @Bean
    public GreenTextScan greenTextScan(GreenConfigProperties greenConfigProperties){
        return new GreenTextScan(greenConfigProperties);
    }
}
