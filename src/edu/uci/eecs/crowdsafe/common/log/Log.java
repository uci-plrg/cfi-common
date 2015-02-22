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

	public enum Level {
		ERROR,
		WARNING,
		MESSAGE,
		DETAIL
	}

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
	private static Level activeLevel = Level.ERROR;

	public static void setLevel(Level level) {
		Log.activeLevel = level;
	}

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

	public static synchronized void addThreadOutput(File file) throws FileNotFoundException {
		if (threadLog == null) {
			threadLog = new ThreadLog();
		}
		threadLog.get().outputs.add(new PrintWriter(file));

		// System.out.println(String.format("Adding output to %s on thread %s", file.getName(), Thread.currentThread()
		// .getName()));
	}

	public static void clearOutputs() {
		sharedOutputs.clear();
	}

	public static void clearThreadOutputs() {
		if (threadLog != null)
			threadLog.get().outputs.clear();

		// System.out.println(String.format("Clearing thread output on thread %s", Thread.currentThread().getName()));
	}

	public static void log() {
		log("");
	}

	public static void log(Object o) {
		log(o == null ? "null" : o.toString(), "");
	}

	public static void log(String format, Object... args) {
		if (getOutputs().isEmpty()) {
			warnNoOutputs();
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

	public static void error(String format, Object... args) {
		log(Level.ERROR, format, args);
	}

	public static void warn(String format, Object... args) {
		log(Level.WARNING, format, args);
	}

	public static void message(String format, Object... args) {
		log(Level.MESSAGE, format, args);
	}

	public static void detail(String format, Object... args) {
		log(Level.DETAIL, format, args);
	}

	public static void spot(String format, Object... args) {
		log(format, args);
	}

	public static void log(Level level, String format, Object... args) {
		if (level.ordinal() <= activeLevel.ordinal())
			log(format, args);
	}

	public static void log(Throwable throwable) {
		if (getOutputs().isEmpty()) {
			warnNoOutputs();
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
			warnNoOutputs();
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
			warnNoOutputs();
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

	private static void warnNoOutputs() {
		System.out.println(String.format(
				"Warning: attempt to log without any outputs configured on thread %s. In shared list? %b.", Thread
						.currentThread().getName(), sharedLogThreads.contains(Thread.currentThread())));
		Thread.dumpStack();
	}
}
