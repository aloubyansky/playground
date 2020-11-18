package io.playground.registry.provider;

import io.quarkus.registry.client.RegistryClientFactory;
import io.quarkus.registry.client.spi.RegistryClientEnvironment;
import io.quarkus.registry.client.spi.RegistryClientFactoryProvider;

public class PlaygroundRegistryClientFactoryProvider implements RegistryClientFactoryProvider {

	@Override
	public RegistryClientFactory newRegistryClientFactory(RegistryClientEnvironment env) {
		return PlaygroundRegistryClientFactory.getInstance(env);
	}
}
