package io.playground.registry.provider;

import io.quarkus.registry.RegistryResolutionException;
import io.quarkus.registry.client.RegistryClient;
import io.quarkus.registry.client.RegistryClientFactory;
import io.quarkus.registry.client.spi.RegistryClientEnvironment;
import io.quarkus.registry.config.RegistryConfig;

public class PlaygroundRegistryClientFactory implements RegistryClientFactory {

	private static PlaygroundRegistryClientFactory instance;
	
	public static PlaygroundRegistryClientFactory getInstance(RegistryClientEnvironment env) {
		return instance == null ? instance = new PlaygroundRegistryClientFactory(env) : instance;
	}
	
	private final RegistryClientEnvironment env;
	
	private PlaygroundRegistryClientFactory(RegistryClientEnvironment env) {
		this.env = env;
	}
	
	@Override
	public RegistryClient buildRegistryClient(RegistryConfig config) throws RegistryResolutionException {
		return new PlaygroundRegistryClient(env, config);
	}
}
