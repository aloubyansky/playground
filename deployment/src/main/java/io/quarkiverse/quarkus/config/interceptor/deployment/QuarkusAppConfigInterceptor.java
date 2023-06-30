package io.quarkiverse.quarkus.config.interceptor.deployment;

import static io.smallrye.config.SecretKeys.doLocked;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.annotation.Priority;

import io.smallrye.config.ConfigSourceInterceptor;
import io.smallrye.config.ConfigSourceInterceptorContext;
import io.smallrye.config.ConfigValue;
import io.smallrye.config.Priorities;

@Priority(Priorities.APPLICATION)
public class QuarkusAppConfigInterceptor implements ConfigSourceInterceptor {

    public static final Map<String, String> collectedProperties = new ConcurrentHashMap<String, String>();

    @Override
    public ConfigValue getValue(ConfigSourceInterceptorContext context, String name) {
        final ConfigValue configValue = doLocked(() -> context.proceed(name));
        collectedProperties.put(name, configValue == null ? "NULL" : configValue.getValue());
        return configValue;
    }
}
