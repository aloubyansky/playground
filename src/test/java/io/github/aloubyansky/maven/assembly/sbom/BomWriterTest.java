package io.github.aloubyansky.maven.assembly.sbom;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;

import org.cyclonedx.model.Bom;
import org.cyclonedx.model.Component;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BomWriterTest {

    @TempDir
    Path tempDir;

    @Test
    void writeJsonProducesValidCycloneDx() throws Exception {
        Path output = tempDir.resolve("test.cdx.json");
        BomWriter.writeJson(createMinimalBom(), output, true);

        String content = Files.readString(output);
        assertTrue(content.contains("\"bomFormat\" : \"CycloneDX\""));
        assertTrue(content.contains("\"specVersion\" : \"1.6\""));
    }

    @Test
    void writeXmlProducesValidCycloneDx() throws Exception {
        Path output = tempDir.resolve("test.cdx.xml");
        BomWriter.writeXml(createMinimalBom(), output);

        String content = Files.readString(output);
        assertTrue(content.contains("<bom"));
        assertTrue(content.contains("cyclonedx.org"));
    }

    @Test
    void jsonContainsComponents() throws Exception {
        Bom bom = createMinimalBom();
        Component comp = new Component();
        comp.setType(Component.Type.LIBRARY);
        comp.setName("test-lib");
        comp.setVersion("1.0");
        comp.setBomRef("test-lib-ref");
        bom.addComponent(comp);

        Path output = tempDir.resolve("test.cdx.json");
        BomWriter.writeJson(bom, output, true);

        String content = Files.readString(output);
        assertTrue(content.contains("test-lib"));
    }

    @Test
    void xmlContainsComponents() throws Exception {
        Bom bom = createMinimalBom();
        Component comp = new Component();
        comp.setType(Component.Type.LIBRARY);
        comp.setName("test-lib");
        comp.setVersion("1.0");
        comp.setBomRef("test-lib-ref");
        bom.addComponent(comp);

        Path output = tempDir.resolve("test.cdx.xml");
        BomWriter.writeXml(bom, output);

        String content = Files.readString(output);
        assertTrue(content.contains("test-lib"));
    }

    private static Bom createMinimalBom() {
        Bom bom = new Bom();
        bom.setSerialNumber("urn:uuid:00000000-0000-0000-0000-000000000000");
        return bom;
    }
}
