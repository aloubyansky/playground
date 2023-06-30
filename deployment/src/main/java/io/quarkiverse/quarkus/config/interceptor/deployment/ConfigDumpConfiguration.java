package io.quarkiverse.quarkus.config.interceptor.deployment;

import java.nio.file.Path;
import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "quarkus.config-dump")
@ConfigRoot(phase = ConfigPhase.BUILD_TIME)
public interface ConfigDumpConfiguration {

    /**
     * Whether configuration dumping is enabled
     *
     * @return whether configuration dumping is enabled
     */
    @WithDefault("true")
    boolean enabled();

    /**
     * Directory in which the configuration dump should be stored.
     * If not configured the {@code .quarkus} directory under the project directory will be used.
     *
     * @return directory in which the configuration dump should be stored or null, in which case
     *         the {@code .quarkus} directory under the project directory will be used
     */
    Optional<Path> directory();

    /**
     * File to which the configuration should be dumped. If not configured, the {@link #filePrefix()} and
     * {@link #fileSuffix()} will be used to generate the final file name.
     * If the configured file path is absolute, the {@link #directory()} option will be ignored. Otherwise,
     * the path will be ocnsidered relative to the {@link #directory()}.
     *
     * @return file to which the configuration should be dumped or null
     */
    Optional<Path> file();

    /**
     * File name prefix. This option will be ignored in case {@link #file()} is configured.
     *
     * @return file name prefix
     */
    @WithDefault("quarkus-app")
    String filePrefix();

    /**
     * File name suffix. This option will be ignored in case {@link #file()} is configured.
     *
     * @return file name suffix
     */
    @WithDefault("-config-dump")
    String fileSuffix();
}
