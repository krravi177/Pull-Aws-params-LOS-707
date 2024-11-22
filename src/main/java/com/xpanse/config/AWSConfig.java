package com.xpanse.config;

import com.xpanse.service.AWSPropertySource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.PropertySource;

import java.util.Map;

@Configuration
public class AWSConfig {

    private static final Logger logger = LoggerFactory.getLogger(AWSConfig.class);

    @Value("${aws.parameter.prefix}")
    private String awsPrefix;

    @Value("${aws.secrets.prefix}")
    private String awsSecretsPrefix;

    @Value("${aws.secrets.endpoint}")
    private String awsSecretsEndpoint;

    @Bean
    public PropertySource<Map<String, Object>> awsPropertySource() {
        logger.info("Initializing AWSPropertySource with awsPrefix: {}, awsSecretsPrefix: {}, awsSecretsEndpoint: {}",
                awsPrefix, awsSecretsPrefix, awsSecretsEndpoint);
        return new AWSPropertySource(awsPrefix, awsSecretsPrefix, awsSecretsEndpoint);  // Passing three parameters
    }
}
