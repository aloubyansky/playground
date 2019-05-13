# This is a reproducer for a profile activation issue in Maven 3.8.5

The reproducer consists of two modules:

* `maven-plugin` - Maven plugin with a mojo `resolve-project-dependencies` that resolves current project's dependencies using the Maven resolver (Aether) API and compares the result to the `MavenProject.getArtifacts()`. Both of the dependency sets are expected to be identical;
* `test` - a module producing an empty JAR that doesn't have any dependency in its default configuration but includes a profile `extraDeps` that adds a single dependency.

## To reproduce the issue

**IMPORTANT: Make sure Maven 3.8.5 is used to build the project!**

The resulting dependency set returned from the Maven (Aether) resolver appears to depend on the way the `extraDeps` profile is activated on the command line. If the profile is activated with`-DextraDeps` then the dependency included in the profile will be included in the result. If the profile is activated with `-PextraDeps` then it won't be included. In Maven versions before 3.8.5 the dependency from the profile is included no matter how the profile is activated.

This can be demonstrated by running the following commands from the root directory of the project:

```shell script
[aloubyansky@localhost maven-profile-dependencies]$ mvn clean package -DextraDeps
<skip>
[INFO] --- acme-maven-plugin:1.0-SNAPSHOT:resolve-project-dependencies (resolve-project-dependencies) @ acme-test ---
[INFO] ------------------------------------------------------------------------
[INFO] Reactor Summary for acme-parent 1.0-SNAPSHOT:
[INFO] 
[INFO] acme-parent ........................................ SUCCESS [  0.131 s]
[INFO] acme-maven-plugin .................................. SUCCESS [  2.621 s]
[INFO] acme-test .......................................... SUCCESS [  0.191 s]
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
<skip>
```

```shell script
[aloubyansky@localhost maven-profile-dependencies]$ mvn clean package -PextraDeps
<skip>
[INFO] --- acme-maven-plugin:1.0-SNAPSHOT:resolve-project-dependencies (resolve-project-dependencies) @ acme-test ---
[INFO] ------------------------------------------------------------------------
[INFO] Reactor Summary for acme-parent 1.0-SNAPSHOT:
[INFO] 
[INFO] acme-parent ........................................ SUCCESS [  0.136 s]
[INFO] acme-maven-plugin .................................. SUCCESS [  2.551 s]
[INFO] acme-test .......................................... FAILURE [  0.180 s]
[INFO] ------------------------------------------------------------------------
[INFO] BUILD FAILURE
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  2.969 s
[INFO] Finished at: 2022-03-15T14:35:51+01:00
[INFO] ------------------------------------------------------------------------
---------------------------------------------------
constituent[0]: file:/home/aloubyansky/apache-maven-3.8.5/conf/logging/
constituent[1]: file:/home/aloubyansky/apache-maven-3.8.5/lib/maven-settings-3.8.5.jar
constituent[2]: file:/home/aloubyansky/apache-maven-3.8.5/lib/maven-resolver-spi-1.6.3.jar
constituent[3]: file:/home/aloubyansky/apache-maven-3.8.5/lib/plexus-interpolation-1.26.jar
constituent[4]: file:/home/aloubyansky/apache-maven-3.8.5/lib/maven-artifact-3.8.5.jar
constituent[5]: file:/home/aloubyansky/apache-maven-3.8.5/lib/jcl-over-slf4j-1.7.32.jar
constituent[6]: file:/home/aloubyansky/apache-maven-3.8.5/lib/plexus-sec-dispatcher-2.0.jar
constituent[7]: file:/home/aloubyansky/apache-maven-3.8.5/lib/wagon-http-3.5.1-shaded.jar
constituent[8]: file:/home/aloubyansky/apache-maven-3.8.5/lib/maven-resolver-provider-3.8.5.jar
constituent[9]: file:/home/aloubyansky/apache-maven-3.8.5/lib/maven-resolver-connector-basic-1.6.3.jar
constituent[10]: file:/home/aloubyansky/apache-maven-3.8.5/lib/jansi-2.4.0.jar
constituent[11]: file:/home/aloubyansky/apache-maven-3.8.5/lib/maven-model-3.8.5.jar
constituent[12]: file:/home/aloubyansky/apache-maven-3.8.5/lib/maven-builder-support-3.8.5.jar
constituent[13]: file:/home/aloubyansky/apache-maven-3.8.5/lib/commons-io-2.6.jar
constituent[14]: file:/home/aloubyansky/apache-maven-3.8.5/lib/maven-embedder-3.8.5.jar
constituent[15]: file:/home/aloubyansky/apache-maven-3.8.5/lib/plexus-utils-3.3.0.jar
constituent[16]: file:/home/aloubyansky/apache-maven-3.8.5/lib/org.eclipse.sisu.plexus-0.3.5.jar
constituent[17]: file:/home/aloubyansky/apache-maven-3.8.5/lib/commons-cli-1.4.jar
constituent[18]: file:/home/aloubyansky/apache-maven-3.8.5/lib/maven-resolver-util-1.6.3.jar
constituent[19]: file:/home/aloubyansky/apache-maven-3.8.5/lib/maven-resolver-api-1.6.3.jar
constituent[20]: file:/home/aloubyansky/apache-maven-3.8.5/lib/javax.annotation-api-1.2.jar
constituent[21]: file:/home/aloubyansky/apache-maven-3.8.5/lib/maven-resolver-transport-wagon-1.6.3.jar
constituent[22]: file:/home/aloubyansky/apache-maven-3.8.5/lib/maven-slf4j-provider-3.8.5.jar
constituent[23]: file:/home/aloubyansky/apache-maven-3.8.5/lib/wagon-file-3.5.1.jar
constituent[24]: file:/home/aloubyansky/apache-maven-3.8.5/lib/maven-model-builder-3.8.5.jar
constituent[25]: file:/home/aloubyansky/apache-maven-3.8.5/lib/javax.inject-1.jar
constituent[26]: file:/home/aloubyansky/apache-maven-3.8.5/lib/maven-settings-builder-3.8.5.jar
constituent[27]: file:/home/aloubyansky/apache-maven-3.8.5/lib/org.eclipse.sisu.inject-0.3.5.jar
constituent[28]: file:/home/aloubyansky/apache-maven-3.8.5/lib/maven-shared-utils-3.3.4.jar
constituent[29]: file:/home/aloubyansky/apache-maven-3.8.5/lib/commons-lang3-3.8.1.jar
constituent[30]: file:/home/aloubyansky/apache-maven-3.8.5/lib/maven-compat-3.8.5.jar
constituent[31]: file:/home/aloubyansky/apache-maven-3.8.5/lib/plexus-cipher-2.0.jar
constituent[32]: file:/home/aloubyansky/apache-maven-3.8.5/lib/plexus-component-annotations-2.1.0.jar
constituent[33]: file:/home/aloubyansky/apache-maven-3.8.5/lib/wagon-provider-api-3.5.1.jar
constituent[34]: file:/home/aloubyansky/apache-maven-3.8.5/lib/guice-4.2.2-no_aop.jar
constituent[35]: file:/home/aloubyansky/apache-maven-3.8.5/lib/guava-25.1-android.jar
constituent[36]: file:/home/aloubyansky/apache-maven-3.8.5/lib/maven-resolver-impl-1.6.3.jar
constituent[37]: file:/home/aloubyansky/apache-maven-3.8.5/lib/maven-core-3.8.5.jar
constituent[38]: file:/home/aloubyansky/apache-maven-3.8.5/lib/slf4j-api-1.7.32.jar
constituent[39]: file:/home/aloubyansky/apache-maven-3.8.5/lib/maven-repository-metadata-3.8.5.jar
constituent[40]: file:/home/aloubyansky/apache-maven-3.8.5/lib/maven-plugin-api-3.8.5.jar
---------------------------------------------------
Exception in thread "main" java.lang.AssertionError: 
expected: ["io.quarkus:quarkus-bom-quarkus-platform-descriptor:2.7.4.Final:json:2.7.4.Final"]
 but was: []
    at org.acme.ResolveProjectDependenciesMojo.execute(ResolveProjectDependenciesMojo.java:59)
    at org.apache.maven.plugin.DefaultBuildPluginManager.executeMojo(DefaultBuildPluginManager.java:137)
    at org.apache.maven.lifecycle.internal.MojoExecutor.doExecute(MojoExecutor.java:301)
    at org.apache.maven.lifecycle.internal.MojoExecutor.execute(MojoExecutor.java:211)
    at org.apache.maven.lifecycle.internal.MojoExecutor.execute(MojoExecutor.java:165)
    at org.apache.maven.lifecycle.internal.MojoExecutor.execute(MojoExecutor.java:157)
    at org.apache.maven.lifecycle.internal.LifecycleModuleBuilder.buildProject(LifecycleModuleBuilder.java:121)
    at org.apache.maven.lifecycle.internal.LifecycleModuleBuilder.buildProject(LifecycleModuleBuilder.java:81)
    at org.apache.maven.lifecycle.internal.builder.singlethreaded.SingleThreadedBuilder.build(SingleThreadedBuilder.java:56)
    at org.apache.maven.lifecycle.internal.LifecycleStarter.execute(LifecycleStarter.java:127)
    at org.apache.maven.DefaultMaven.doExecute(DefaultMaven.java:294)
    at org.apache.maven.DefaultMaven.doExecute(DefaultMaven.java:192)
    at org.apache.maven.DefaultMaven.execute(DefaultMaven.java:105)
    at org.apache.maven.cli.MavenCli.execute(MavenCli.java:960)
    at org.apache.maven.cli.MavenCli.doMain(MavenCli.java:293)
    at org.apache.maven.cli.MavenCli.main(MavenCli.java:196)
    at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
    at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:62)
    at java.base/jdk.internal.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
    at java.base/java.lang.reflect.Method.invoke(Method.java:566)
    at org.codehaus.plexus.classworlds.launcher.Launcher.launchEnhanced(Launcher.java:282)
    at org.codehaus.plexus.classworlds.launcher.Launcher.launch(Launcher.java:225)
    at org.codehaus.plexus.classworlds.launcher.Launcher.mainWithExitCode(Launcher.java:406)
    at org.codehaus.plexus.classworlds.launcher.Launcher.main(Launcher.java:347)
```
