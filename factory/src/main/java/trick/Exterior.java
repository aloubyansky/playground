package trick;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;

public class Exterior {

	private static final String SERVICE_PATH = "META-INF/services/" + ExteriorInstanceProvider.class.getName();
	
	public static <T> T getInstance(Class<T> type) throws Exception {
		return getInstance(type, Thread.currentThread().getContextClassLoader());
	}
	
	public static <T> T getInstance(Class<T> type, Path p) throws Exception {
		URLClassLoader ucl = null;
		try {
			ucl = new URLClassLoader(new URL[] { p.toUri().toURL() }, Thread.currentThread().getContextClassLoader());
		} catch (MalformedURLException e) {
			throw new IllegalStateException("Failed to translate " + p + " to URL", e);
		}
		return getInstance(type, ucl);
	}

	public static <T> T getInstance(Class<T> type, ClassLoader parent) throws Exception {
		final ClassLoader tccl = Thread.currentThread().getContextClassLoader();
		//final Set<URL> parentUrls = new HashSet<>();
		//collectParentUrls(tccl, parentUrls);
		final List<URL> urls = new ArrayList<>(1);
		final Enumeration<URL> providers = tccl.getResources(SERVICE_PATH);
		while(providers.hasMoreElements()) {
			URL url = providers.nextElement();
			if(url.getProtocol().equals("jar")) {
				url = new URL(url.getFile().substring(0, url.getFile().length() - SERVICE_PATH.length() - 2));
			} else {
				url = new URL(url.getProtocol(), url.getHost(), url.getFile().substring(0, url.getFile().length() - SERVICE_PATH.length()));
			}
			//if(!parentUrls.contains(url)) {
				urls.add(url);
				System.out.println("URL: " + url);
			//}
    	}
		if(urls.isEmpty()) {
			throw new IllegalStateException("Failed to locate implementations of " + ExteriorInstanceProvider.class.getName() + " on the classpath");
		}
		try (URLClassLoader servicesCl = new URLClassLoader(urls.toArray(new URL[urls.size()]), null)) {
			final ExteriorClassLoader trickCl = new ExteriorClassLoader(servicesCl, parent);
			Thread.currentThread().setContextClassLoader(trickCl);		
			final ServiceLoader<ExteriorInstanceProvider> sl = ServiceLoader.load(ExteriorInstanceProvider.class, trickCl);
			final Iterator<ExteriorInstanceProvider> i = sl.iterator();
			if(!i.hasNext()) {
				throw new IllegalStateException("Failed to locate implementations of " + ExteriorInstanceProvider.class.getName() + " on the classpath");
			}
			for(ExteriorInstanceProvider s : sl) {
				if(s.supports(type)) {
					return s.getInstance(type);
				}
			}
			throw new IllegalStateException("Failed to load Trick");
		} finally {
			Thread.currentThread().setContextClassLoader(tccl);
		}
	}
	
	private static void collectParentUrls(ClassLoader cl, Set<URL> urls) {
		if(cl.getParent() != null) {
			collectParentUrls(cl.getParent(), urls);
		}
		if(!(cl instanceof URLClassLoader)) {
			return;
		}
		for(URL url : ((URLClassLoader)cl).getURLs()) {
			urls.add(url);
		}
	}
}
