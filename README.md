# Playground

A playground to figure out how to make Configuration.copyRecursive() work with test-fixtures.

Running `./gradlew :app:greeting` produces output such as
````
$ ./gradlew clean :app:greeting
> Task :app:greeting FAILED

FAILURE: Build failed with an exception.

* What went wrong:
Execution failed for task ':app:greeting'.
> Could not resolve all artifacts for configuration ':app:greetingClasspathCopy'.
   > Could not resolve project :app.
     Required by:
         project :app
      > Unable to find a matching variant of project :app:
          - No variants exist.

* Try:
> Creating consumable variants is explained in more detail at https://docs.gradle.org/8.13/userguide/declaring_dependencies.html#sec:resolvable-consumable-configs.
> Review the variant matching algorithm at https://docs.gradle.org/8.13/userguide/variant_attributes.html#sec:abm_algorithm.
> Run with --stacktrace option to get the stack trace.
> Run with --info or --debug option to get more log output.
> Run with --scan to get full insights.
> Get more help at https://help.gradle.org.

BUILD FAILED in 483ms
6 actionable tasks: 1 executed, 5 up-to-date
````

The producer consists of two modules:
* `plugin` - a simple Gradle plugin that registers `greetingClasspath` configuration and adds `greeting` task that resolves it.
* `app` - an "application" project that applies the example plugin.
