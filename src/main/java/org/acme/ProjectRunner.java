package org.acme;

import java.nio.file.Path;

public class ProjectRunner {

    public static void main(String[] args) throws Exception {

        ProjectGenerator.newInstance()
                .setProjectDirectory(Path.of(System.getProperty("user.home"))
                        .resolve("playground")
                        .resolve("test-project"))
                .setRegenerate(true)
                // Quarkus version
                .setQuarkusVersion("3.6.1")
                // Dependencies
                .addQuarkusExtension("quarkus-resteasy-reactive")
                .addQuarkusExtension("quarkus-hibernate-orm-panache")
                .addQuarkusExtension("quarkus-jdbc-postgresql")
                // Configuration
                .setApplicationProperty("quarkus.hibernate-orm.database.generation", "update")
                // Classes
                .generate(ctx -> {

                    for(int i = 0; i < 1000; ++i) {
                        ctx.newClassBuilder("ApplicationScopedBean" + (i + 1))
                                .setApplicationScoped();
                    }

                    for(int i = 0; i < 1600; ++i) {
                        ctx.newClassBuilder("Entity" + (i + 1))
                                .setPanacheEntity();
                    }
                });

        log("done");
    }

    private static void log(String m) {
        System.out.println(m);
    }
}
