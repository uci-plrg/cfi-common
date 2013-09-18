package edu.uci.eecs.crowdsafe.common.data.graph.cluster;

import edu.uci.eecs.crowdsafe.common.data.dist.SoftwareDistributionUnit;
import edu.uci.eecs.crowdsafe.common.data.dist.SoftwareModule;

public class ClusterModule extends SoftwareModule {

	public final int id;
	
	ClusterModule(int id, SoftwareDistributionUnit unit, String version) {
		super(unit, version);
		
		this.id = id;
	}
}
