package io.playground;

@SuppressWarnings("serial")
public class BomDecomposerException extends Exception {

	public BomDecomposerException(String message) {
		super(message);
	}

	public BomDecomposerException(String msg, Throwable cause) {
		super(msg, cause);
	}
}
