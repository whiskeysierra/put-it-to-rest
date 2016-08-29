package org.zalando.putittorest;

import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureOrder;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.zalando.logbook.spring.LogbookAutoConfiguration;
import org.zalando.tracer.spring.TracerAutoConfiguration;

@Configuration
@AutoConfigureOrder(Ordered.LOWEST_PRECEDENCE)
@AutoConfigureAfter(value = {
        JacksonAutoConfiguration.class,
        LogbookAutoConfiguration.class,
        TracerAutoConfiguration.class,
}, name = {
        "ZmonMetricFilterAutoConfiguration"
})
public class RestClientAutoConfiguration {

    @Bean
    public static RestClientPostProcessor restClientPostProcessor() {
        return new RestClientPostProcessor();
    }

}
