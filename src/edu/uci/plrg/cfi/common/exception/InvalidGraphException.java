package edu.uci.plrg.cfi.common.exception;

public class InvalidGraphException extends RuntimeException {
	public InvalidGraphException(Exception source) {
		super(source);
	}

	public InvalidGraphException(String format, Object... args) {
		super(String.format(format, args));
	}
}
