package io.quarkus.bom.decomposer;

import org.eclipse.aether.artifact.Artifact;

public class DecomposedBomReleasesLogger extends NoopDecomposedBomVisitor {

	public class Config {

		private Config() {
		}

		public Config logger(MessageWriter logger) {
			log = logger;
			return this;
		}

		public Config defaultLogLevel(Level level) {
			logLevel = level;
			return this;
		}

		public Config conflictLogLevel(Level level) {
			conflictLogLevel = level;
			return this;
		}

		public DecomposedBomReleasesLogger build() {
			return DecomposedBomReleasesLogger.this;
		}
	}

	public static Config config() {
		return new DecomposedBomReleasesLogger().new Config();
	}

	public static Config config(boolean skipOriginsWithSingleRelease) {
		return new DecomposedBomReleasesLogger(skipOriginsWithSingleRelease).new Config();
	}

	public enum Level {
		DEBUG,
		INFO,
		WARN,
		ERROR
	}

	public DecomposedBomReleasesLogger() {
		super();
	}

	public DecomposedBomReleasesLogger(boolean skipOriginsWithSingleRelease) {
		super(skipOriginsWithSingleRelease);
	}

	private MessageWriter log;
	private Level logLevel = Level.INFO;
	private Level conflictLogLevel;
	private int originCounter;
	private int releaseCounter;
	private int artifactCounter;
	private boolean versionConflict;
	private final StringBuilder buf = new StringBuilder();

	private MessageWriter logger() {
		return log == null ? log = new DefaultMessageWriter() : log;
	}

	private StringBuilder buf() {
		buf.setLength(0);
		return buf;
	}

	@Override
	public void enterBom(Artifact bomArtifact) {
		log(buf().append("Multi Module Project Releases Detected Among The Managed Dependencies of ").append(bomArtifact));
		if(skipOriginsWithSingleRelease) {
			log("(release origins with a single release were filtered out)");
		}
	}

	@Override
	public boolean enterReleaseOrigin(ReleaseOrigin releaseOrigin, int versions) {
		final boolean result = super.enterReleaseOrigin(releaseOrigin, versions);
		if (result) {
			versionConflict = versions > 1;
			++originCounter;
			log(buf().append("Origin: ").append(releaseOrigin));
		}
		return result;
	}

	@Override
	public void leaveReleaseOrigin(ReleaseOrigin releaseOrigin) throws BomDecomposerException {
		super.leaveReleaseOrigin(releaseOrigin);
		versionConflict = false;
	}

	@Override
	public void visitProjectRelease(ProjectRelease release) {
		++releaseCounter;
		log("  " + release.id().version());
		int artifactCounter = 0;
		for (ProjectDependency dep : release.dependencies()) {
			final StringBuilder buf = buf();
			buf.append("    ").append(++artifactCounter).append(") ").append(dep);
			if(dep.isUpdateAvailable()) {
				buf.append(" -> ").append(dep.availableUpdate().artifact().getVersion());
			}
			log(buf);
		}
		this.artifactCounter += artifactCounter;
	}

	@Override
	public void leaveBom() throws BomDecomposerException {
		if (originCounter > 0) {
			log("TOTAL REPORTED");
			log(buf().append("  Release origins:  ").append(originCounter));
			log(buf().append("  Release versions: ").append(releaseCounter));
			log(buf().append("  Artifacts:        ").append(artifactCounter));
		}
		if(conflictLogLevel == Level.ERROR) {
			throw new BomDecomposerException("There have been version conflicts, please refer to the messages logged above");
		}
	}

	private void log(Object msg) {
		if(versionConflict) {
			log(conflictLogLevel == null ? logLevel : conflictLogLevel, msg);
		} else {
		    log(logLevel, msg);
		}
	}

	private void log(Level level, Object msg) {
		switch (level) {
		case DEBUG:
			logger().debug(msg);
			break;
		case INFO:
			logger().info(msg);
			break;
		case ERROR:
			logger().error(msg);
			break;
		default:
			logger().warn(msg);
		}
	}
}
