package io.quarkiverse.quarkus.config.interceptor.deployment;

import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import io.quarkus.builder.item.EmptyBuildItem;
import io.quarkus.deployment.IsNormal;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.ConfigurationBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.LaunchModeBuildItem;
import io.quarkus.deployment.builditem.QuarkusBuildCloseablesBuildItem;
import io.quarkus.deployment.pkg.builditem.ArtifactResultBuildItem;
import io.quarkus.deployment.pkg.builditem.BuildSystemTargetBuildItem;

class ConfigInterceptorProcessor {

    private static final String FEATURE = "config-dump";

    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep(onlyIf = IsNormal.class)
    void persistCollectedProperties(BuildProducer<ArtifactResultBuildItem> dummy,
            QuarkusBuildCloseablesBuildItem closeables,
            ConfigDumpConfiguration config,
            LaunchModeBuildItem launchMode,
            BuildSystemTargetBuildItem buildSystemTargetBuildItem,
            ConfigurationBuildItem configBuildItem) {

        Path file = config.file().orElse(null);
        if (file == null) {
            Path dir;
            if (config.directory().isPresent()) {
                dir = config.directory().get();
            } else {
                dir = buildSystemTargetBuildItem.getOutputDirectory().getParent().resolve(".quarkus");
            }
            file = dir
                    .resolve(config.filePrefix() + "-" + launchMode.getLaunchMode().getDefaultProfile() + config.fileSuffix());
        } else if (!file.isAbsolute()) {
            file = config.directory().orElse(buildSystemTargetBuildItem.getOutputDirectory()).resolve(file);
        }

        final Map<String, String> allBuildTimeValus = configBuildItem.getReadResult().getAllBuildTimeValues();
        final Map<String, String> buildTimeRuntimeValues = configBuildItem.getReadResult().getBuildTimeRunTimeValues();
        final Path output = file;
        closeables.add(() -> {
            System.out.println("ConfigInterceptorProcessor DUMP CONFIG");
            if (output.getParent() != null) {
                Files.createDirectories(output.getParent());
            }
            try (BufferedWriter writer = Files.newBufferedWriter(output)) {
                final List<String> names = new ArrayList<>(QuarkusAppConfigInterceptor.collectedProperties.keySet());
                Collections.sort(names);
                for (String name : names) {
                    if (allBuildTimeValus.containsKey(name) || buildTimeRuntimeValues.containsKey(name)) {
                        var value = QuarkusAppConfigInterceptor.collectedProperties.get(name);
                        if (value != null) {
                            writer.write(name);
                            writer.write("=");
                            writer.write(value);
                            writer.newLine();
                        }
                    }
                }
            }
        });
    }

    private static final class AlwaysBuildItem extends EmptyBuildItem {
    }
}
