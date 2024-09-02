package com.comet.opik.infrastructure.auth;

import com.comet.opik.infrastructure.AuthenticationConfig;
import com.comet.opik.infrastructure.OpikConfiguration;
import com.google.common.base.Preconditions;
import com.google.inject.Provides;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import ru.vyarus.dropwizard.guice.module.support.DropwizardAwareModule;
import ru.vyarus.dropwizard.guice.module.yaml.bind.Config;

import java.util.Objects;

public class AuthModule extends DropwizardAwareModule<OpikConfiguration> {

    @Provides
    @Singleton
    public AuthService authService(
            @Config("authentication") AuthenticationConfig config,
            @NonNull Provider<RequestContext> requestContext) {

        if (!config.isEnabled()) {
            return new AuthServiceImpl(requestContext);
        }

        Objects.requireNonNull(config.getUi(),
                "The property authentication.ui.url is required when authentication is enabled");
        Objects.requireNonNull(config.getSdk(),
                "The property authentication.sdk.url is required when authentication is enabled");

        Preconditions.checkArgument(StringUtils.isNotBlank(config.getUi().url()),
                "The property authentication.ui.url must not be blank when authentication is enabled");
        Preconditions.checkArgument(StringUtils.isNotBlank(config.getSdk().url()),
                "The property authentication.sdk.url must not be blank when authentication is enabled");

        return new RemoteAuthService(client(), config.getSdk(), config.getUi(), requestContext);
    }

    public Client client() {
        return ClientBuilder.newClient();
    }

}