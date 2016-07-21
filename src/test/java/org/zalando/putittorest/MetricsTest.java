package org.zalando.putittorest;

import com.codahale.metrics.MetricRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.zalando.putittorest.annotation.RestClient;
import org.zalando.riptide.Rest;
import org.zalando.zmon.actuator.config.ZmonMetricsAutoConfiguration;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(MetricsTest.TestConfiguration.class)
public class MetricsTest {

    @Configuration
    @Import({ZmonMetricsAutoConfiguration.class})
    static class TestConfiguration extends DefaultTestConfiguration {

        @Bean
        public MetricRegistry metricRegistry() {
            return mock(MetricRegistry.class);
        }

    }

    @RestClient("example")
    private Rest exampleRest;

    @Test
    public void shouldWireCorrectly() throws Exception {
        assertThat(exampleRest, is(notNullValue()));
    }

}
