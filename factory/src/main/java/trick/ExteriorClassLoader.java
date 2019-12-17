package trick;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;

public class ExteriorClassLoader extends ClassLoader {

	private final ClassLoader servicesCl;
	
	public ExteriorClassLoader(ClassLoader servicesCl, ClassLoader parent) {
		super(parent);
		this.servicesCl = servicesCl;
	}
	
	@Override
	public URL getResource(String name) {
		return servicesCl.getResource(name);
	}

	@Override
	public Enumeration<URL> getResources(String name) throws IOException {
		return servicesCl.getResources(name);
	}

	@Override
	public InputStream getResourceAsStream(String name) {
		return servicesCl.getResourceAsStream(name);
	}
	
	@Override
	protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
		if(name.startsWith("java.") || name.startsWith("javax.")) {
			return super.loadClass(name, resolve);
		}
		try {
			return findClass(name);
		} catch(ClassNotFoundException e) {
			// ignore
		} catch (Error e) {
			// potential race conditions if another thread is loading the same class
			final Class<?> existing = findLoadedClass(name);
			if (existing != null) {
				return existing;
			}
		}
		return super.loadClass(name, resolve);
	}

	@Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        Class<?> existing = findLoadedClass(name);
        if (existing != null) {
            return existing;
        }
        final String resourcePath = name.replace('.', '/') + ".class";
        final InputStream is = getResourceAsStream(resourcePath);
        if(is == null) {
        	throw new ClassNotFoundException(name);
        }
        final byte[] bytes;
        try {
        	bytes = readAll(is);
        } catch (IOException e) {
        	throw new ClassNotFoundException("Failed to read class file " + resourcePath, e);
		} finally {
        	try {
				is.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
        }
        return defineClass(name, bytes, 0, bytes.length);
    }

	private static byte[] readAll(InputStream is) throws IOException {
		ByteArrayOutputStream buffer = new ByteArrayOutputStream();

		int nRead;
		byte[] data = new byte[16384];

		while ((nRead = is.read(data, 0, data.length)) != -1) {
			buffer.write(data, 0, nRead);
		}

		return buffer.toByteArray();
	}
}
