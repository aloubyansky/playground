package org.acme;

import java.nio.file.Path;

public class ProjectRunner {

    public static void main(String[] args) throws Exception {

        ProjectGenerator.newInstance()
                .setProjectDirectory(Path.of(System.getProperty("user.home"))
                        .resolve("playground")
                        .resolve("test-project"))
                .setRegenerate(true)
                .setQuarkusVersion("3.6.1")
                .addQuarkusExtension("quarkus-resteasy-reactive")
                .generate(ctx -> {

                    for(int i = 0; i < 2000; ++i) {
                        ctx.newClassBuilder("ClassNumber" + i)
                                .addClassAnnotation("jakarta.enterprise.context.ApplicationScoped");
                    }
                    var cat = ctx.newClassBuilder("Cat")
                            .addClassAnnotation("jakarta.enterprise.context.ApplicationScoped");
                    var dog = ctx.newClassBuilder("Dog")
                            .addClassAnnotation("jakarta.enterprise.context.ApplicationScoped");
                });
    }

    private static void log(String m) {
        System.out.println(m);
    }
}
