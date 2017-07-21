package org.zalando.putittorest;

import org.springframework.beans.factory.FactoryBean;
import org.zalando.putittorest.RestSettings.Defaults;
import org.zalando.putittorest.RestSettings.GlobalOAuth;
import org.zalando.stups.tokens.AccessTokens;
import org.zalando.stups.tokens.AccessTokensBuilder;
import org.zalando.stups.tokens.JsonFileBackedClientCredentialsProvider;
import org.zalando.stups.tokens.JsonFileBackedUserCredentialsProvider;
import org.zalando.stups.tokens.Tokens;

import javax.annotation.Nullable;
import java.net.URI;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.common.base.Preconditions.checkArgument;

class AccessTokensFactoryBean implements FactoryBean<AccessTokens> {

    private AccessTokensBuilder builder;

    public void setSettings(final RestSettings settings) {
        final Defaults defaults = settings.getDefaults();
        final GlobalOAuth oAuth = settings.getOauth();

        final URI accessTokenUrl = getAccessTokenUrl(oAuth);
        @Nullable final Path directory = oAuth.getCredentialsDirectory();
        final TimeSpan connectionTimeout = firstNonNull(oAuth.getConnectionTimeout(), defaults.getConnectionTimeout());
        final TimeSpan socketTimeout = firstNonNull(oAuth.getSocketTimeout(), defaults.getSocketTimeout());

        this.builder = Tokens.createAccessTokensWithUri(accessTokenUrl)
                .usingClientCredentialsProvider(getClientCredentialsProvider(directory))
                .usingUserCredentialsProvider(getUserCredentialsProvider(directory))
                .schedulingPeriod((int) oAuth.getSchedulingPeriod().getAmount())
                .schedulingTimeUnit(oAuth.getSchedulingPeriod().getUnit())
                .connectTimeout((int) connectionTimeout.to(TimeUnit.MILLISECONDS))
                .socketTimeout((int) socketTimeout.to(TimeUnit.MILLISECONDS));

        settings.getClients().forEach((id, client) -> {
            @Nullable final RestSettings.OAuth clientOAuth = client.getOauth();

            if (clientOAuth == null) {
                return;
            }

            builder.manageToken(id)
                    .addScopesTypeSafe(clientOAuth.getScopes())
                    .done();
        });
    }

    private JsonFileBackedClientCredentialsProvider getClientCredentialsProvider(@Nullable final Path directory) {
        return directory == null ?
                new JsonFileBackedClientCredentialsProvider() :
                new JsonFileBackedClientCredentialsProvider(directory.resolve("client.json").toFile());
    }

    private JsonFileBackedUserCredentialsProvider getUserCredentialsProvider(@Nullable final Path directory) {
        return directory == null ?
                new JsonFileBackedUserCredentialsProvider() :
                new JsonFileBackedUserCredentialsProvider(directory.resolve("user.json").toFile());
    }

    private URI getAccessTokenUrl(final GlobalOAuth oauth) {
        @Nullable final URI accessTokenUrl = oauth.getAccessTokenUrl();

        if (accessTokenUrl == null) {
            @Nullable final String env = System.getenv("ACCESS_TOKEN_URL");
            checkArgument(env != null, "" +
                    "Neither 'rest.oauth.access-token-url' nor 'ACCESS_TOKEN_URL' was set, " +
                    "but at least one client requires OAuth");
            return URI.create(env);
        }

        return accessTokenUrl;
    }

    @Override
    public AccessTokens getObject() {
        return builder.start();
    }

    @Override
    public Class<?> getObjectType() {
        return AccessTokens.class;
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

}
