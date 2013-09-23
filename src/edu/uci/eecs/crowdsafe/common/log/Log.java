package edu.uci.eecs.crowdsafe.common.log;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Log {

	public static class OutputException extends RuntimeException {
		public OutputException(Throwable t) {
			super(t);
		}
	}

	private static class ThreadOutput {
		final List<PrintWriter> outputs = new ArrayList<PrintWriter>();
	}

	private static class ThreadLog extends ThreadLocal<ThreadOutput> {
		@Override
		protected ThreadOutput initialValue() {
			return new ThreadOutput();
		}
	}

	private static final List<PrintWriter> sharedOutputs = new ArrayList<PrintWriter>();
	private static Set<Thread> sharedLogThreads = new HashSet<Thread>();
	private static ThreadLog threadLog = null;

	public static void addOutput(OutputStream output) {
		sharedLogThreads.add(Thread.currentThread());

		sharedOutputs.add(new PrintWriter(output));
	}

	public static void addOutput(File file) {
		sharedLogThreads.add(Thread.currentThread());

		try {
			sharedOutputs.add(new PrintWriter(file));
		} catch (Throwable t) {
			throw new OutputException(t);
		}
	}

	public static void addThreadOutput(File file) throws FileNotFoundException {
		if (threadLog == null) {
			threadLog = new ThreadLog();
		}
		threadLog.get().outputs.add(new PrintWriter(file));
	}

	public static void clearOutputs() {
		sharedOutputs.clear();
	}

	public static void clearThreadOutputs() {
		if (threadLog != null)
			threadLog.get().outputs.clear();
	}

	public static void log() {
		log("");
	}

	public static void log(Object o) {
		log(o == null ? "null" : o.toString(), "");
	}

	public static void log(String format, Object... args) {
		if (getOutputs().isEmpty()) {
			System.out.println("Warning: attempt to log without any outputs configured.");
			return;
		}

		try {
			for (PrintWriter output : getOutputs()) {
				output.format(format, args);
				output.println();
				output.flush();
			}
		} catch (Throwable t) {
			throw new OutputException(t);
		}
	}

	public static void log(Throwable throwable) {
		if (getOutputs().isEmpty()) {
			System.out.println("Warning: attempt to log without any outputs configured.");
			return;
		}

		try {
			for (PrintWriter output : getOutputs()) {
				throwable.printStackTrace(output);
				output.flush();
			}
		} catch (Throwable t) {
			throw new OutputException(t);
		}
	}

	public static void sharedLog(String format, Object... args) {
		if (sharedOutputs.isEmpty()) {
			System.out.println("Warning: attempt to log without any outputs configured.");
			return;
		}

		try {
			for (PrintWriter output : sharedOutputs) {
				output.format(format, args);
				output.println();
				output.flush();
			}
		} catch (Throwable t) {
			throw new OutputException(t);
		}
	}

	public static void sharedLog(Throwable throwable) {
		if (sharedOutputs.isEmpty()) {
			System.out.println("Warning: attempt to log without any outputs configured.");
			return;
		}

		try {
			for (PrintWriter output : sharedOutputs) {
				throwable.printStackTrace(output);
				output.flush();
			}
		} catch (Throwable t) {
			throw new OutputException(t);
		}
	}

	
	public static void closeOutputs() {
		try {
			for (PrintWriter output : getOutputs()) {
				output.close();
			}
		} catch (Throwable t) {
			throw new OutputException(t);
		}
	}

	private static List<PrintWriter> getOutputs() {
		if ((threadLog == null) || sharedLogThreads.contains(Thread.currentThread())) {
			return sharedOutputs;
		} else {
			return threadLog.get().outputs;
		}
	}
}
