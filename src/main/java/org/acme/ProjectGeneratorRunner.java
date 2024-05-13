package org.acme;

import java.nio.file.Path;

public class ProjectGeneratorRunner {

    public static void main(String[] args) throws Exception {

        ProjectGenerator.newInstance()
                .setProjectDirectory(Path.of(System.getProperty("user.home"))
                        .resolve("playground")
                        .resolve("test-project"))
                // remove and re-generate the project
                .setRegenerate(true)
                // Quarkus version
                .setQuarkusPlatformGroupId("io.quarkus")
                .setQuarkusPlatformVersion("999-SNAPSHOT")
                //.setQuarkusPlatformVersion("3.6.1")
                // Dependencies
                .addQuarkusExtension("quarkus-rest")
                .addQuarkusExtension("quarkus-hibernate-orm-panache")
                .addQuarkusExtension("quarkus-jdbc-postgresql")
                // Configuration
                .setApplicationProperty("quarkus.hibernate-orm.database.generation", "none")
                .setApplicationProperty("quarkus.hibernate-orm.validate-in-dev-mode", "false")
                // Classes
                .generate(project -> {

                    for(int i = 0; i < 1000; ++i) {
                        project.newClassBuilder("ApplicationScopedBean" + (i + 1))
                                .setApplicationScoped();
                    }

                    for(int i = 0; i < 1600; ++i) {
                        project.newClassBuilder("Entity" + (i + 1))
                                .addField("firstname", "String", "@jakarta.persistence.Basic")
                                .setPanacheEntity();
                    }
                });

        log("done");
    }

    private static void log(String m) {
        System.out.println(m);
    }
}
