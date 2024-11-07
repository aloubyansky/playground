This repo contains a couple of examples demonstrating the challenge of manifesting embedded/bundled (shaded) vs linked dependencies.

Run
```
./mvnw -f aggregate-pom.xml
```
to generate the CycloneDX SBOMs.

## product-x

In this example, `product-x` depends on `lib-a`, `lib-a` depends on `lib-z` but `product-x` excludes `lib-z` as a dependency in its project configuration. And so
```
less product-x/target/bom.json
```
shows that `product-x` depends on `lib-a` which does not have dependencies on its own, although
```
less lib-a/target/bom.json
```
shows that it does depend on `lib-z`.

So if there are tools that, based on SBOM analysis, conclude that `lib-z` is a dependency of `product-x`, that will be wrong in this case.

## product-y

In this example, `product-y` depends on three libraries:

* `lib-a` that depends on `lib-z:1.0`;
* `lib-b` that depends on `lib-z:2.0`;
* `lib-c` that depends on `lib-z:3.0`.

```
less product-y/target/bom.json
```
manifests dependencies on `lib-a`, `lib-b` and `lib-c`, and that all of them depend on `lib-z:1.0` (meaning in the context of `product-y` all the other libs will use `lib-z:1.0`).

So if SBOM dependency analysis tools conclude that
* `product-y` transitively depends on `lib-z:1.0` - correct;
* `product-y` transitively depends on `lib-z:2.0` - wrong, since `lib-b` does not bundle `lib-z:2.0`;
* `product-z` transitively depends on `lib-z:3.0` - correct, since `lib-c` bundles `lib-z:3.0`.

This can be seen by running
```
vim lib-c/target/lib-c-1.0.jar
```
that will display the following content included in the `lib-c-1.0.jar`:
```
lib-z-3.0.txt
META-INF/maven/org.z/
META-INF/maven/org.z/lib-z/
META-INF/maven/org.z/lib-z/pom.xml
META-INF/maven/org.z/lib-z/pom.properties
```
which comes from `lib-z/3.0/target/lib-z-3.0.jar`.
