//DEPS io.quarkus:quarkus-bootstrap-core:999-SNAPSHOT


import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;

import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Model;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.Exclusion;
import org.eclipse.aether.resolution.ArtifactDescriptorResult;

import io.quarkus.bootstrap.resolver.AppModelResolverException;
import io.quarkus.bootstrap.resolver.maven.MavenArtifactResolver;
import io.quarkus.bootstrap.resolver.maven.workspace.ModelUtils;
import io.quarkus.bootstrap.util.IoUtils;

public class Main {

	private static final String BASEDIR = "basedir";
	private static final String MAVEN_CMD_LINE_ARGS = "MAVEN_CMD_LINE_ARGS";
	private static final String POM_XML = "pom.xml";
	private static final String REDHAT_VERSION_SUFFIX = "redhat";

	private static final String OUTPUT_BOM_MODULE_DIR = "productized-only-bom";
	private static final String OUTPUT_RUNTIME_BOM_MODULE_DIR = OUTPUT_BOM_MODULE_DIR + "/runtime";
	private static final String OUTPUT_DEPLOYMENT_BOM_MODULE_DIR = OUTPUT_BOM_MODULE_DIR + "/deployment";

	private static final String PLATFORM_RUNTIME_BOM_PATH = "bom/runtime/pom.xml";
	private static final String PLATFORM_DEPLOYMENT_BOM_PATH = "bom/deployment/pom.xml";

	static enum Arg {

		DEBUG("debug", "d", null, "Enables debug logging"),
		HELP("help", "h", null, "Prints this page"),
		INPUT_BOM_ARTIFACT_ID("input-artifact-id", null, "quarkus-bom", "artifactId of the input BOM"),
		INPUT_BOM_GROUP_ID("input-group-id", null, "io.quarkus", "groupId of the input BOM"),
		INPUT_BOM_VERSION("input-version", null, "0.0.1580390082822.temporary-redhat-00006", "Version of the input BOM"),
		INPUT_DEPLOYMENT_BOM_ARTIFACT_ID("input-deployment-artifact-id", null, null, "artifactId of the input deployment BOM"),
		OUTPUT_BOM_ARTIFACT_ID("output-artifact-id", null, null, "artifactId of the output BOM"),
		OUTPUT_BOM_GROUP_ID("output-group-id", null, "com.redhat.quarkus", "groupId of the output BOM"),
		OUTPUT_BOM_NAME("output-name", null, null, "Name of the output BOM artifact"),
		OUTPUT_BOM_DESCR("output-descr", null, null, "Desription of the output BOM artifact"),
		OUTPUT_BOM_VERSION("output-version", null, null, "Version of the BOM artifact"),
		OUTPUT_DEPLOYMENT_BOM_ARTIFACT_ID("output-deployment-artifact-id", null, null, "artifactId of the output deployment BOM"),
		PLATFORM_HOME("platform-home", null, "", "Platform source code repository location"),
		SETTINGS_XML("settings", "s", "settings.xml", "Maven settings.xml to configure the artifact resolver");

		final String fullName;
		final String shortName;
		final String defaultValue;
		final String descr;

		Arg(String fullName, String shortName, String defaultValue, String descr) {
			this.fullName = fullName;
			this.shortName = shortName;
			this.defaultValue = defaultValue;
			this.descr = descr;
		}

		String get(String[] args) {
			return get(args, false, defaultValue);
		}

		String get(String[] args, boolean required) {
			return get(args, required, defaultValue);
		}

		String get(String[] args, String defaultValue) {
			return get(args, false, defaultValue);
		}

		private String get(String[] args, boolean required, String defaultValue) {
			if(args == null || args.length == 0) {
				return missingArg(required, defaultValue);
			}
			int i = 0;
			while(i < args.length) {
				final String arg = args[i++];
				final int valueOffset = getValueOffset(arg);
				if (valueOffset > 0) {
					if(arg.length() == valueOffset) {
						if(i >= args.length) {
							throw new IllegalArgumentException("Argument " + arg + " is missing value");
						}
						return args[i];
					}
					if(arg.charAt(valueOffset) == '=') {
						final String value = arg.substring(valueOffset + 1);
						if(value.isEmpty()) {
							throw new IllegalArgumentException("Argument " + arg + " is missing value");
						}
						return value;
					}
				}
			}
			return missingArg(required, defaultValue);
		}

		boolean isSet(String[] args) {
			if(args == null || args.length == 0) {
				return false;
			}
			int i = 0;
			while(i < args.length) {
				final String arg = args[i++];
				final int valueOffset = getValueOffset(arg);
				if (valueOffset > 0) {
					return true;
				}
			}
			return false;
		}

		private int getValueOffset(String arg) {
			int valueOffset = -1;
			return fullName != null && (valueOffset = matchesArg(arg, "--", fullName)) > 0
					|| shortName != null && (valueOffset = matchesArg(arg, "-", shortName)) > 0 ? valueOffset : -1;
		}

		private int matchesArg(String arg, String prefix, String name) {
			return arg.startsWith(prefix) && arg.regionMatches(prefix.length(), name, 0, name.length()) ? prefix.length() + name.length() : -1;
		}

		private String missingArg(boolean required, String defaultValue) {
			if (required && defaultValue == null) {
				final StringBuilder buf = new StringBuilder();
				buf.append("Missing required argument ");
				if (fullName != null) {
					buf.append("--").append(fullName);
				}
				if (shortName != null) {
					if (fullName != null) {
						buf.append('/');
					}
					buf.append("-").append(shortName);
				}
				throw new IllegalArgumentException(buf.toString());
			}
			return defaultValue;
		}
	}

	private static String[] args;
	private static Path basedir;
	private static boolean debug;

	private static Path getOrInitBaseDir() {
		if(basedir == null) {
			final String basedirProp = System.getProperty(BASEDIR);
			basedir = Paths.get(basedirProp == null ? "" : basedirProp).normalize().toAbsolutePath();
			if(basedirProp == null) {
				System.setProperty(BASEDIR, basedir.toString());
			}
		}
		return basedir;
	}

	private static Path propagateSettingsXml() {
		final String s = Arg.SETTINGS_XML.get(args);
		final Path p = getOrInitBaseDir().resolve(s).normalize().toAbsolutePath();
		if(!Files.exists(p)) {
			warn("Failed to locate " + p);
		} else {
			debug("Using Maven settings %s", p);
		}
		System.setProperty(MAVEN_CMD_LINE_ARGS, "-s " + s);
		return p;
	}

	static Path getPlatformHome() {
		return Paths.get(Arg.PLATFORM_HOME.get(args)).toAbsolutePath().normalize();
	}

	public static void main(String[] args) throws Exception {

		Main.args = args;
		if(Arg.HELP.isSet(args)) {
			printHelp();
			return;
		}
		debug = Arg.DEBUG.isSet(args);

		propagateSettingsXml();
		final MavenArtifactResolver mvn = MavenArtifactResolver.builder().build();

		final DefaultArtifact inputBomArtifact = getInputBomArtifact();
		final DefaultArtifact outputBomArtifact = getOutputBomArtifact(inputBomArtifact);
		final Model outputBomModel = generateOutputBom(inputBomArtifact, outputBomArtifact, mvn);

		final DefaultArtifact inputDeploymentBomArtifact = getInputDeploymentBomArtifact(inputBomArtifact);
		final DefaultArtifact outputDeploymentBomArtifact = getOutputDeploymentBomArtifact(outputBomArtifact);
		final Model outputDeploymentBomModel = generateOutputBom(inputDeploymentBomArtifact, outputDeploymentBomArtifact, mvn);

		persistOutputBoms(outputBomModel, outputDeploymentBomModel);
	}

	private static void persistOutputBoms(final Model outputBomModel, final Model outputDeploymentBomModel)
			throws IOException {
		final Path platformHome = getPlatformHome();
		debug("Platform repo home: %s", platformHome);
		final Path platformRootPom = platformHome.resolve(POM_XML);
		if(Files.exists(platformRootPom)) {
			// Persist the BOMs as modules in an existing repo
			addBomsAsPlatformModules(outputBomModel, outputDeploymentBomModel, platformRootPom);
		} else {
			// Persist the BOMs as files in a dir
			if(Files.exists(platformHome)) {
				if(!Files.isDirectory(platformHome)) {
					throw new IllegalArgumentException(platformHome + " specified as the platform repo home does not appear to be a directory");
				}
			} else {
				Files.createDirectories(platformHome);
			}
			persistModel(platformHome, outputBomModel);
			persistModel(platformHome, outputDeploymentBomModel);
		}
	}

	private static void addBomsAsPlatformModules(Model outputBomModel, Model outputDeploymentBomModel, Path platformRootPom) throws IOException {
		final Path platformHome = platformRootPom.getParent();

		importAndAlignBuilds(platformHome, outputBomModel, PLATFORM_RUNTIME_BOM_PATH);
		importAndAlignBuilds(platformHome, outputDeploymentBomModel, PLATFORM_DEPLOYMENT_BOM_PATH);

		persistAsModule(platformHome, OUTPUT_RUNTIME_BOM_MODULE_DIR, outputBomModel);
		persistAsModule(platformHome, OUTPUT_DEPLOYMENT_BOM_MODULE_DIR, outputDeploymentBomModel);

		final Path modifiedRootPom = platformHome.resolve("modified-pom.xml");
		try(BufferedReader reader = Files.newBufferedReader(platformRootPom);
				BufferedWriter writer = Files.newBufferedWriter(modifiedRootPom)) {
			String line = reader.readLine();
			boolean addedModules = false;
			while(line != null) {
				writer.write(line);
				writer.newLine();
				if (!addedModules) {
					final int modulesIndex = line.lastIndexOf("<modules>", line.length() - 1);
					if (modulesIndex >= 0) {
						final String offset = line.substring(0, modulesIndex);
						addModule(writer, offset, OUTPUT_RUNTIME_BOM_MODULE_DIR);
						addModule(writer, offset, OUTPUT_DEPLOYMENT_BOM_MODULE_DIR);
						addedModules = true;
					}
				}
				line = reader.readLine();
			}
			if(!addedModules) {
				throw new IllegalStateException("Failed to add the generated BOMs as modules to " + platformRootPom);
			}
		}
		IoUtils.copy(modifiedRootPom, platformRootPom);
		IoUtils.recursiveDelete(modifiedRootPom);
	}

	private static void importAndAlignBuilds(Path platformHome, Model outputBomModel, String referencePom) throws IOException {
		final Path refPom = platformHome.resolve(referencePom);
		if(Files.exists(refPom)) {
			debug("Importing %s:%s:%s into %s", outputBomModel.getGroupId(), outputBomModel.getArtifactId(), outputBomModel.getVersion(), refPom);
			final Model platformBom = ModelUtils.readModel(refPom);
			outputBomModel.setParent(platformBom.getParent());
			outputBomModel.setBuild(platformBom.getBuild());

			int pomContext = 0;
			final Path modifiedPom = refPom.getParent().resolve("modified-pom.xml");
			try(BufferedReader reader = Files.newBufferedReader(refPom);
					BufferedWriter writer = Files.newBufferedWriter(modifiedPom)) {
				String line = reader.readLine();
				String offset = "";
				while(line != null) {
					writer.write(line);
					writer.newLine();
					int index;
					if(pomContext == 0 && (index = line.lastIndexOf("<dependencyManagement>", line.length() - 1)) > 0) {
						++pomContext;
						offset = line.substring(0, index);
					} else if(pomContext == 1 && (index = line.lastIndexOf("<dependencies>", line.length() - 1)) > 0) {
						++pomContext;
						final CharSequence depsOffset = line.subSequence(0, index);
						writer.append(depsOffset);
						writer.append(offset);
						writer.append("<dependency>");
						writer.newLine();

						writeElement(writer, depsOffset, 2, "groupId", outputBomModel.getGroupId());
						writeElement(writer, depsOffset, 2, "artifactId", outputBomModel.getArtifactId());
						writeElement(writer, depsOffset, 2, "version", outputBomModel.getVersion());
						writeElement(writer, depsOffset, 2, "type", outputBomModel.getPackaging());
						writeElement(writer, depsOffset, 2, "scope", "import");

						writer.append(depsOffset);
						writer.append(offset);
						writer.append("</dependency>");
						writer.newLine();
						writer.newLine();
					}
					line = reader.readLine();
				}
			}

			IoUtils.copy(modifiedPom, refPom);
			IoUtils.recursiveDelete(modifiedPom);
		} else {
			warn("Failed to locate reference POM at " + refPom);
		}
	}

	private static void addModule(BufferedWriter writer, String offset, String path) throws IOException {
		writeElement(writer, offset, 2, "module", path);
	}

	private static void writeElement(BufferedWriter writer, CharSequence offset, int depth, String name, String value) throws IOException {
		int i = 0;
		while(i < depth) {
			writer.append(offset);
			++i;
		}
		writer.append('<');
		writer.append(name);
		writer.append('>');
		writer.append(value);
		writer.append("</");
		writer.append(name);
		writer.append('>');
		writer.newLine();
	}

	private static void persistAsModule(final Path platformRepo, String moduleDir, final Model outputBomModel) throws IOException {
		final Path runtimeDir = platformRepo.resolve(moduleDir);
		Files.createDirectories(runtimeDir);
		ModelUtils.persistModel(runtimeDir.resolve(POM_XML), outputBomModel);
	}

	private static Model generateOutputBom(final DefaultArtifact inputBomArtifact,
			final DefaultArtifact outputBomArtifact, final MavenArtifactResolver mvn)
			throws AppModelResolverException, IOException {
		final ArtifactDescriptorResult inputDescr = mvn.resolveDescriptor(inputBomArtifact);
		final Model outputModel = initOutputModel(outputBomArtifact, inputDescr);

		final List<Dependency> originalDeps = inputDescr.getManagedDependencies();

		final DependencyManagement productizedDm = new DependencyManagement();
		outputModel.setDependencyManagement(productizedDm);

		for(Dependency dep : originalDeps) {
			if(dep.getArtifact().getVersion().contains(REDHAT_VERSION_SUFFIX)) {
			    productizedDm.addDependency(toModelDep(dep));
			} else {
				//debug("Non-productized %s", dep);
			}
		}
		return outputModel;
	}

	private static Model initOutputModel(DefaultArtifact outputArtifact, ArtifactDescriptorResult inputDescr) {
		final Model outputModel = new Model();
		outputModel.setModelVersion("4.0.0");
		outputModel.setPackaging("pom");
		outputModel.setGroupId(outputArtifact.getGroupId());
		outputModel.setArtifactId(outputArtifact.getArtifactId());
		outputModel.setVersion(outputArtifact.getVersion());

		final String name = Arg.OUTPUT_BOM_NAME.get(args, null);
		if(name != null) {
			outputModel.setName(name);
		}

		final String descr = Arg.OUTPUT_BOM_DESCR.get(args, null);
		if(descr != null) {
			outputModel.setDescription(descr);
		}

		return outputModel;
	}

	private static DefaultArtifact getInputBomArtifact() {
		final DefaultArtifact inputArtifact = new DefaultArtifact(Arg.INPUT_BOM_GROUP_ID.get(args),
				Arg.INPUT_BOM_ARTIFACT_ID.get(args), "", "pom", Arg.INPUT_BOM_VERSION.get(args, true));
		debug("Input runtime BOM: %s", inputArtifact);
		return inputArtifact;
	}

	private static DefaultArtifact getInputDeploymentBomArtifact(DefaultArtifact bomArtifact) {
		String artifactId = Arg.INPUT_DEPLOYMENT_BOM_ARTIFACT_ID.get(args);
		if(artifactId == null) {
			artifactId = bomArtifact.getArtifactId() + "-deployment";
		}
		final DefaultArtifact inputArtifact = new DefaultArtifact(bomArtifact.getGroupId(), artifactId, "", "pom",
				bomArtifact.getVersion());
		debug("Input deployment BOM: %s", inputArtifact);
		return inputArtifact;
	}

	private static DefaultArtifact getOutputBomArtifact(DefaultArtifact inputBom) {
		final DefaultArtifact outputArtifact = new DefaultArtifact(Arg.OUTPUT_BOM_GROUP_ID.get(args),
				Arg.OUTPUT_BOM_ARTIFACT_ID.get(args, inputBom.getArtifactId()), "", "pom",
				Arg.OUTPUT_BOM_VERSION.get(args, inputBom.getVersion()));
		debug("Output runtime BOM: %s", outputArtifact);
		return outputArtifact;
	}

	private static DefaultArtifact getOutputDeploymentBomArtifact(DefaultArtifact outputBom) {
		String artifactId = Arg.OUTPUT_DEPLOYMENT_BOM_ARTIFACT_ID.get(args);
		if(artifactId == null) {
			artifactId = outputBom.getArtifactId() + "-deployment";
		}
		final DefaultArtifact outputArtifact = new DefaultArtifact(outputBom.getGroupId(), artifactId, "", "pom",
				outputBom.getVersion());
		debug("Output deployment BOM: %s", outputArtifact);
		return outputArtifact;
	}

	private static org.apache.maven.model.Dependency toModelDep(Dependency originalDep) {
		org.apache.maven.model.Dependency targetDep = new org.apache.maven.model.Dependency();
		final Artifact originalArtifact = originalDep.getArtifact();
		targetDep.setGroupId(originalArtifact.getGroupId());
		targetDep.setArtifactId(originalArtifact.getArtifactId());

		final String classifier = originalArtifact.getClassifier();
		if(classifier != null && !classifier.isEmpty()) {
		    targetDep.setClassifier(classifier);
		}
		targetDep.setType(originalArtifact.getExtension());
		final String version = originalArtifact.getVersion();
		if(version == null || version.isEmpty()) {
			throw new IllegalStateException("Dependency " + originalDep + " is missing version");
		}
		targetDep.setVersion(version);
		final String scope = originalDep.getScope();
		if(scope != null && !scope.isEmpty()) {
		    targetDep.setScope(scope);
		}
		if(originalDep.getOptional() != null) {
			targetDep.setOptional(originalDep.isOptional());
		}
		final Collection<Exclusion> originalExclusions = originalDep.getExclusions();
		if(originalExclusions != null && !originalExclusions.isEmpty()) {
			for(Exclusion originalExclusion : originalExclusions) {
				final org.apache.maven.model.Exclusion targetExclusion = new org.apache.maven.model.Exclusion();
				targetExclusion.setGroupId(originalExclusion.getGroupId());
				targetExclusion.setArtifactId(originalExclusion.getArtifactId());
				targetDep.addExclusion(targetExclusion);
			}
		}
		return targetDep;
	}

	private static void persistModel(Path dir, Model model) throws IOException {
		final Path file = dir.resolve(model.getGroupId() + "." + model.getArtifactId() + "-" + model.getVersion() + ".xml");
		debug("Persisting %s:%s:%s to %s", model.getGroupId(), model.getArtifactId(), model.getVersion(), file);
		ModelUtils.persistModel(file, model);
	}

	private static void printHelp() {
		log("Supported arguments:");
		final StringBuilder buf = new StringBuilder();
		for(Arg a : Arg.values()) {
			buf.setLength(0);
			if(a.fullName != null) {
				buf.append("--").append(a.fullName);
			}
			if(a.shortName != null) {
				if(a.fullName != null) {
					buf.append('/');
				}
				buf.append('-').append(a.shortName);
			}
			buf.append(": ").append(a.descr);
			if(a.defaultValue != null) {
				buf.append(" (Default value is ").append(a.defaultValue.isEmpty() ? "''" : a.defaultValue).append(")");
			}
			log("");
			log(buf.toString());
		}
	}

	protected static void warn(String expr, Object... args) {
		log("[WARN] " + String.format(expr, args));
	}

	protected static void warn(Object arg) {
		log("[WARN] " + arg);
	}

	protected static void info(String expr, Object... args) {
		log("[INFO] " + String.format(expr, args));
	}

	protected static void info(Object arg) {
		log("[INFO] " + arg);
	}

	protected static void debug(String expr, Object... args) {
		if(!debug) {
			return;
		}
		log("[DEBUG] " + String.format(expr, args));
	}

	protected static void debug(Object arg) {
		if(!debug) {
			return;
		}
		log("[DEBUG] " + arg);
	}

	protected static void log(String expr, Object... args) {
		log(String.format(expr, args));
	}

	protected static void log(Object msg) {
		System.out.println(msg);
	}
}