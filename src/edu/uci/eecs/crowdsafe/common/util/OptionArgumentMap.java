package edu.uci.eecs.crowdsafe.common.util;

import gnu.getopt.Getopt;

import java.util.HashMap;
import java.util.Map;

public class OptionArgumentMap {

	public static abstract class Option<Type> {
		public final Character id;

		Option(Character id) {
			this.id = id;
		}

		abstract Type getValue();
	}

	public static class StringOption extends Option<String> {
		String value = null;

		public StringOption(Character id) {
			super(id);
		}

		@Override
		public String getValue() {
			return value;
		}
	}

	public static class BooleanOption extends Option<Boolean> {
		Boolean value = false;

		public BooleanOption(Character id) {
			super(id);
		}

		@Override
		public Boolean getValue() {
			return value;
		}
	}

	public static StringOption createStringOption(char c) {
		return new StringOption(c);
	}

	public static BooleanOption createBooleanOption(char c) {
		return new BooleanOption(c);
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
		args.popOptions();
	}
}
