package org.zalando.putittorest;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.zalando.logbook.spring.LogbookAutoConfiguration;
import org.zalando.stups.tokens.AccessTokens;
import org.zalando.tracer.spring.TracerAutoConfiguration;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest
@ActiveProfiles(profiles = "no-oauth", inheritProfiles = false)
@Component
public final class AccessTokensImplicitDisableTest {

    @Configuration
    @ImportAutoConfiguration({
            RestClientAutoConfiguration.class,
            JacksonAutoConfiguration.class,
            LogbookAutoConfiguration.class,
            TracerAutoConfiguration.class
    })
    public static class TestConfiguration {

    }

    @Autowired(required = false)
    private AccessTokens accessTokens;

    @Test
    public void shouldImplicitlyDisable() {
        assertThat(accessTokens, is(nullValue()));
    }

}
