package edu.uci.eecs.crowdsafe.common.config;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

import edu.uci.eecs.crowdsafe.common.config.CrowdSafeConfiguration.Environment;

public class CrowdSafeConfiguration {

	public enum Environment {
		CROWD_SAFE_MERGE_DIR("CROWD_SAFE_MERGE_DIR"),
		CROWD_SAFE_COMMON_DIR("CROWD_SAFE_COMMON_DIR");

		public final String name;

		private Environment(String name) {
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

	public static void initialize(Set<Environment> requiredEnvironment, OptionOverride... options) {
		INSTANCE = new CrowdSafeConfiguration();
		INSTANCE.initializeImpl(requiredEnvironment, options);
	}

	private static CrowdSafeConfiguration INSTANCE;

	public final Map<Environment, String> environmentValues = new EnumMap<Environment, String>(Environment.class);

	private void initializeImpl(Set<Environment> requiredEnvironment, OptionOverride options[]) {
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
