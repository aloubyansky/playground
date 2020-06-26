package io.quarkus.bom.decomposer;

import org.apache.maven.model.Model;

import io.quarkus.bootstrap.resolver.maven.workspace.ModelUtils;

public class ReleaseIdFactory {
	public static ReleaseId forModel(Model model) {
		if(Util.getScmOrigin(model) != null) {
			return "HEAD".equals(Util.getScmTag(model))
					? create(ReleaseOrigin.Factory.scmConnection(Util.getScmOrigin(model)), ReleaseVersion.Factory.version(ModelUtils.getVersion(model)))
					: create(ReleaseOrigin.Factory.scmConnection(Util.getScmOrigin(model)), ReleaseVersion.Factory.tag(Util.getScmTag(model)));
		}
		return create(ReleaseOrigin.Factory.ga(ModelUtils.getGroupId(model), model.getArtifactId()), ReleaseVersion.Factory.version(ModelUtils.getVersion(model)));
	}

	public static ReleaseId create(ReleaseOrigin origin, ReleaseVersion version) {
		return new DefaultReleaseId(origin, version);
	}
}