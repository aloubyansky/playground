# Playground

A reproducer to figure out why a dependency provider is called more than once during a configuration action.

Running `./gradlew :app:dependencies --configuration greetingClasspath` produces output such as
````
$ ./gradlew :app:dependencies --configuration greetingClasspath

> Task :app:dependencies

------------------------------------------------------------
Project ':app'
------------------------------------------------------------

greetingClasspath
Dependency provider call counter: 1
Dependency provider call counter: 2
\--- xom:xom:1.3.9

A web-based, searchable dependency report is available by adding the --scan option.
````

The producer consists of two modules:
* `plugin` - a simple Gradle plugin that registers `greetingClasspath` configuration and uses the Provider API to add dependencies to it.
* `app` - an "application" project that applies the example plugin.
