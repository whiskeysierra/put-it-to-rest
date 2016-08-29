package org.zalando.putittorest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import lombok.SneakyThrows;
import org.apache.http.client.HttpClient;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.boot.autoconfigure.web.HttpMessageConverters;
import org.springframework.boot.bind.PropertiesConfigurationFactory;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.http.client.AsyncClientHttpRequestFactory;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.scheduling.concurrent.ConcurrentTaskExecutor;
import org.springframework.web.util.DefaultUriTemplateHandler;
import org.springframework.web.util.UriTemplateHandler;
import org.zalando.putittorest.zmon.ZmonRequestInterceptor;
import org.zalando.putittorest.zmon.ZmonResponseInterceptor;
import org.zalando.riptide.Rest;
import org.zalando.riptide.httpclient.RestAsyncClientHttpRequestFactory;
import org.zalando.riptide.stream.Streams;
import org.zalando.stups.oauth2.httpcomponents.AccessTokensRequestInterceptor;
import org.zalando.stups.tokens.AccessTokens;
import org.zalando.tracer.concurrent.TracingExecutors;

import javax.annotation.Nullable;
import java.util.List;
import java.util.concurrent.Executors;

import static org.springframework.beans.factory.support.BeanDefinitionBuilder.genericBeanDefinition;
import static org.zalando.putittorest.Registry.generateBeanName;
import static org.zalando.putittorest.Registry.list;
import static org.zalando.putittorest.Registry.ref;

public class RestClientPostProcessor implements BeanDefinitionRegistryPostProcessor, EnvironmentAware {

    private ConfigurableEnvironment environment;
    private Registry registry;
    private RestSettings settings;

    @Override
    public void setEnvironment(final Environment environment) {
        // TODO under which circumstances can this be something else?
        this.environment = (ConfigurableEnvironment) environment;
    }

    @Override
    public void postProcessBeanDefinitionRegistry(final BeanDefinitionRegistry beanDefinitionRegistry) throws BeansException {
        this.registry = new Registry(beanDefinitionRegistry);

        getSettings().getClients().forEach((id, client) -> {
            final String baseUrl = client.getBaseUrl();

            final String convertersId = registerHttpMessageConverters(id);

            final String asyncFactoryId = registerAsyncClientHttpRequestFactory(id, client);
            registerRest(id, asyncFactoryId, convertersId, baseUrl);
        });
    }

    @VisibleForTesting
    @SneakyThrows
    RestSettings getSettings() {
        if (settings == null) {
            final PropertiesConfigurationFactory<RestSettings> factory =
                    new PropertiesConfigurationFactory<>(RestSettings.class);

            factory.setTargetName("rest");
            factory.setPropertySources(environment.getPropertySources());
            factory.setConversionService(environment.getConversionService());

            settings = factory.getObject();
        }
        return settings;
    }

    private String registerHttpMessageConverters(final String id) {
        return registry.register(id, HttpMessageConverters.class, () -> {
            final List<Object> list = list();

            list.add(genericBeanDefinition(StringHttpMessageConverter.class)
                    .addPropertyValue("writeAcceptCharset", false)
                    .getBeanDefinition());

            final String objectMapperId = findObjectMapper(id);

            list.add(genericBeanDefinition(MappingJackson2HttpMessageConverter.class)
                    .addConstructorArgReference(objectMapperId)
                    .getBeanDefinition());

            list.add(genericBeanDefinition(Streams.class)
                    .setFactoryMethod("streamConverter")
                    .addConstructorArgReference(objectMapperId)
                    .getBeanDefinition());

            return BeanDefinitionBuilder.genericBeanDefinition(ClientHttpMessageConverters.class)
                    .addConstructorArgValue(list);
        });
    }

    private String findObjectMapper(final String id) {
        final String beanName = Registry.generateBeanName(id, ObjectMapper.class);
        return registry.isRegistered(beanName) ? beanName : "jacksonObjectMapper";
    }

    private String registerAccessTokens(final RestSettings settings) {
        return registry.register(AccessTokens.class, () -> {
            final BeanDefinitionBuilder builder = genericBeanDefinition(AccessTokensFactoryBean.class);
            builder.addPropertyValue("settings", settings);
            return builder;
        });
    }

    private String registerRest(final String id, final String factoryId, final String convertersId, @Nullable final String baseUrl) {
        return registry.register(id, Rest.class, () -> {
            final BeanDefinitionBuilder rest = genericBeanDefinition(Rest.class);
            rest.setFactoryMethod("create");
            rest.addConstructorArgReference(factoryId);

            final AbstractBeanDefinition converters = BeanDefinitionBuilder.genericBeanDefinition()
                    .setFactoryMethod("getConverters")
                    .getBeanDefinition();
            converters.setFactoryBeanName(convertersId);
            rest.addConstructorArgValue(converters);

            rest.addConstructorArgValue(newUriTemplateHandler(baseUrl));
            return rest;
        });
    }

    private UriTemplateHandler newUriTemplateHandler(@Nullable final String baseUrl) {
        final DefaultUriTemplateHandler handler = new DefaultUriTemplateHandler();
        handler.setBaseUrl(baseUrl);
        return handler;
    }

    private String registerAsyncClientHttpRequestFactory(final String id, final Client client) {
        return registry.register(id, AsyncClientHttpRequestFactory.class, () -> {
            final BeanDefinitionBuilder factory =
                    genericBeanDefinition(RestAsyncClientHttpRequestFactory.class);

            factory.addConstructorArgReference(registerHttpClient(id, client));
            factory.addConstructorArgValue(genericBeanDefinition(ConcurrentTaskExecutor.class)
                    .addConstructorArgValue(BeanDefinitionBuilder.genericBeanDefinition(TracingExecutors.class)
                            .setFactoryMethod("preserve")
                            .addConstructorArgValue(genericBeanDefinition(Executors.class)
                                    .setFactoryMethod("newCachedThreadPool")
                                    .setDestroyMethodName("shutdown")
                                    .getBeanDefinition())
                            .addConstructorArgReference("tracer")
                            .getBeanDefinition())
                    .getBeanDefinition());

            return factory;
        });
    }

    private String registerHttpClient(final String id, final Client client) {
        return registry.register(id, HttpClient.class, () -> {
            final BeanDefinitionBuilder httpClient = genericBeanDefinition(HttpClientFactoryBean.class);
            configureTimeouts(httpClient, client.getTimeouts());
            configureInterceptors(httpClient, id, client.getOauth());

            final String customizerId = generateBeanName(id, HttpClientCustomizer.class);
            if (registry.isRegistered(customizerId)) {
                httpClient.addPropertyReference("customizer", customizerId);
            }

            return httpClient;
        });
    }

    private void configureTimeouts(final BeanDefinitionBuilder builder, final Timeouts timeouts) {
        builder.addPropertyValue("connectTimeout", (int) timeouts.getConnectUnit().toMillis(timeouts.getConnect()));
        builder.addPropertyValue("socketTimeout", (int) timeouts.getReadUnit().toMillis(timeouts.getRead()));
    }

    private void configureInterceptors(final BeanDefinitionBuilder builder, final String id, @Nullable final OAuth oauth) {
        final List<Object> requestInterceptors = list();
        final List<Object> responseInterceptors = list();

        if (oauth != null) {
            requestInterceptors.add(genericBeanDefinition(AccessTokensRequestInterceptor.class)
                    .addConstructorArgValue(id)
                    .addConstructorArgReference(registerAccessTokens(getSettings()))
                    .getBeanDefinition());
        }

        requestInterceptors.add(ref("tracerHttpRequestInterceptor"));

        if (registry.isRegistered("zmonMetricsWrapper")) {
            requestInterceptors.add(genericBeanDefinition(ZmonRequestInterceptor.class).getBeanDefinition());
            responseInterceptors.add(genericBeanDefinition(ZmonResponseInterceptor.class)
                    .addConstructorArgValue(ref("zmonMetricsWrapper"))
                    .getBeanDefinition());
        }

        requestInterceptors.add(ref("logbookHttpRequestInterceptor"));
        responseInterceptors.add(ref("logbookHttpResponseInterceptor"));

        builder.addPropertyValue("requestInterceptors", requestInterceptors);
        builder.addPropertyValue("responseInterceptors", responseInterceptors);
    }

    @Override
    public void postProcessBeanFactory(final ConfigurableListableBeanFactory beanFactory) throws BeansException {
        // nothing to do
    }

}