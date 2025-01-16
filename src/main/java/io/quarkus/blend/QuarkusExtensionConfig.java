package io.quarkus.blend;

public class QuarkusExtensionConfig {

    private String groupId;
    private String artifactId;
    private String version;

    public String getGroupId() {
        return groupId;
    }

    public QuarkusExtensionConfig setGroupId(String groupId) {
        this.groupId = groupId;
        return this;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public QuarkusExtensionConfig setArtifactId(String artifactId) {
        this.artifactId = artifactId;
        return this;
    }

    public String getVersion() {
        return version;
    }

    public QuarkusExtensionConfig setVersion(String version) {
        this.version = version;
        return this;
    }
}
