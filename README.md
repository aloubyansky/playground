# Playground

A playground to figure out how to add a variant avoiding ambiguity.

Running `./gradlew :app:greeting` produces output such as
````
$ ./gradlew :app:greeting
> Task :app:greeting FAILED

[Incubating] Problems report is available at: file:///home/aloubyansky/git/playground/build/reports/problems/problems-report.html

FAILURE: Build failed with an exception.

* What went wrong:
Execution failed for task ':app:greeting'.
> Could not resolve all files for configuration ':app:greetingClasspath'.
   > Could not resolve xom:xom:1.3.9.
     Required by:
         project :app
      > Cannot choose between the available variants of xom:xom:1.3.9:
          - otherVariant
          - runtime
        All of them match the consumer attributes:
          - Variant 'otherVariant' capability 'xom:xom:1.3.9':
              - Unmatched attributes:
                  - Provides org.gradle.category 'library' but the consumer didn't ask for it
                  - Provides org.gradle.libraryelements 'jar' but the consumer didn't ask for it
                  - Provides org.gradle.status 'release' but the consumer didn't ask for it
                  - Provides org.gradle.usage 'java-runtime' but the consumer didn't ask for it
                  - Provides other-attr 'on' but the consumer didn't ask for it
          - Variant 'runtime' capability 'xom:xom:1.3.9':
              - Unmatched attributes:
                  - Provides org.gradle.category 'library' but the consumer didn't ask for it
                  - Provides org.gradle.libraryelements 'jar' but the consumer didn't ask for it
                  - Provides org.gradle.status 'release' but the consumer didn't ask for it
                  - Provides org.gradle.usage 'java-runtime' but the consumer didn't ask for it

* Try:
> Ambiguity errors are explained in more detail at https://docs.gradle.org/8.13/userguide/variant_model.html#sub:variant-ambiguity.
> Review the variant matching algorithm at https://docs.gradle.org/8.13/userguide/variant_attributes.html#sec:abm_algorithm.
> Run with --stacktrace option to get the stack trace.
> Run with --info or --debug option to get more log output.
> Run with --scan to get full insights.
> Get more help at https://help.gradle.org.

BUILD FAILED in 579ms
5 actionable tasks: 3 executed, 2 up-to-date
````

The producer consists of two modules:
* `plugin` - a simple Gradle plugin that registers `greetingClasspath` configuration and adds `greeting` task that resolves it.
* `app` - an "application" project that applies the example plugin.
