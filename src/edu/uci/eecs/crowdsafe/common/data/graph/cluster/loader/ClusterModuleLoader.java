package edu.uci.eecs.crowdsafe.common.data.graph.cluster.loader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import edu.uci.eecs.crowdsafe.common.data.dist.AutonomousSoftwareDistribution;
import edu.uci.eecs.crowdsafe.common.data.dist.ConfiguredSoftwareDistributions;
import edu.uci.eecs.crowdsafe.common.data.dist.SoftwareDistributionUnit;
import edu.uci.eecs.crowdsafe.common.data.graph.cluster.ClusterModuleList;
import edu.uci.eecs.crowdsafe.common.datasource.cluster.ClusterTraceDataSource;
import edu.uci.eecs.crowdsafe.common.datasource.cluster.ClusterTraceStreamType;

public class ClusterModuleLoader {

	private final ClusterTraceDataSource dataSource;

	ClusterModuleLoader(ClusterTraceDataSource dataSource) {
		this.dataSource = dataSource;
	}

	ClusterModuleList loadModules(AutonomousSoftwareDistribution cluster, ClusterTraceDataSource dataSource)
			throws IOException {
		ClusterModuleList modules = new ClusterModuleList();

		BufferedReader input = new BufferedReader(new InputStreamReader(dataSource.getDataInputStream(cluster,
				ClusterTraceStreamType.MODULE)));

		while (input.ready()) {
			String moduleLine = input.readLine();
			if (moduleLine.length() == 0)
				continue;

			int lastDash = moduleLine.lastIndexOf('-');
			String moduleName = moduleLine.substring(0, lastDash);
			SoftwareDistributionUnit unit = ConfiguredSoftwareDistributions.getInstance().establishUnit(moduleName);
			String version = moduleLine.substring(lastDash + 1);
			modules.addModule(unit, version);
		}

		return modules;
	}
}
