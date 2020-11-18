package io.playground.registry.maven;


import io.quarkus.arc.config.ConfigProperties;

@ConfigProperties
public interface QerConfig {

	String groupId();
	
	String version();
	
	String descriptor();
	
	String platforms();
	
	String nonPlatformExtensions();
	
	String streams();
}
