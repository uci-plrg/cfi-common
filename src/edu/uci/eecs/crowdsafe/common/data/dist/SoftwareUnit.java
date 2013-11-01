package edu.uci.eecs.crowdsafe.common.data.dist;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SoftwareUnit {

	private static final Pattern FILENAME_PATTERN = Pattern.compile("^([^\\-]+)-([^\\-]+-[^\\-]+-[^\\-]+)$");

	public static final String DYNAMORIO_UNIT_NAME = "__dynamorio";
	public static final String DYNAMIC_UNIT_NAME = "__dynamic";
	public static final String STATIC_UNIT_NAME = "__static";
	public static final String ANONYMOUS_UNIT_NAME = "__anonymous";

	public static final SoftwareUnit DYNAMORIO = new SoftwareUnit("|dynamorio|-" + SoftwareModule.EMPTY_VERSION, true);
	public static final SoftwareUnit CLUSTER_BOUNDARY = new SoftwareUnit("|cluster_boundary|-"
			+ SoftwareModule.EMPTY_VERSION);

	public final String name;
	public final String filename;
	public final String version;
	public final boolean isAnonymous;

	SoftwareUnit(String name) {
		this(name, false);
	}

	SoftwareUnit(String name, boolean isAnonymous) {
		this.name = name;
		this.isAnonymous = isAnonymous;

		Matcher matcher = FILENAME_PATTERN.matcher(name);
		if (matcher.matches()) {
			filename = matcher.group(1);
			version = matcher.group(2);
		} else {
			filename = name;
			version = SoftwareModule.EMPTY_VERSION;
		}
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((name == null) ? 0 : name.hashCode());
		return result;
	}

	// 5% hot during load!
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		SoftwareUnit other = (SoftwareUnit) obj;
		if (!name.equals(other.name))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return name;
	}
}
