#!/bin/bash

# remove the local Maven repository
rm -rf local-maven-repo/

projectDir="code-with-quarkus"
if (( "$#" == 1 )); then
    projectDir=$1
fi

mavenGoals="clean package"
if [[ "$projectDir" == "quarkus-super-heroes"* ]]; then
    mavenGoals="clean package -DskipTests -DskipITs"
fi

echo Building "$projectDir" with "$mavenGoals"

# build the project
./"$projectDir"/mvnw $mavenGoals -f "$projectDir" -Dquarkus.platform.group-id=com.redhat.quarkus.platform -Dquarkus.platform.version=3.27.0.redhat-00002 -s settings.xml -Dmaven.repo.local=local-maven-repo -Predhat