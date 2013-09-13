package edu.uci.eecs.crowdsafe.common.data.dist;

public abstract class SoftwareModule {

	public final SoftwareDistributionUnit unit;
	public final String version;

	protected SoftwareModule(SoftwareDistributionUnit unit, String version) {
		this.unit = unit;
		this.version = version;
	}

	public boolean isEquivalent(SoftwareModule other) {
		return unit.filename.equals(other.unit.filename) && version.equals(other.version) && !unit.isDynamic();
	}
}
