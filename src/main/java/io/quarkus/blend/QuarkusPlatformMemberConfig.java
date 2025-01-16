package io.quarkus.blend;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

public class QuarkusPlatformMemberConfig {

    private String groupId;
    private String artifactId;
    private String version;
    private List<QuarkusExtensionConfig> extensions = new ArrayList<>();

    public String getGroupId() {
        return groupId;
    }

    public QuarkusPlatformMemberConfig setGroupId(String groupId) {
        this.groupId = groupId;
        return this;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public QuarkusPlatformMemberConfig setArtifactId(String artifactId) {
        this.artifactId = artifactId;
        return this;
    }

    public String getVersion() {
        return version;
    }

    public QuarkusPlatformMemberConfig setVersion(String version) {
        this.version = version;
        return this;
    }

    public QuarkusPlatformMemberConfig addExtension(QuarkusExtensionConfig ext) {
        Objects.requireNonNull(ext);
        extensions.add(ext);
        return this;
    }

    public QuarkusPlatformMemberConfig addExtension(String artifactId) {
        return addExtension(new QuarkusExtensionConfig()
                .setArtifactId(artifactId));
    }

    public Collection<QuarkusExtensionConfig> getExtensions() {
        return extensions;
    }
}
