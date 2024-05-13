package org.acme;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.List;

public class Field {
	private final String name;
	private final String type;
	private List<String> annotations;

	public Field(String name, String type, String... annotations) {
		this.name = name;
		this.type = type;
		this.annotations = List.of(annotations);
	}

	public void render(BufferedWriter writer) throws IOException {
		for (String annotation : annotations) {
			writer.write( '\t' );
			writer.write(annotation);
			writer.newLine();
		}
		writer.write( '\t' );
		writer.write( type );
		writer.write( ' ' );
		writer.write( name );
		writer.write( ';' );
		writer.newLine();
	}
}
