# Reproducer for the JaCoCo instrumentation issue

The issue was discussed here https://groups.google.com/g/jacoco/c/ggU-DrjtrAM/m/NoWbPQtNBAAJ

The reproducer includes an `Example` class that has a method `callme(String greeting)`. The test locates the class file on the classpath
and reads its bytes into a byte array. The it calls Jandex Indexer to read the ClassInfo and verifies whether the `callme` method has a parameter named `greeting`.

Next it intruments the class using the JaCoCo Instrumenter API and calls the Jandex Indexer again to read the ClassInfo from the instrumented byte array.

If the `maven.compiler.parameters` in the `pom.xml` is set to `false`, the test will fail, demonstrating the parameter name info became unavailable after the instrumentation.

If the `maven.compiler.parameters` is set to `true` the test passes.