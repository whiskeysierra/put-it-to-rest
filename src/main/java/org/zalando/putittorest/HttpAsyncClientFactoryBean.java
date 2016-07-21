package org.zalando.putittorest;

import com.google.gag.annotation.remark.Hack;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.HttpResponseInterceptor;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.apache.http.nio.client.HttpAsyncClient;
import org.springframework.beans.factory.FactoryBean;
import org.zalando.zmon.actuator.metrics.MetricsWrapper;

import java.util.List;

class HttpAsyncClientFactoryBean implements FactoryBean<HttpAsyncClient> {

    private final HttpAsyncClientBuilder builder = HttpAsyncClientBuilder.create();

    private Object metricsWrapper;

    public void setFirstRequestInterceptors(final List<HttpRequestInterceptor> interceptors) {
        interceptors.forEach(builder::addInterceptorFirst);
    }

    public void setLastRequestInterceptors(final List<HttpRequestInterceptor> interceptors) {
        interceptors.forEach(builder::addInterceptorLast);
    }

    public void setLastResponseInterceptors(final List<HttpResponseInterceptor> interceptors) {
        interceptors.forEach(builder::addInterceptorLast);
    }

    @Hack("Hide type to avoid runtime dependency")
    public void setMetricsWrapper(final Object metricsWrapper) {
        this.metricsWrapper = metricsWrapper;
    }

    @Override
    public HttpAsyncClient getObject() {
        // TODO: builder.setConnectionTimeToLive(30, TimeUnit.SECONDS);
        final CloseableHttpAsyncClient client = builder.build();
        if (metricsWrapper != null) {
            return new InstrumentedHttpAsyncClient((MetricsWrapper) metricsWrapper, client);
        }
        return client;
    }

    @Override
    public Class<?> getObjectType() {
        return HttpAsyncClient.class;
    }

    @Override
    public boolean isSingleton() {
        return false;
    }

}
