package edu.uci.eecs.crowdsafe.util.log;

import java.io.File;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

public class Log {

	public static class OutputException extends RuntimeException {
		public OutputException(Throwable t) {
			super(t);
		}
	}

	private static final List<PrintWriter> outputs = new ArrayList<PrintWriter>();

	public static void addOutput(OutputStream output) {
		outputs.add(new PrintWriter(output));
	}

	public static void addOutput(File file) {
		try {
			outputs.add(new PrintWriter(file));
		} catch (Throwable t) {
			throw new OutputException(t);
		}
	}

	public static void log() {
		log("");
	}

	public static void log(Object o) {
		log(o.toString(), "");
	}

	public static void log(String format, Object... args) {
		try {
			for (PrintWriter output : outputs) {
				output.println(String.format(format, args));
			}
		} catch (Throwable t) {
			throw new OutputException(t);
		}
	}
}
