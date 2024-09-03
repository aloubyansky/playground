# Example of a customer Quarkus platform and extension registry setup

## Platform project

The project consists of the following modules:

* `bom` - platform BOM that includes extension version constraints and the platform descriptor JSON artifact;
* `platform-descriptor` - generates platform descriptor JSON artifact by processing the platform BOM;
- `extensions` - module containing platform extension implementations (there is only one in this example - `acme-magic`);

The parent `pom.xml` is used to set the Quarkus and Maven plugin versions.

`registry` directory isn't a project module but just a directory that contains data for extension registry initialization (explained below).

To setup a local extension registry this project has to be built and installed in the local Maven repository with `mvn install`.

## Extension registry setup

Extension registry is a Quarkus application that can be cloned from GitHub with
```
git clone https://github.com/quarkusio/registry.quarkus.io
```

Once the registry has been cloned, it can launched with
```
mvn clean quarkus:dev -Dquarkus.registry.id=registry.quarkus.acme.io -Dquarkus.registry.groupId=io.acme.quarkus.registry
```

Once launched, the registry needs to be populated with data about the Quarkus platform and extensions. This is typically done with a JBang script.

```
$ cd registry

registry$ jbang catalog_publish@quarkusio --working-directory=. --registry-url=http://localhost:8080 --token=test --all
2024-09-03T08:43:37.812517374+02:00[Europe/Zurich] [INFO] Processing platform ./platforms/quarkus-bom.yaml
2024-09-03T08:43:37.819736306+02:00[Europe/Zurich] [INFO] ---------------------------------------------------------------
2024-09-03T08:43:37.842353545+02:00[Europe/Zurich] [INFO] Publishing io.acme.quarkus:acme-quarkus-bom-quarkus-platform-descriptor:1.2.3-SNAPSHOT
2024-09-03T08:43:37.918748906+02:00[Europe/Zurich] [ERROR] The LogManager accessed before the "java.util.logging.manager" system property was set to "org.jboss.logmanager.LogManager". Results may be unexpected.
2024-09-03T08:43:38.183750820+02:00[Europe/Zurich] [INFO] Platform published
2024-09-03T08:43:38.223253501+02:00[Europe/Zurich] [INFO] ---------------------------------------------------------------
```

Now the registry contains the necessary data. This can be verified by querying the registry through the Swagger UI, for example http://localhost:8080/q/swagger-ui/#/Client/get_client_platforms to see the platform and http://localhost:8080/q/swagger-ui/#/Client/get_client_extensions_all to see the extension catalog (there will be only one extension).

## Configuring the registry client

To be able to create projects using the custom Quarkus platform locally, a registry client needs to be configured to query the new extension registry.

One of the ways to obtain an initial configuration would be to query http://localhost:8080/q/swagger-ui/#/Client/get_client_config_yaml, this will return a configuration in the YAML format that can be saved under `~/.quarkus/config.yaml`.
However, this configuration will include only this registry (so the registry client will pull data only from this registry) and the URL will need an adjustment.

If we want to enable the default `registry.quarkus.io` in addition to the custom registry (to platform and extensions from Quarkiverse), we need to add `registry.quarkus.io` to the list, such as
```
registries:
- registry.quarkus.acme.io:
    update-policy: "always"
    maven:
      repository:
        url: "http://localhost:8080/maven"
- registry.quarkus.io
```

This way, `registry.quarkus.acme.io` will be the primary registry (the first to be queried) and `registry.quarkus.io` will be used to complete the platform and extension catalog from the Quarkiverse.

`update-policy` set to `always` means caching of the responses from the `registry.quarkus.acme.io` will be disabled, so every query will be served by the registry service. The default value for `update-policy` is `daily`, meaning a registry would be queried on the first request in a day to refresh the local cache.

## Creating applications

Once the registry is up, populated with data and the client configuration is in place, Quarkus dev tools (such as the CLI, Maven and Gradle plugins) can be used to create and manipulate Quarkus projects.

```
$ quarkus create app test-app -x quarkus-rest,magic
Looking for the newly published extensions in registry.quarkus.acme.io
-----------
selected extensions: 
- io.acme.quarkus:quarkus-magic
- io.quarkus:quarkus-rest


applying codestarts...
📚 java
🔨 maven
📦 quarkus
📝 config-properties
🔧 tooling-dockerfiles
🔧 tooling-maven-wrapper
🚀 rest-codestart

-----------
[SUCCESS] ✅  quarkus project has been successfully generated in:
--> /home/aloubyansky/playground/test-app
-----------
Navigate into this directory and get started: quarkus dev
```

The resulting `pom.xml` will include

```
    <properties>
        <quarkus.platform.artifact-id>quarkus-bom</quarkus.platform.artifact-id>
        <quarkus.platform.group-id>io.quarkus.platform</quarkus.platform.group-id>
        <quarkus.platform.version>3.12.1</quarkus.platform.version>
    </properties>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>io.acme.quarkus</groupId>
                <artifactId>acme-quarkus-bom</artifactId>
                <version>1.2.3-SNAPSHOT</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
            <dependency>
                <groupId>${quarkus.platform.group-id}</groupId>
                <artifactId>${quarkus.platform.artifact-id}</artifactId>
                <version>${quarkus.platform.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <dependencies>
        <dependency>
            <groupId>io.acme.quarkus</groupId>
            <artifactId>quarkus-magic</artifactId>
        </dependency>
        <dependency>
            <groupId>io.quarkus</groupId>
            <artifactId>quarkus-rest</artifactId>
        </dependency>
```
