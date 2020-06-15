# Reproducer for https://github.com/quarkusio/quarkus/issues/9490

## Projects

* acme-common-1.0 - common library version 1.0. Defines a CommonBean with a single static method
* acme-common-2.0 - common library version 2.0. Backward compatible with version 1.0 but intrduces a new method that's not present in 1.0.
* acme-app-lib - a library that declares a dependency on acme-common 1.0
* acme-quarkus-bom - let's say it's a quarkus (platform) runtime BOM that includes acme quarkus extension
* acme-quarkus-bom-deployment - quarkus (platform) deployment BOM that includes acme quarkus extension and acme-common 2.0 that's used by the acme quarkus extension
* acme-quarkus-extension - acme quarkus extension, the deployment artifact of which depends on acme-common 2.0
* acme-app - acme quarkus app that declares a dependency on acme-app-lib and also acme quarkus extension

## Build

```
./build.sh
```

This will install all the dependent projects into the local repo and build the acme quarkus app.
The build will fail because acme-common that is a transitive dependency of acme-app-lib will be resolved as version 1.0
as a runtime dependency. The runtime dependencies will be dominating the build classpath. So acme-quarkus-extension-deployment will end using an older version of acme-common
instead of what was configured in the deployment BOM.

If the application imported acme-quarkus-bom-deployment instead of acme-quarkus-bom the application will build.