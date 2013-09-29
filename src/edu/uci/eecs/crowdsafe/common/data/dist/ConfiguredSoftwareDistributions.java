package edu.uci.eecs.crowdsafe.common.data.dist;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.uci.eecs.crowdsafe.common.config.CrowdSafeConfiguration;

public class ConfiguredSoftwareDistributions {

	public enum ClusterMode {
		UNIT,
		GROUP;
	}

	public static void initialize(ClusterMode clusterMode) {
		initialize(
				clusterMode,
				new File(new File(CrowdSafeConfiguration.getInstance().environmentValues
						.get(CrowdSafeConfiguration.Environment.CROWD_SAFE_COMMON_DIR)), "config"));
	}

	public static void initialize(ClusterMode clusterMode, File configDir) {
		INSTANCE = new ConfiguredSoftwareDistributions(clusterMode, configDir);
		INSTANCE.loadDistributions();
	}

	public static ConfiguredSoftwareDistributions getInstance() {
		return INSTANCE;
	}

	private static String getFilename(String unitName) {
		int dashIndex = unitName.lastIndexOf('-');
		if (dashIndex < 0)
			return unitName;
		return unitName.substring(0, dashIndex);
	}

	public static String getVersion(String unitName) {
		int dashIndex = unitName.lastIndexOf('-');
		if (dashIndex < 0)
			return "";
		return unitName.substring(dashIndex + 1);
	}

	private static ConfiguredSoftwareDistributions INSTANCE;

	public static final AutonomousSoftwareDistribution MAIN_PROGRAM = new AutonomousSoftwareDistribution(
			"<main-program>", "main-program");

	public final File configDir;
	public final ClusterMode clusterMode;
	public final Map<String, AutonomousSoftwareDistribution> distributions = new HashMap<String, AutonomousSoftwareDistribution>();
	public final Map<String, SoftwareDistributionUnit> unitsByName = new HashMap<String, SoftwareDistributionUnit>();
	public final Map<SoftwareDistributionUnit, AutonomousSoftwareDistribution> distributionsByUnit = new HashMap<SoftwareDistributionUnit, AutonomousSoftwareDistribution>();

	private ConfiguredSoftwareDistributions(ClusterMode clusterMode, File configDir) {
		this.clusterMode = clusterMode;
		this.configDir = configDir;
		distributions.put(MAIN_PROGRAM.name, MAIN_PROGRAM);
	}

	private void loadDistributions() {
		if (clusterMode == ClusterMode.UNIT) {
			AutonomousSoftwareDistribution unknown = new AutonomousSoftwareDistribution(
					SoftwareDistributionUnit.UNKNOWN.name, Collections.singleton(SoftwareDistributionUnit.UNKNOWN));
			distributionsByUnit.put(SoftwareDistributionUnit.UNKNOWN, unknown);
			return;
		}

		try {
			for (File configFile : configDir.listFiles()) {
				if (configFile.getName().endsWith(".asd")) {
					AutonomousSoftwareDistribution dist = AutonomousSoftwareDistributionLoader
							.loadDistribution(configFile);
					distributions.put(dist.name, dist);
				} else if (configFile.getName().endsWith(".ssm")) {
					List<AutonomousSoftwareDistribution> singletons = AutonomousSoftwareDistributionLoader
							.loadSingleton(configFile);
					for (AutonomousSoftwareDistribution singleton : singletons) {
						distributions.put(singleton.name, singleton);
					}
				}
			}

			for (AutonomousSoftwareDistribution distribution : distributions.values()) {
				for (SoftwareDistributionUnit unit : distribution.distributionUnits) {
					unitsByName.put(unit.name, unit);
					distributionsByUnit.put(unit, distribution);
				}
			}
		} catch (IOException e) {
			throw new IllegalStateException(String.format(
					"Error reading the autonomous software distribution configuration from %s!",
					configDir.getAbsolutePath()));
		}
	}

	public synchronized AutonomousSoftwareDistribution establishCluster(String name) {
		AutonomousSoftwareDistribution cluster = distributions.get(name);
		if (cluster == null) {
			cluster = new AutonomousSoftwareDistribution(name, name);
			distributions.put(name, cluster);
		}
		return cluster;
	}

	public synchronized SoftwareDistributionUnit establishUnit(String name) {
		SoftwareDistributionUnit existing = unitsByName.get(name);
		if (existing == null)
			existing = unitsByName.get(getFilename(name));
		if (existing != null)
			return existing;

		if (clusterMode == ClusterMode.UNIT) {
			AutonomousSoftwareDistribution unitCluster;
			if (name.endsWith(".exe"))
				unitCluster = MAIN_PROGRAM;
			else
				unitCluster = establishCluster(name);

			SoftwareDistributionUnit unit = new SoftwareDistributionUnit(name);
			unitsByName.put(unit.name, unit);
			distributionsByUnit.put(unit, unitCluster);
			unitCluster.distributionUnits.add(unit);
			return unit;
		} else {
			SoftwareDistributionUnit unit = new SoftwareDistributionUnit(name);
			AutonomousSoftwareDistribution main = distributions.get(MAIN_PROGRAM);
			unitsByName.put(unit.name, unit);
			distributionsByUnit.put(unit, main);
			main.distributionUnits.add(unit);
			return unit;
		}
	}
}
