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

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((unit == null) ? 0 : unit.hashCode());
		result = prime * result + ((version == null) ? 0 : version.hashCode());
		return result;
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
		if (unit == null) {
			if (other.unit != null)
				return false;
		} else if (!unit.equals(other.unit))
			return false;
		if (version == null) {
			if (other.version != null)
				return false;
		} else if (!version.equals(other.version))
			return false;
		return true;
	}
}
