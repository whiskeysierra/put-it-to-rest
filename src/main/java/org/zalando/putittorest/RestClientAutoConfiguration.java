package org.zalando.putittorest;

import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureOrder;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration
@AutoConfigureOrder(Ordered.LOWEST_PRECEDENCE)
@AutoConfigureAfter(value = JacksonAutoConfiguration.class,
        name = {
                "LogbookAutoConfiguration",
                "TracerAutoConfiguration",
                "ZmonMetricFilterAutoConfiguration"
        })
public class RestClientAutoConfiguration {

    @Bean
    public static RestClientPostProcessor restClientPostProcessor() {
        return new RestClientPostProcessor();
    }

}
