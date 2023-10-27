This is a simple reproducer that demonstrates `test` (or `provided`) scope dependencies may override (or "leak into") compile classpath.

1. Run `mvn install -f aggregate-pom.xml` from the root project directory to install all the sample projects into the local Maven repository
2. Run `mvn -f product-a dependency:tree` from the root project directory

The following outcome should be logged to the terminal:
```
[aloubyansky@localhost playground]$ mvn -f product-a dependency:tree
[INFO] Scanning for projects...
[INFO] 
[INFO] ----------------------< org.acme:acme-product-a >-----------------------
[INFO] Building acme-product-a 1.0.0-SNAPSHOT
[INFO]   from pom.xml
[INFO] --------------------------------[ jar ]---------------------------------
[INFO] 
[INFO] --- dependency:3.6.0:tree (default-cli) @ acme-product-a ---
[INFO] org.acme:acme-product-a:jar:1.0.0-SNAPSHOT
[INFO] +- org.acme:acme-lib-d:jar:1.0.0-SNAPSHOT:test
[INFO] |  \- org.acme:acme-lib-c:jar:2.0.0-SNAPSHOT:compile
[INFO] \- org.acme:acme-lib-b:jar:1.0.0-SNAPSHOT:compile
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
```

Here you can see that `org.acme:acme-lib-c:2.0.0-SNAPSHOT` (which is actually on a `test` dependency branch) was selected during the conflict resolution and its scope was promoted to `compile,` while a `compile` dependency `org.acme:acme-lib-b:1.0.0-SNAPSHOT` depends on `org.acme:acme-lib-c:1.0.0-SNAPSHOT`.

NOTE: the same issue can be demonstrated for `provided` dependencies by replacing `test` scope with `provided` for dependency `acme-lib-d`.