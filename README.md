Before using the commands below it is recommended to install all the projects by running `mvn install -f aggregate-pom.xml` from the root directory.

## The product-a project

In this project the Cyclone DX plugin is invoked twice:

1. with the `provided` scope included saving the result in the `target/bom-build-time.json`;
2. with the `provided` scope excluded saving the result in the `target/bom-runtime.json`.

To generate the SBOMs run `mvn -Pcdx` from the `product-a` directory.

### CycloneDX 2.7.9

`bom-build-time.json` records a dependency tree identical to what `mvn dependency:tree` logs, which is correct.

`bom-runtime.json` however, recorded `acme-lib-c:2.0.0-SNAPSOT` as a dependency of `acme-lib-b:1.0.0-SNAPSHOT`, which is wrong
given that `acme-lib-b:1.0.0-SNAPSHOT` dependends on `acme-lib-c:1.0.0-SNAPSHOT`. Basically, the `provided` dependency on
`acme-lib-d` was excluded but its transitive dependency still appears in the SBOM.

### CycloneDX 2.7.4

`bom-build-time.json` does include `acme-lib-d` since the `provided` scope was included in the configuration. It also includes `acme-lib-c:2.0.0-SNAPSHOT` as a dependency of `acme-lib-d`.

`bom-runtime.json` does not include `acme-lib-d` since the `provided` scope was excluded in the configuration. However

1. `acme-lib-c:2.0.0-SNAPSHOT` appears among the components, although nothing else than the excluded `acme-lib-d` depends on it;
2. `acme-lib-b` is missing a dependency on `acme-lib-c:1.0.0-SNAPSHOT` that is also missing among the components.

### CycloneDX 2.7.5

Change the version of the CycloneDX plugin from 2.7.4 to 2.7.5 and run `mvn -Pcdx` from the `product-a` directory.

In this version, `bom-runtime.json` looks identical to `bom-build-time.json`, i.e. the value of `includeProvidedScope` does not seem to have any effect, although it is displayed in the log correctly:
```
[aloubyansky@localhost product-a]$ mvn -Pcdx
[INFO] Scanning for projects...
[INFO] 
[INFO] ----------------------< org.acme:acme-product-a >-----------------------
[INFO] Building acme-product-a 1.0.0-SNAPSHOT
[INFO] --------------------------------[ jar ]---------------------------------
[INFO] 
[INFO] --- cyclonedx-maven-plugin:2.7.5:makeBom (build-time-sbom) @ acme-product-a ---
[INFO] CycloneDX: Parameters
[INFO] ------------------------------------------------------------------------
[INFO] schemaVersion          : 1.4
[INFO] includeBomSerialNumber : true
[INFO] includeCompileScope    : true
[INFO] includeProvidedScope   : true
[INFO] includeRuntimeScope    : true
[INFO] includeTestScope       : false
[INFO] includeSystemScope     : true
[INFO] includeLicenseText     : false
[INFO] outputFormat           : all
[INFO] outputName             : bom-build-time
[INFO] ------------------------------------------------------------------------
[INFO] CycloneDX: Resolving Dependencies
[INFO] CycloneDX: Creating BOM
[INFO] CycloneDX: Writing BOM (XML): /home/aloubyansky/git/playground/product-a/target/bom-build-time.xml
[INFO] CycloneDX: Validating BOM (XML): /home/aloubyansky/git/playground/product-a/target/bom-build-time.xml
[INFO] CycloneDX: Writing BOM (JSON): /home/aloubyansky/git/playground/product-a/target/bom-build-time.json
[INFO] CycloneDX: Validating BOM (JSON): /home/aloubyansky/git/playground/product-a/target/bom-build-time.json
[WARNING] Unknown keyword additionalItems - you should define your own Meta Schema. If the keyword is irrelevant for validation, just use a NonValidationKeyword
[INFO] 
[INFO] --- cyclonedx-maven-plugin:2.7.5:makeBom (runtime-sbom) @ acme-product-a ---
[INFO] CycloneDX: Parameters
[INFO] ------------------------------------------------------------------------
[INFO] schemaVersion          : 1.4
[INFO] includeBomSerialNumber : true
[INFO] includeCompileScope    : true
[INFO] includeProvidedScope   : false
[INFO] includeRuntimeScope    : true
[INFO] includeTestScope       : false
[INFO] includeSystemScope     : true
[INFO] includeLicenseText     : false
[INFO] outputFormat           : all
[INFO] outputName             : bom-runtime
[INFO] ------------------------------------------------------------------------
[INFO] CycloneDX: Resolving Dependencies
[INFO] CycloneDX: Creating BOM
[INFO] CycloneDX: Writing BOM (XML): /home/aloubyansky/git/playground/product-a/target/bom-runtime.xml
[INFO] CycloneDX: Validating BOM (XML): /home/aloubyansky/git/playground/product-a/target/bom-runtime.xml
[WARNING] artifact org.acme:acme-product-a:xml:cyclonedx:1.0.0-SNAPSHOT already attached, replace previous instance
[INFO] CycloneDX: Writing BOM (JSON): /home/aloubyansky/git/playground/product-a/target/bom-runtime.json
[INFO] CycloneDX: Validating BOM (JSON): /home/aloubyansky/git/playground/product-a/target/bom-runtime.json
[WARNING] artifact org.acme:acme-product-a:json:cyclonedx:1.0.0-SNAPSHOT already attached, replace previous instance
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  1.045 s
```