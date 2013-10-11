package edu.uci.eecs.crowdsafe.common.data.dist;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class AutonomousSoftwareDistribution {
	public final String name;
	public final String id;
	public final Set<SoftwareUnit> distributionUnits;

	AutonomousSoftwareDistribution(String name, String id) {
		this.name = name;
		this.id = id;
		distributionUnits = new HashSet<SoftwareUnit>();
	}

	AutonomousSoftwareDistribution(String name, Set<SoftwareUnit> distributionUnits) {
		this.name = this.id = name;
		this.distributionUnits = Collections.unmodifiableSet(distributionUnits);
	}

	@Override
	public String toString() {
		return name;
	}
}
