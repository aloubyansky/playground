package io.playground.registry.maven.provider;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.StringWriter;

import io.quarkus.registry.catalog.json.JsonCatalogMapperHelper;

public class JsonUtil {

	static String toJson(Object config) {
		final StringWriter writer = new StringWriter();
		try(BufferedWriter b = new BufferedWriter(writer)) {
			JsonCatalogMapperHelper.serialize(config, b);
		} catch (IOException e) {
			throw new RuntimeException("Failed to serialize descriptor", e);
		}
		return writer.getBuffer().toString();
	}
}
