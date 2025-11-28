# Instructions to test Quarkus builds with the IBM Maven repository

This repository contains a couple of scripts to test building Quarkus projects with IBM and Red Hat Maven repositories.

Every build will use an empty local Maven repository, that is re-created on every build.

## Java Version

Java 21 is the required minimum for these builds.

## Maven Goals

The default Maven goals are `clean package`.

For `quarkus-super-heroes` the test executions will be skipped with `-DskipTests -DskipITs`, since they take a significant amount of time and not that relevant for this exercise.

## Basic REST Application

### Create a project

```shell script
curl https://code.quarkus.io/d?cn=code.quarkus.io --output code-with-quarkus.zip && unzip code-with-quarkus.zip
```

### Build with the IBM Maven repository

```shell script
./ibm-build.sh
```

The script will re-create a new temporary local Maven repository on every run. So you can re-run this command to do another clean build.

### Build with the Red Hat Maven repository

```shell script
./redhat-build.sh
```

Same as above. The script will re-create a new temporary local Maven repository on every run. So you can re-run this command to do another clean build.

## Building Quarkus Super Heroes

### Clone the project

```shell script
git clone git@github.com:quarkusio/quarkus-super-heroes.git
```

### Build with the IBM Maven repository

```shell script
./ibm-build.sh quarkus-super-heroes
```

The script will skip test executions (since they take a significant amount of time to run) and re-create a new temporary local Maven repository on every run. So you can re-run this command to do another clean build.

### Build with the Red Hat Maven repository

```shell script
./redhat-build.sh quarkus-super-heroes
```

Same as above. The script will skip test executions (since they take a significant amount of time to run) and re-create a new temporary local Maven repository on every run. So you can re-run this command to do another clean build.

