package edu.uci.eecs.crowdsafe.common.data.graph.cluster;

import java.util.ArrayList;
import java.util.List;

import edu.uci.eecs.crowdsafe.common.data.dist.SoftwareDistributionUnit;

public class ClusterModuleList {

	private final List<ClusterModule> modules = new ArrayList<ClusterModule>();

	ClusterModule addModule(SoftwareDistributionUnit unit, String version) {
		ClusterModule module = new ClusterModule(modules.size(), unit, version);
		modules.add(module);
		return module;
	}

	public ClusterModule establishModule(SoftwareDistributionUnit unit, String version) {
		for (int i = 0; i < modules.size(); i++) {
			ClusterModule module = modules.get(i);
			if (module.unit.equals(unit) && module.version.equals(version))
				return module;
		}
		return addModule(unit, version);
	}
}
