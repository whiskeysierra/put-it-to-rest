package org.zalando.putittorest;

import org.apache.http.HttpRequestInterceptor;
import org.apache.http.HttpResponseInterceptor;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
class HttpClientFactoryBean implements FactoryBean<HttpClient> {

    private final HttpClientBuilder builder = HttpClientBuilder.create();
    private final RequestConfig.Builder config = RequestConfig.custom();
    private HttpClientCustomizer customizer = $ -> {};

    public void setRequestInterceptors(final List<HttpRequestInterceptor> interceptors) {
        interceptors.forEach(builder::addInterceptorLast);
    }

    public void setResponseInterceptors(final List<HttpResponseInterceptor> interceptors) {
        interceptors.forEach(builder::addInterceptorLast);
    }

    public void setConnectTimeout(final int connectTimeout) {
        config.setConnectTimeout(connectTimeout);
    }

    public void setSocketTimeout(final int socketTimeout) {
        config.setSocketTimeout(socketTimeout);
    }

    public void setCustomizer(final HttpClientCustomizer customizer) {
        this.customizer = customizer;
    }

    @Override
    public CloseableHttpClient getObject() {
        builder.setDefaultRequestConfig(config.build());
        builder.setConnectionTimeToLive(30, TimeUnit.SECONDS);
        customizer.customize(builder);
        return builder.build();
    }

    @Override
    public Class<?> getObjectType() {
        return CloseableHttpClient.class;
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

}
