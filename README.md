# Instructions to launch an extension registry and a code.quarkus instances for local testing

The `docker-compose.yaml` included in this repo launches an instance of the Quarkus extension registry and an instance of code.quarkus pre-configured to use the started registry
and the upstream registry `registry.quarkus.io` (which is similar to [code.quarkus.redhat.com](https://code.quarkus.redhat.com) using `registry.quarkus.redhat.com` and `registry.quarkus.op`).

## Populating the registry with the right platform metadata

1) The registry has to be started first, so `docker-compose up` is the first step (the code.quarkus services may fail start because the registry is empty but they can be ignored for now);
2) `init-registry.sh` will populate the registry with the platform information by calling a [JBang script](https://jbang.dev/) (JBang has to be installed locally).

The JBang script will pull the metadata for the platform version configured in `platforms/quarkus-bom.yaml` from the Maven repository (Indy) which is also configured in that YAML file.

The `quarkus-bom.yaml` does not need to be re-named to `quarkus-camel-bom.yaml` or anything else. Simply the version of the BOM has to be changed to the version to be tested.
The information about other members from the specified platform release will automatically be added to the registry.

## Description of the files and directories

* `extensions/` is an empty directory which is supposed to include information about non-platform extensions, it's currently required by the JBang script generating the registry data
* `platforms/` contains information about platform versions that should be added to the registry and where their metadata should be downloaded from
* `platforms/quarkus-bom.yaml` contains the version(s) of the platform releases to be added to the registry
* `docker-compose.yaml` launches all the necessary services: the registry and the code.quarkus
* `init-registry.sh` populates the registry service with the configured platform release information
