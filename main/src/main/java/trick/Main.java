package trick;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {

	public static void main(String[] args) throws Exception {
		loadInstance("green");
		loadInstance("blue");
		loadInstance("red");
	}

	private static void loadInstance(String name) throws Exception {
		log("---");
		log("Loading " + name);
		final Path trickOrigin = getTrickOrigin(name);		
		log("From " + trickOrigin);
		final Trick trick = Exterior.getInstance(Trick.class, trickOrigin);
		log("What are you?: " + trick.whatAreYou());
		log("Info: " + trick.getInfo());
	}
	
	private static Path getTrickOrigin(String name) {
		Path base = Paths.get("").normalize().toAbsolutePath().getParent().getParent().resolve(name);
		if(!Files.exists(base)) {
			throw new IllegalStateException("Failed to resolve origin for trick " + name + " at " + base);
		}
		base = base.resolve("target").resolve("classes");
		if(!Files.exists(base)) {
			throw new IllegalStateException("Failed to resolve classes directory for trick " + name + " at " + base);
		}
		return base;
	}
	
	private static void log(Object o) {
		System.out.println(o == null ? "null" : o);
	}
}
