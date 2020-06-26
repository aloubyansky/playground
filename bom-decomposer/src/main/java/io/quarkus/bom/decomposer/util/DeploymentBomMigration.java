package io.quarkus.bom.decomposer.util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;

import io.quarkus.bootstrap.model.AppArtifactCoords;
import io.quarkus.bootstrap.resolver.maven.workspace.ModelUtils;

public class DeploymentBomMigration {

	public static void main(String[] args) throws Exception {

		final Path bomDir = Paths.get(System.getProperty("user.home")).resolve("git").resolve("quarkus").resolve("bom");
		final Path deploymentBomXml = bomDir.resolve("deployment").resolve("pom.xml");
		if(!Files.exists(deploymentBomXml)) {
			throw new IllegalStateException("Failed to locate the deployment BOM at " + deploymentBomXml);
		}
		final Model deploymentModel = ModelUtils.readModel(deploymentBomXml);
		final LinkedList<String> deploymentBomLines = readAllLines(deploymentBomXml);

		final Path runtimeBomXml = bomDir.resolve("runtime").resolve("pom.xml");
		if(!Files.exists(runtimeBomXml)) {
			throw new IllegalStateException("Failed to locate the runtime BOM at " + runtimeBomXml);
		}
		final Model runtimeModel = ModelUtils.readModel(runtimeBomXml);
		final LinkedList<String> runtimeBomLines = readAllLines(runtimeBomXml);

		final Set<AppArtifactCoords> runtimeDeps = runtimeModel.getDependencyManagement().getDependencies().stream().map(d -> toAppCoords(d)).collect(Collectors.toSet());
		final List<AppArtifactCoords> deploymentDeps = deploymentModel.getDependencyManagement().getDependencies().stream().map(d -> toAppCoords(d)).collect(Collectors.toList());
		for(Dependency deploymentDep : deploymentModel.getDependencyManagement().getDependencies()) {
			final AppArtifactCoords deploymentCoords = toAppCoords(deploymentDep);
			boolean removeFromBom = false;
			if("test-jar".equals(deploymentDep.getType())) {
				//log("TEST-JAR " + toString(deploymentDep));
			} else if("test".equals(deploymentDep.getScope())) {
				//log("TEST SCOPE " + toString(deploymentDep));
			} else if(deploymentDep.getArtifactId().endsWith("-deployment")) {
				final String rtArtifactId = deploymentDep.getArtifactId().substring(0, deploymentDep.getArtifactId().length() - "-deployment".length());
				if(runtimeDeps.contains(new AppArtifactCoords(deploymentDep.getGroupId(), rtArtifactId, "", "jar", deploymentDep.getVersion()))) {
					migrateDependency(deploymentDep, rtArtifactId, runtimeBomLines, runtimeBomXml);
					removeFromBom = true;
				} else {
					log("DEPLOYMENT " + toString(deploymentDep));
					log("  no corresponding runtime artifact");
				}
			} else if(runtimeDeps.contains(deploymentCoords)) {
				removeFromBom = true;
			} else if(deploymentDep.getArtifactId().equals("quarkus-test-common")
					|| deploymentDep.getArtifactId().equals("quarkus-junit5-internal")) {
				migrateDependency(deploymentDep, "quarkus-junit5", runtimeBomLines, runtimeBomXml);
				removeFromBom = true;

			}

			if(removeFromBom) {
				deploymentDeps.remove(deploymentCoords);
				final String deploymentDepPhrase = "<artifactId>" + deploymentDep.getArtifactId() + "</artifactId>";
				int i = 0;
				while(i < deploymentBomLines.size()) {
					String line = deploymentBomLines.get(i++);
					if(line.contains(deploymentDepPhrase)) {
						break;
					}
				}
				if(i == deploymentBomLines.size()) {
					throw new IllegalStateException("Failed to locate " + deploymentDep.getArtifactId() + " in " + deploymentBomXml);
				}
				int depStart = i;
				while(depStart > 0 && !deploymentBomLines.get(--depStart).contains("<dependency>")) {
				}
				int depEnd = i;
				while(depEnd < deploymentBomLines.size() && !deploymentBomLines.get(++depEnd).contains("</dependency>")) {
				}
				for(int j = 0; j <= depEnd - depStart; ++j) {
					deploymentBomLines.remove(depStart);
				}
			}
		}

		if(!deploymentModel.getProperties().isEmpty()) {
			int i = 0;
			while(i < runtimeBomLines.size() && !runtimeBomLines.get(++i).contains("</properties>")) {
			}
			if(runtimeBomLines.size() == i) {
				throw new IllegalArgumentException("Failed to locate properties in " + runtimeBomXml);
			}
			final String lastProp = runtimeBomLines.get(i - 1);
			final String offset = lastProp.substring(0, lastProp.indexOf('<'));
			for(Map.Entry<?, ?> prop : deploymentModel.getProperties().entrySet()) {
				runtimeBomLines.add(i++, offset + "<" + prop.getKey() + ">" + prop.getValue() + "</" + prop.getKey() + ">");
			}

			int p = 0;
			while(p < deploymentBomLines.size() && !deploymentBomLines.get(++p).contains("<properties>")) {
			}
			if(p == deploymentBomLines.size()) {
				throw new IllegalStateException("Failed to locate properties in " + deploymentBomXml);
			}
			while(true) {
				final String line = deploymentBomLines.remove(p);
				if(line.contains("</properties>")) {
					break;
				}
			}
		}

		final Path buildPomXml = bomDir.getParent().resolve("build-parent").resolve("pom.xml");
		if(!Files.exists(buildPomXml)) {
			throw new IllegalStateException("Failed to locate " + buildPomXml);
		}
		LinkedList<String> buildPomLines = readAllLines(buildPomXml);

		AtomicInteger buildPomEndDepsTag = new AtomicInteger(0);
		while(buildPomEndDepsTag.intValue() < buildPomLines.size() && !buildPomLines.get(buildPomEndDepsTag.incrementAndGet()).contains("</dependencies>")) {
		}
		if(buildPomLines.size() == buildPomEndDepsTag.intValue()) {
			throw new IllegalArgumentException("Failed to locate properties in " + buildPomXml);
		}

		AtomicInteger rtBomEndDepsTag = new AtomicInteger(0);
		while(rtBomEndDepsTag.intValue() < runtimeBomLines.size() && !runtimeBomLines.get(rtBomEndDepsTag.incrementAndGet()).contains("</dependencies>")) {
		}
		if(runtimeBomLines.size() == rtBomEndDepsTag.intValue()) {
			throw new IllegalArgumentException("Failed to locate properties in " + runtimeBomXml);
		}
		runtimeBomLines.add(rtBomEndDepsTag.getAndIncrement(), "");
		runtimeBomLines.add(rtBomEndDepsTag.getAndIncrement(), "            <!-- Quarkus build related dependencies -->");


		for(AppArtifactCoords deploymentCoords : deploymentDeps) {
			if(deploymentCoords.getArtifactId().equals("quarkus-bom")) {
				continue;
			}

			LinkedList<String> targetList = null;
			AtomicInteger targetIndex = null;
			if("test-jar".equals(deploymentCoords.getType())
					|| deploymentCoords.getArtifactId().contains("test")) {
				targetList = buildPomLines;
				targetIndex = buildPomEndDepsTag;
			} else {
				targetList = runtimeBomLines;
				targetIndex = rtBomEndDepsTag;
			}

			if (targetList != null) {

				int customIndex = -1;
				if(deploymentCoords.getArtifactId().startsWith("quarkus-")) {
					final int dash = deploymentCoords.getArtifactId().indexOf('-', "quarkus-".length() + 1);
					if(dash > 0) {
						final String rtArtifactIdPhrase = "<artifactId>" + deploymentCoords.getArtifactId().substring(0, dash);
						int i = targetIndex.intValue();
						while(i > 0) {
							String line = targetList.get(i--);
							if(line.contains(rtArtifactIdPhrase)) {
								customIndex = i;
								while(customIndex < targetList.size() && !targetList.get(customIndex++).contains("</dependency>")) {
								}
								break;
							}
						}
					}
				}

				final String artifactIdPhrase = "<artifactId>" + deploymentCoords.getArtifactId() + "</artifactId>";
				int i = 0;
				while (i < deploymentBomLines.size() && !deploymentBomLines.get(++i).contains(artifactIdPhrase)) {
				}
				if(i == deploymentBomLines.size()) {
					throw new IllegalStateException("Failed to locate " + deploymentCoords + " in " + deploymentBomXml);
				}

				int depStart = i;
				while(depStart > 0 && !deploymentBomLines.get(--depStart).contains("<dependency>")) {
				}
				int depEnd = i;
				while(depEnd < deploymentBomLines.size() && !deploymentBomLines.get(++depEnd).contains("</dependency>")) {
				}
				int index = customIndex < 0 ? targetIndex.intValue() : customIndex;
				for(int j = 0; j <= depEnd - depStart; ++j) {
					targetList.add(index++, deploymentBomLines.remove(depStart));
					targetIndex.incrementAndGet();
				}
			}
		}

		int i = 0;
		while(i < deploymentBomLines.size() && !deploymentBomLines.get(i++).contains("<dependencies>")) {
		}
		if(i == deploymentBomLines.size()) {
			throw new IllegalStateException("Failed to locate <dependencies> in " + deploymentBomXml);
		}
		final String offset = deploymentBomLines.get(i - 1).substring(0, deploymentBomLines.get(i - 1).length() + 1 - "</dependencies>".length());
		deploymentBomLines.add(i++, "");
		deploymentBomLines.add(i++, offset + "<!--");
		deploymentBomLines.add(i++, offset + "    DO NOT ADD NEW DEPENDENCIES HERE!");
		deploymentBomLines.add(i++, offset + "    THIS BOM IS DEPRECATED IN FAVOR OF quarkus-bom.");
		deploymentBomLines.add(i++, offset + "-->");

		boolean replacePoms = true;

		try(BufferedWriter writer = Files.newBufferedWriter(getPersistPath(runtimeBomXml, replacePoms))) {
			for(String line : runtimeBomLines) {
				writer.write(line);
				writer.newLine();
			}
		}

		try(BufferedWriter writer = Files.newBufferedWriter(getPersistPath(deploymentBomXml, replacePoms))) {
			for(String line : deploymentBomLines) {
				writer.write(line);
				writer.newLine();
			}
		}

		try(BufferedWriter writer = Files.newBufferedWriter(getPersistPath(buildPomXml, replacePoms))) {
			for(String line : buildPomLines) {
				writer.write(line);
				writer.newLine();
			}
		}

	}

	private static Path getPersistPath(Path p, boolean replacePom) {
		return replacePom ? p : p.getParent().resolve("migrated-pom.xml");
	}

	private static LinkedList<String> readAllLines(final Path xml) throws IOException {
		final LinkedList<String> deploymentBomLines = new LinkedList<>();
		try(BufferedReader reader = Files.newBufferedReader(xml)) {
			String line;
			while((line = reader.readLine()) != null) {
				deploymentBomLines.add(line);
			}
		}
		return deploymentBomLines;
	}

	private static void migrateDependency(Dependency deploymentDep, final String nextToArtifactId,
			final LinkedList<String> targetLines, final Path targetBom) {
		final String rtArtifactIdPhrase = "<artifactId>" + nextToArtifactId + "</artifactId>";
		int i = 0;
		while(i < targetLines.size()) {
			String line = targetLines.get(i++);
			if(line.contains(rtArtifactIdPhrase)) {
				break;
			}
		}
		if(i == targetLines.size()) {
			throw new IllegalStateException("Failed to locate " + rtArtifactIdPhrase + " in " + targetBom);
		}
		int depStart = i;
		while(depStart > 0 && !targetLines.get(--depStart).contains("<dependency>")) {
		}
		int depEnd = i;
		while(depEnd < targetLines.size() && !targetLines.get(++depEnd).contains("</dependency>")) {
		}
		for(int j = depStart; j <= depEnd; ++j) {
			String line = targetLines.get(j);
			if(line.contains("<artifactId>")) {
				line = line.replace(nextToArtifactId, deploymentDep.getArtifactId());
			}
			targetLines.add(depEnd + 1 + j - depStart, line);
		}
	}

	private static AppArtifactCoords toAppCoords(Dependency dep) {
		return new AppArtifactCoords(dep.getGroupId(), dep.getArtifactId(), dep.getClassifier(), dep.getType(), dep.getVersion());
	}

	private static String toString(Dependency dep) {
		return dep.getGroupId() + ":" + dep.getArtifactId() + ":" + (dep.getClassifier() == null ? "" : dep.getClassifier()) + ":" + dep.getType() + ":" + dep.getVersion();
	}

	private static void log(Object msg) {
		System.out.println(msg == null ? "null" : msg.toString());
	}
}
