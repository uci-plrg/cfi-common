package edu.uci.eecs.crowdsafe.common.data.dist;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class AutonomousSoftwareDistribution {
	public final String name;
	public final String id;

	private boolean isDynamic = false;
	private final Set<SoftwareUnit> units;

	AutonomousSoftwareDistribution(String name, String id) {
		this.name = name;
		this.id = id;

		units = new HashSet<SoftwareUnit>();
	}

	AutonomousSoftwareDistribution(String name, Set<SoftwareUnit> distributionUnits) {
		this.name = this.id = name;

		this.units = Collections.unmodifiableSet(distributionUnits);
	}
	
	public Iterable<SoftwareUnit> getUnits() {
		return units;
	}
	
	public boolean isDynamic() {
		return isDynamic;
	}

	void addUnit(SoftwareUnit unit) {
		if (units.isEmpty()) {
			isDynamic = unit.isDynamic;
		} else if (isDynamic != unit.isDynamic) {
			if (isDynamic)
				throw new IllegalArgumentException(String.format(
						"Attempt to add static software unit %s to a dynamic cluster.", unit));
			else
				throw new IllegalArgumentException(String.format(
						"Attempt to add dynamic software unit %s to a static cluster.", unit));
		}
		units.add(unit);
	}

	@Override
	public String toString() {
		return name;
	}
}
