package edu.uci.eecs.crowdsafe.common.util;

import gnu.getopt.Getopt;

import java.util.HashMap;
import java.util.Map;

public class OptionArgumentMap {

	public enum OptionMode {
		OPTIONAL,
		REQUIRED;
	}

	public static abstract class Option<Type> {
		public final Character id;
		final OptionMode mode;

		Option(Character id, OptionMode mode) {
			this.id = id;
			this.mode = mode;
		}

		public abstract boolean hasValue();

		public abstract Type getValue();

		void validate() {
			if ((mode == OptionMode.REQUIRED) && (getValue() == null))
				throw new IllegalStateException("Option '" + id + "' is required!");
		}

	}

	public static class StringOption extends Option<String> {
		String value = null;

		public StringOption(Character id) {
			super(id, OptionMode.OPTIONAL);
		}

		public StringOption(Character id, OptionMode mode) {
			super(id, mode);
		}

		public StringOption(Character id, String defaultValue) {
			super(id, OptionMode.OPTIONAL);

			this.value = defaultValue;
		}

		@Override
		public boolean hasValue() {
			return (value != null);
		}

		@Override
		public String getValue() {
			return value;
		}
	}

	public static class BooleanOption extends Option<Boolean> {
		Boolean value = false;

		public BooleanOption(Character id) {
			super(id, OptionMode.OPTIONAL);
		}

		public BooleanOption(Character id, OptionMode mode) {
			super(id, mode);
		}

		public BooleanOption(Character id, boolean defaultValue) {
			super(id, OptionMode.OPTIONAL);
			value = defaultValue;
		}

		@Override
		public boolean hasValue() {
			return value;
		}

		@Override
		public Boolean getValue() {
			return value;
		}
	}

	public static StringOption createStringOption(char c) {
		return new StringOption(c);
	}

	public static StringOption createStringOption(char c, OptionMode mode) {
		return new StringOption(c, mode);
	}

	public static StringOption createStringOption(char c, String defaultValue) {
		return new StringOption(c, defaultValue);
	}

	public static BooleanOption createBooleanOption(char c) {
		return new BooleanOption(c);
	}

	public static BooleanOption createBooleanOption(char c, OptionMode mode) {
		return new BooleanOption(c, mode);
	}

	public static BooleanOption createBooleanOption(char c, boolean defaultValue) {
		return new BooleanOption(c, defaultValue);
	}

	public static void populateOptions(ArgumentStack args, Option<?>... options) {
		new OptionArgumentMap(args, options).parseOptions();
	}

	private final ArgumentStack args;
	private final StringBuilder optionKey = new StringBuilder();
	private final Map<Character, Option<?>> map = new HashMap<Character, Option<?>>();

	public OptionArgumentMap(ArgumentStack args, Option<?>... options) {
		this.args = args;

		for (Option<?> option : options) {
			initializeOption(option);
		}
	}

	public OptionArgumentMap(ArgumentStack args, Iterable<Option<?>> options) {
		this.args = args;

		for (Option<?> option : options) {
			initializeOption(option);
		}
	}

	private void initializeOption(Option<?> option) {
		map.put(option.id, option);

		optionKey.append(option.id);
		if (option instanceof StringOption) {
			optionKey.append(":");
		}
	}

	public void parseOptions() {
		Getopt options = args.parseOptions(optionKey.toString());
		int c;
		while ((c = options.getopt()) != -1) {
			Option<?> option = map.get((char) c);
			if (option == null)
				throw new IllegalArgumentException("Unknown argument '" + (char) c + "'");
			if (option instanceof StringOption)
				((StringOption) option).value = options.getOptarg();
			else
				((BooleanOption) option).value = true;
		}

		for (Option<?> option : map.values()) {
			option.validate();
		}

		args.popOptions();
	}
}
