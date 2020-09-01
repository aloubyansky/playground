# BOM Decomposer

The project includes various utilities that help analyze managed dependencies of a project
and suggest version changes to avoid potential conflicts among the dependencies.

## Multi module release detection

One of the utilities included is a multi module release detector. It is a best-effort
(not 100% accurate) utility that is trying to identify the origin (e.g. a git repo or
another ID) of the artifacts from the effective set of the project's managed dependencies and
in case multiple releases from the same origin are detected it reports them as conflicts.

NOTE: the utility will be resolving every managed dependency as part of the analyses!

The utility can invoked using a public API or a Maven plugin with a minimal configuration below
```
    <build>
        <plugins>
            <plugin>
                <groupId>io.quarkus</groupId>
                <artifactId>quarkus-platform-bom-maven-plugin</artifactId>
                <version>999-SNAPSHOT</version>
                <executions>
                    <execution>
                        <goals>
                            <goal>report-release-versions</goal>
                        </goals>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
```

which should be added to the `pom.xml`. With this minimal configuration the conflicts will be
logged as a `WARNING`.

Here is a complete set of supported options
```
    <build>
        <plugins>
            <plugin>
                <groupId>io.quarkus</groupId>
                <artifactId>quarkus-platform-bom-maven-plugin</artifactId>
                <version>999-SNAPSHOT</version>
                <configuration>
                    <!-- Whether to skip this goal during the build -->
                    <skip>${skipBomReport}</skip>

                    <!-- wWether to generate the HTML report, the default is true -->
                    <htmlReport>${bomHtmlReport}</htmlReport>

                    <!--
                       Whether to report all the detected release origins
                       or only those with the conflicts, the default is false (only the conflicts)
                    -->
                    <reportAll>${bomReportAll}</reportAll>

                    <!-- The default level to use for report logging, the default is DEBUG -->
                    <reportLogging>${bomReportLogging}</reportLogging>

                    <!--
                      How to handle a detected conflict. The allowed values are:
                      * WARN - log a warning (the default)
                      * ERROR - log an error and fail the build
                      * INFO - log an info message
                      * DEBUG - log a debug message
                    -->
                    <bomConflict>${bomConflict}</bomConflict>

                    <!--
                      How to handle a detected resolvable version conflict. I.e. in case
                      the preferred version of the artifact was found to be available in the Maven
                      repository. Allowed values are:
                      * WARN - log a warning
                      * ERROR - log an error and fail the build (the default)
                      * INFO - log and info message
                      * DEBUG - log a debug message
                    -->
                    <bomResolvableConflict>${bomResolvableConflict}</bomResolvableConflict>

                    <!--
                      Whether to skip checking the conflicting dependencies for available versions updates
                      picking the latest version found in the BOM as the preferred one to align with.
                      The default is false.
                    -->
                    <skipUpdates>${bomSkipUpdates}</skipUpdates>
                </configuration>
                <executions>
                    <execution>
                        <goals>
                            <goal>report-release-versions</goal>
                        </goals>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
```