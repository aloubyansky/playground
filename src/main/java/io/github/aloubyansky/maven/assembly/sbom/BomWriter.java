package io.github.aloubyansky.maven.assembly.sbom;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.cyclonedx.Version;
import org.cyclonedx.exception.GeneratorException;
import org.cyclonedx.generators.BomGeneratorFactory;
import org.cyclonedx.generators.json.BomJsonGenerator;
import org.cyclonedx.generators.xml.BomXmlGenerator;
import org.cyclonedx.model.Bom;

/**
 * Serializes a CycloneDX {@link Bom} to disk in JSON or XML format.
 *
 * <p>
 * All output uses the CycloneDX 1.6 specification version. The class is
 * a stateless utility and cannot be instantiated.
 * </p>
 */
public final class BomWriter {

    private BomWriter() {
    }

    /**
     * Writes the given BOM as a CycloneDX 1.6 JSON document.
     *
     * @param bom the BOM model to serialize
     * @param output the file path to write to (created or overwritten)
     * @param prettyPrint whether to indent the output for readability
     * @throws IOException if the file cannot be written
     * @throws GeneratorException if the BOM model cannot be converted to JSON
     */
    public static void writeJson(Bom bom, Path output, boolean prettyPrint)
            throws IOException, GeneratorException {
        BomJsonGenerator generator = BomGeneratorFactory.createJson(Version.VERSION_16, bom);
        Files.writeString(output, generator.toJsonString(prettyPrint), StandardCharsets.UTF_8);
    }

    /**
     * Writes the given BOM as a CycloneDX 1.6 XML document.
     * The output is always indented.
     *
     * @param bom the BOM model to serialize
     * @param output the file path to write to (created or overwritten)
     * @throws IOException if the file cannot be written
     * @throws GeneratorException if the BOM model cannot be converted to XML
     */
    public static void writeXml(Bom bom, Path output)
            throws IOException, GeneratorException {
        BomXmlGenerator generator = BomGeneratorFactory.createXml(Version.VERSION_16, bom);
        Files.writeString(output, generator.toXmlString(), StandardCharsets.UTF_8);
    }
}
