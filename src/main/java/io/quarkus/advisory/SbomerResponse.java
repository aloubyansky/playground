package io.quarkus.advisory;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_DEFAULT)
public class SbomerResponse {

    private static volatile ObjectMapper mapper;

    public static ObjectMapper getMapper() {
        if (mapper == null) {
            ObjectMapper om = new ObjectMapper();
            om.enable(SerializationFeature.INDENT_OUTPUT);
            om.enable(JsonParser.Feature.ALLOW_COMMENTS);
            om.enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY);
            om.setPropertyNamingStrategy(PropertyNamingStrategies.KEBAB_CASE);
            om.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            mapper = om;
        }
        return mapper;
    }

    public static SbomerResponse deserialize(Path p) {
        try (BufferedReader reader = Files.newBufferedReader(p)) {
            return getMapper().readValue(reader, SbomerResponse.class);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to deserialize " + p, e);
        }
    }

    public static SbomerResponse deserialize(InputStream is) {
        try (InputStream stream = is) {
            return getMapper().readValue(stream, SbomerResponse.class);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to deserialize SBOMer result", e);
        }
    }

    public static void serialize(Object catalog, Path p) throws IOException {
        serialize(getMapper(), catalog, p);
    }

    public static void serialize(ObjectMapper mapper, Object catalog, Path p) throws IOException {
        final Path parent = p.getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }
        try (BufferedWriter writer = Files.newBufferedWriter(p)) {
            serialize(mapper, catalog, writer);
        }
    }

    public static void serialize(ObjectMapper mapper, Object catalog, Writer writer) throws IOException {
        mapper.writeValue(writer, catalog);
    }

    private List<Content> content;
    private Map<String, Object> any;

    public List<Content> getContent() {
        return content;
    }

    public void setContent(List<Content> content) {
        this.content = content;
    }

    public Map<String, Object> getAny() {
        return any;
    }

    @JsonAnySetter
    public void setAny(Map<String, Object> any) {
        this.any = any;
    }

    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    public static class Content {

        private JsonNode sbom;
        private Map<String, Object> any;

        public JsonNode getSbom() {
            return sbom;
        }

        public void setSbom(JsonNode bom) {
            this.sbom = bom;
        }

        public Map<String, Object> getAny() {
            return any;
        }

        @JsonAnySetter
        public void setAny(Map<String, Object> any) {
            this.any = any;
        }
    }
}
