package org.acme;

import io.quarkus.blend.QuarkusBlend;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;

public class QuarkusBlendTest {

    /**
     * Build an app including the test classes
     * @throws Exception
     */
    @Test
    public void test() throws Exception {

        Path app = QuarkusBlend.builder()
                // the classes directory
                .addClasses(Path.of("target/test-classes"))
                // configure the quarkus-bom version and what comes from it
                .withQuarkus(quarkus -> {
                    quarkus.setVersion("3.17.7");
                    quarkus.addExtension("quarkus-rest");
                })
                // other platform BOMs
                //.withPlatformMember("quarkus-camel-bom", cq -> cq.addExtension("camel-quarkus-amqp"))
                // the output directory
                .setOutputDir(Path.of("target/blend-output"))
                .build()
                .getExecutable();

        var appProcess = new ProcessBuilder().command("java", "-jar", app.toAbsolutePath().toString()).inheritIO().start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> terminate(appProcess)));
        appProcess.waitFor();
    }

    private static void terminate(Process process) {
        if(!process.isAlive()) {
            return;
        }
        try {
            process.getInputStream().readAllBytes();
        } catch (IOException e) {
        }
        process.destroy();
    }
}
