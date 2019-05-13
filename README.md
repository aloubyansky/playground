# Playground

1) `cd gradle-bom` and install it `mvn install`
2) `cd gradle-plugin` and install it `./gradlew publishToMavenLocal`
3) `cd gradle-app` and list its dependencies `./gradlew dependencies --configuration compileClasspath`

You should see

````
compileClasspath - Compile classpath for source set 'main'.
+--- io.playground:playground-bom:999-SNAPSHOT
\--- commons-lang:commons-lang FAILED
````