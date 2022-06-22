package org.acme;

import java.io.BufferedWriter;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.quarkus.fs.util.ZipUtils;

public class JarCreationTest {

    public static final String TRANSFORMED_BYTECODE_JAR = "transformed-bytecode.jar";

	protected Path getTargetDir() {
		return Path.of("target").normalize().toAbsolutePath();
	}
	
	@Test
	void createJarWithZipUtils() throws Exception {
		Path jar = getTargetDir().resolve("created-with-zip-utils.jar");
		ensureDoesNotExist(jar);
		try(FileSystem fs = ZipUtils.newZip(jar)) {
			writeContent(fs);
		}
		Assertions.assertTrue(Files.exists(jar));
	}

	@Test
	void transformedBytecodeWithZipUtils() throws Exception {
		Path jar = getTargetDir().resolve(TRANSFORMED_BYTECODE_JAR);
		ensureDoesNotExist(jar);
		try(FileSystem fs = ZipUtils.newZip(jar)) {
			writeContent(fs);
		}
		Assertions.assertTrue(Files.exists(jar));
	}

	@Test
	void createWithFileSystemsApi() throws Exception {
		Path jar = getTargetDir().resolve("created-with-filesystems.jar");
		ensureDoesNotExist(jar);
		try(FileSystem fs = newJar(jar)) {
			writeContent(fs);
		}
		Assertions.assertTrue(Files.exists(jar));
	}

	@Test
	void transformedBytecodeWithFileSystemsApi() throws Exception {
		Path jar = getTargetDir().resolve(TRANSFORMED_BYTECODE_JAR);
		ensureDoesNotExist(jar);
		try(FileSystem fs = newJar(jar)) {
			writeContent(fs);
		}
		Assertions.assertTrue(Files.exists(jar));
	}

	private void ensureDoesNotExist(Path jar) throws IOException {
		if(Files.exists(jar)) {
			Files.delete(jar);
		}
		Assertions.assertFalse(Files.exists(jar));
	}

	private void writeContent(FileSystem fs) throws IOException {
		try(BufferedWriter writer = Files.newBufferedWriter(fs.getPath("content"))) {
			writer.write("test");
		}
	}
	
	private static FileSystem newJar(Path zipFile) throws IOException {
        final Map<String, ?> env;
        if (Files.exists(zipFile)) {
            env = Map.of();
        } else {
            env = Map.of("create", "true");
        }
        return FileSystems.newFileSystem(toZipUri(zipFile), env);
    }
	
	public static URI toZipUri(Path zipFile) throws IOException {
        URI zipUri = zipFile.toUri();
        try {
            zipUri = new URI("jar:" + zipUri.getScheme(), zipUri.getPath(), null);
        } catch (URISyntaxException e) {
            throw new IOException("Failed to create a JAR URI for " + zipFile, e);
        }
        return zipUri;
	}
}
