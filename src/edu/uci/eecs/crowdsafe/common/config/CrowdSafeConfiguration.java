package edu.uci.eecs.crowdsafe.common.config;

import java.util.HashMap;
import java.util.Map;

public class CrowdSafeConfiguration {

	public static class Environment {

		public static final Environment CROWD_SAFE_COMMON_DIR = new Environment("CROWD_SAFE_COMMON_DIR");

		public final String name;

		public Environment(String name) {
			this.name = name;
		}
	}

	public static class OptionOverride {
		final Environment variable;
		final String value;

		public OptionOverride(Environment variable, String value) {
			this.variable = variable;
			this.value = value;
		}
	}

	public static CrowdSafeConfiguration getInstance() {
		return INSTANCE;
	}

	public static void initialize(Environment requiredEnvironment[], OptionOverride... options) {
		INSTANCE = new CrowdSafeConfiguration();
		INSTANCE.initializeImpl(requiredEnvironment, options);
	}

	protected static CrowdSafeConfiguration INSTANCE;

	public final Map<Environment, String> environmentValues = new HashMap<Environment, String>();

	protected void initializeImpl(Environment requiredEnvironment[], OptionOverride options[]) {
		for (OptionOverride option : options) {
			environmentValues.put(option.variable, option.value);
		}

		for (Environment variable : requiredEnvironment) {
			if (environmentValues.containsKey(variable))
				continue; // overridden above

			String value = System.getenv(variable.name);
			if (value == null)
				throw new IllegalStateException(String.format("Please configure the environment variable %s.",
						variable.name));
			environmentValues.put(variable, value);
		}
	}
}
