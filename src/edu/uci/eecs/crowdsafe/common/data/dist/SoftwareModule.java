package edu.uci.eecs.crowdsafe.common.data.dist;

public abstract class SoftwareModule {

	public static final String DYNAMORIO_MODULE_NAME = "|dynamorio|";
	public static final String DYNAMIC_MODULE_NAME = "|dynamic|";

	public static final String EMPTY_VERSION = "0-0-0";

	public final SoftwareUnit unit;

	protected SoftwareModule(SoftwareUnit unit) {
		this.unit = unit;
	}

	public boolean isEquivalent(SoftwareModule other) {
		return unit.equals(other.unit) && !unit.isDynamic;
	}

	@Override
	public int hashCode() {
		return unit.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		SoftwareModule other = (SoftwareModule) obj;
		if (!unit.equals(other.unit))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return unit.toString();
	}
}
