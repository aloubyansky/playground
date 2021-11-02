import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.jacoco.core.instr.Instrumenter;
import org.jacoco.core.runtime.OfflineInstrumentationAccessGenerator;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.Indexer;
import org.jboss.jandex.MethodInfo;
import org.junit.jupiter.api.Test;

public class JaCoCoInstrumentationTest {

	@Test
	void testJaCoCoInstrumentation() throws Exception {
		
		final byte[] originalBytes = readClassBytes(Example.class);
		
		assertJandexInfo(originalBytes);
		
        final Instrumenter instrumenter = new Instrumenter(new OfflineInstrumentationAccessGenerator());
        final byte[] instrumentedBytes = instrumenter.instrument(originalBytes, Example.class.getName());
        
        assertJandexInfo(instrumentedBytes);
	}

	private byte[] readClassBytes(Class<?> cls) throws IOException {
		final String examplePath = cls.getName().replace('.', '/') + ".class";
		final URL exampleUrl = Thread.currentThread().getContextClassLoader().getResource(examplePath);
		if(exampleUrl == null) {
			throw new IllegalStateException("Failed to locate " + examplePath + " on the classpath");
		}
		return Files.readAllBytes(Paths.get(exampleUrl.getPath()));
	}

	private void assertJandexInfo(final byte[] bytes) throws IOException {
		final ClassInfo classInfo = toJandexClassInfo(bytes);
		assertThat(classInfo.name().toString()).isEqualTo(Example.class.getName());
		final MethodInfo callme = classInfo.methods().stream().filter(m -> m.name().equals("callme")).findFirst().get();
		assertThat(callme).isNotNull();
		assertThat(callme.parameters().size()).isEqualTo(1);
		assertThat(callme.parameterName(0)).isEqualTo("greeting");
	}

	private ClassInfo toJandexClassInfo(byte[] bytes) throws IOException {
		try(ByteArrayInputStream is = new ByteArrayInputStream(bytes)) {
		    return new Indexer().index(is);
		}
	}
}
