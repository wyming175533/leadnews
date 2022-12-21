package com.heima.aliyun.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author wangyiming
 */
@Data
@ConfigurationProperties(prefix = "aliyun")
public class GreenConfigProperties {
    private String accessKeyId;
    private String secret;
    private String scenes;
}
