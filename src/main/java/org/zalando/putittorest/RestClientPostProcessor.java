package org.zalando.putittorest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import com.google.gag.annotation.remark.Hack;
import lombok.SneakyThrows;
import org.apache.http.nio.client.HttpAsyncClient;
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
import org.springframework.http.client.HttpComponentsAsyncClientHttpRequestFactory;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.util.DefaultUriTemplateHandler;
import org.zalando.riptide.Rest;
import org.zalando.stups.oauth2.httpcomponents.AccessTokensRequestInterceptor;
import org.zalando.stups.tokens.AccessTokens;

import javax.annotation.Nullable;
import java.util.List;

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
            @Nullable final OAuth oauth = client.getOauth();
            final Timeouts timeouts = client.getTimeouts();
            final String baseUrl = client.getBaseUrl();

            final String convertersId = registerHttpMessageConverters(id);

            final String asyncFactoryId = registerAsyncClientHttpRequestFactory(id, timeouts, oauth);
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

    private void configureTimeouts(final BeanDefinitionBuilder builder, final Timeouts timeouts) {
        builder.addPropertyValue("connectTimeout", (int) timeouts.getConnectUnit().toMillis(timeouts.getConnect()));
        builder.addPropertyValue("readTimeout", (int) timeouts.getReadUnit().toMillis(timeouts.getRead()));
    }

    private String registerHttpMessageConverters(final String id) {
        return registry.register(id, HttpMessageConverters.class, () ->
                BeanDefinitionBuilder.genericBeanDefinition(HttpMessageConverters.class)
                        .addConstructorArgValue(false)
                        .addConstructorArgValue(Registry.list(
                                BeanDefinitionBuilder.genericBeanDefinition(StringHttpMessageConverter.class)
                                        .addPropertyValue("writeAcceptCharset", false)
                                        .getBeanDefinition(),
                                BeanDefinitionBuilder.genericBeanDefinition(MappingJackson2HttpMessageConverter.class)
                                        .addConstructorArgReference(findObjectMapper(id))
                                        .getBeanDefinition())));
    }

    private String findObjectMapper(final String id) {
        final String beanName = Registry.generateBeanName(id, ObjectMapper.class);
        return registry.isRegistered(beanName) ? beanName : "jacksonObjectMapper";
    }

    private String registerAccessTokens(final RestSettings settings) {
        return registry.register(AccessTokens.class, () -> {
            final BeanDefinitionBuilder builder = BeanDefinitionBuilder.genericBeanDefinition(AccessTokensFactoryBean.class);
            builder.addPropertyValue("settings", settings);
            return builder;
        });
    }

    private String registerRest(final String id, final String factoryId, final String convertersId, @Nullable final String baseUrl) {
        return registry.register(id, Rest.class, () -> {
            final BeanDefinitionBuilder rest = BeanDefinitionBuilder.genericBeanDefinition(Rest.class);
            rest.setFactoryMethod("create");
            rest.addConstructorArgReference(factoryId);

            final AbstractBeanDefinition converters = BeanDefinitionBuilder.genericBeanDefinition()
                    .setFactoryMethod("getConverters")
                    .getBeanDefinition();
            converters.setFactoryBeanName(convertersId);
            rest.addConstructorArgValue(converters);

            final DefaultUriTemplateHandler handler = new DefaultUriTemplateHandler();
            handler.setBaseUrl(baseUrl);

            rest.addConstructorArgValue(handler);
            return rest;
        });
    }

    private String registerAsyncClientHttpRequestFactory(final String id, final Timeouts timeouts, @Nullable final OAuth oauth) {
        return registry.register(id, AsyncClientHttpRequestFactory.class, () -> {
            final BeanDefinitionBuilder factory =
                    BeanDefinitionBuilder.genericBeanDefinition(HttpComponentsAsyncClientHttpRequestFactory.class);
            factory.addConstructorArgReference(registerHttpAsyncClient(id, oauth));
            configureTimeouts(factory, timeouts);
            return factory;
        });
    }

    private String registerHttpAsyncClient(final String id, @Nullable final OAuth oauth) {
        return registry.register(id, HttpAsyncClient.class, () -> {
            final BeanDefinitionBuilder httpClient = BeanDefinitionBuilder.genericBeanDefinition(HttpAsyncClientFactoryBean.class);
            configureInterceptors(httpClient, id, oauth);
            return httpClient;
        });
    }

    @Hack("In order to avoid any runtime dependency")
    private void configureInterceptors(final BeanDefinitionBuilder builder, final String id, @Nullable final OAuth oauth) {
        final List<Object> requestInterceptors = Registry.list();

        if (oauth != null) {
            requestInterceptors.add(BeanDefinitionBuilder.genericBeanDefinition(AccessTokensRequestInterceptor.class)
                    .addConstructorArgValue(id)
                    .addConstructorArgReference(registerAccessTokens(getSettings()))
                    .getBeanDefinition());
        }

        if (registry.isRegistered("tracerHttpRequestInterceptor")) {
            requestInterceptors.add(Registry.ref("tracerHttpRequestInterceptor"));
        }

        builder.addPropertyValue("firstRequestInterceptors", requestInterceptors);

        if (registry.isRegistered("logbookHttpRequestInterceptor")) {
            builder.addPropertyValue("lastRequestInterceptors", Registry.list(Registry.ref("logbookHttpRequestInterceptor")));
        }

        if (registry.isRegistered("logbookHttpResponseInterceptor")) {
            builder.addPropertyValue("lastResponseInterceptors", Registry.list(Registry.ref("logbookHttpResponseInterceptor")));
        }

        if (registry.isRegistered("zmonMetricsWrapper")) {
            builder.addPropertyValue("metricsWrapper", Registry.ref("zmonMetricsWrapper"));
        }
    }

    @Override
    public void postProcessBeanFactory(final ConfigurableListableBeanFactory beanFactory) throws BeansException {
        // nothing to do
    }

}