package io.playground;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Objects;

import org.eclipse.aether.artifact.Artifact;

public abstract class DetectedReleasesFileWriter implements DetectedReleasesCallback {

	private final Path reportFile;
	private BufferedWriter writer;

	public DetectedReleasesFileWriter(String name) {
		this(Paths.get(name));
	}

	public DetectedReleasesFileWriter(Path p) {
		Objects.requireNonNull(p);
		reportFile = p;
	}

	protected BufferedWriter getWriter() {
		return writer;
	}

	protected void writeLine(Object line) throws IOException {
		writer.write(line.toString());
		writer.newLine();
	}

	@Override
	public void startBom(Artifact bomArtifact) {
		try {
			if (!Files.exists(reportFile)) {
				final Path parentDir = reportFile.isAbsolute() ? reportFile.getParent() : reportFile.normalize().toAbsolutePath().getParent();
				if(parentDir != null) {
				    Files.createDirectories(parentDir);
				}
			}
			writer = Files.newBufferedWriter(reportFile);
			writeStartBom(writer, bomArtifact);
		} catch (Exception e) {
			close();
			throw new IllegalStateException("Failed to init " + reportFile + " writer", e);
		}
	}

	protected abstract void writeStartBom(BufferedWriter writer, Artifact bomArtifact) throws IOException;

	@Override
	public void startReleaseOrigin(ReleaseOrigin releaseOrigin) {
		try {
			writeStartReleaseOrigin(writer, releaseOrigin);
		} catch(Exception e) {
			close();
			throw new IllegalStateException("Failed to write release origin " + releaseOrigin + " to " + reportFile, e);
		}
	}

	protected abstract void writeStartReleaseOrigin(BufferedWriter writer, ReleaseOrigin releaseOrigin) throws IOException;

	@Override
	public void endReleaseOrigin(ReleaseOrigin releaseOrigin) {
		try {
			writeEndReleaseOrigin(writer, releaseOrigin);
		} catch(Exception e) {
			close();
			throw new IllegalStateException("Failed to write release origin " + releaseOrigin + " to " + reportFile, e);
		}
	}

	protected abstract void writeEndReleaseOrigin(BufferedWriter writer, ReleaseOrigin releaseOrigin) throws IOException;

	@Override
	public void startReleaseVersion(ReleaseVersion releaseVersion, Collection<Artifact> artifacts) {
		try {
			writeStartReleaseVersion(writer, releaseVersion, artifacts);
		} catch (Exception e) {
			close();
			throw new IllegalStateException("Failed to write release version " + releaseVersion + " to " + reportFile, e);
		}
	}

	protected abstract void writeStartReleaseVersion(BufferedWriter writer, ReleaseVersion releaseVersion, Collection<Artifact> artifacts) throws IOException;

	@Override
	public void endReleaseVersion(ReleaseVersion releaseVersion) {
		try {
			writeEndReleaseVersion(writer, releaseVersion);
		} catch (Exception e) {
			close();
			throw new IllegalStateException("Failed to write release version " + releaseVersion + " to " + reportFile, e);
		}
	}

	protected abstract void writeEndReleaseVersion(BufferedWriter writer, ReleaseVersion releaseVersion) throws IOException;

	@Override
	public void endBom() {
		try {
			writeEndBom(writer);
		} catch (Exception e) {
			throw new IllegalStateException("Failed to write conclusion to " + reportFile, e);
		} finally {
			close();
		}
	}

	protected abstract void writeEndBom(BufferedWriter writer) throws IOException;

	private void close() {
		if(writer != null) {
			try {
				writer.close();
			} catch (IOException e) {
			}
		}
	}
}
