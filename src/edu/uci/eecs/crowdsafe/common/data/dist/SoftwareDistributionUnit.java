package edu.uci.eecs.crowdsafe.common.data.dist;

public class SoftwareDistributionUnit {

	public static final SoftwareDistributionUnit UNKNOWN = new SoftwareDistributionUnit("__unknown__");
	public static final SoftwareDistributionUnit CLUSTER_BOUNDARY = new SoftwareDistributionUnit("__cluster_boundary__");

	public final String name;
	public final String filename;

	SoftwareDistributionUnit(String name) {
		this.name = name;

		int lastDash = name.lastIndexOf('-');
		if (lastDash < 0) {
			filename = name;
		} else {
			filename = name.substring(0, lastDash);
		}
	}
	
	public boolean isDynamic() {
		return (this == UNKNOWN) || filename.endsWith(".cpl");
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
		SoftwareDistributionUnit other = (SoftwareDistributionUnit) obj;
		if (name == null) {
			if (other.name != null)
				return false;
		} else if (!name.equals(other.name))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return name;
	}
}
