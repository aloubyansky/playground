package io.quarkus.blend;

import io.quarkus.bootstrap.app.ArtifactResult;

import java.nio.file.Path;
import java.util.List;

public class QuarkusBlend {

    public static QuarkusBlendBuilder builder() {
        return new QuarkusBlendBuilder();
    }

    private final List<ArtifactResult> results;

    QuarkusBlend(List<ArtifactResult> buildResults) {
        this.results = buildResults;
    }

    public Path getExecutable() {
        return results.get(0).getPath();
    }
}
