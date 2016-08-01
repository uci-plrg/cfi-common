package edu.uci.plrg.cfi.common.exception;

public class TagNotFoundException extends RuntimeException {
	public TagNotFoundException(String format, Object... args) {
		super(String.format(format, args));
	}
}
