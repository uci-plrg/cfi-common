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

	private static ConfiguredSoftwareDistributions INSTANCE;

	public static final AutonomousSoftwareDistribution MAIN_PROGRAM = new AutonomousSoftwareDistribution(
			"<main-program>", "main-program");

	public final File configDir;
	public final ClusterMode clusterMode;
	public final Map<String, AutonomousSoftwareDistribution> distributions = new HashMap<String, AutonomousSoftwareDistribution>();
	public final Map<String, SoftwareUnit> unitsByName = new HashMap<String, SoftwareUnit>();
	public final Map<SoftwareUnit, AutonomousSoftwareDistribution> distributionsByUnit = new HashMap<SoftwareUnit, AutonomousSoftwareDistribution>();

	private ConfiguredSoftwareDistributions(ClusterMode clusterMode, File configDir) {
		this.clusterMode = clusterMode;
		this.configDir = configDir;

		if (clusterMode == ClusterMode.GROUP)
			distributions.put(MAIN_PROGRAM.name, MAIN_PROGRAM);
	}

	private void loadDistributions() {
		if (clusterMode == ClusterMode.UNIT) {
			AutonomousSoftwareDistribution dynamorioCluster = establishCluster(SoftwareUnit.DYNAMORIO_UNIT_NAME);
			installCluster(dynamorioCluster, SoftwareUnit.DYNAMORIO);
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
				for (SoftwareUnit unit : distribution.getUnits()) {
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

	public SoftwareUnit establishUnitByName(String unitName) {
		if (unitName.startsWith(SoftwareUnit.DYNAMIC_UNIT_NAME))
			throw new IllegalArgumentException(String.format("Name collision: file %s will look like a dynamic unit!",
					unitName));
		if (unitName.startsWith(SoftwareModule.DYNAMIC_MODULE_NAME))
			unitName = unitName.replace(SoftwareModule.DYNAMIC_MODULE_NAME, SoftwareUnit.DYNAMIC_UNIT_NAME);

		if (unitName.startsWith(SoftwareUnit.DYNAMORIO_UNIT_NAME))
			throw new IllegalArgumentException(String.format(
					"Name collision: file %s will look like the dynamorio module!", unitName));
		if (unitName.contains(SoftwareModule.DYNAMORIO_MODULE_NAME))
			unitName = unitName.replace(SoftwareModule.DYNAMORIO_MODULE_NAME, SoftwareUnit.DYNAMORIO_UNIT_NAME);

		return establishUnitByFileSystemName(unitName);
	}

	public synchronized SoftwareUnit establishUnitByFileSystemName(String name) {
		SoftwareUnit existing = unitsByName.get(name);
		if (existing != null)
			return existing;

		if (clusterMode == ClusterMode.UNIT) {
			AutonomousSoftwareDistribution unitCluster = establishCluster(name);
			SoftwareUnit unit = new SoftwareUnit(name, name.startsWith(SoftwareUnit.DYNAMIC_UNIT_NAME)
					|| name.startsWith(SoftwareUnit.DYNAMORIO_UNIT_NAME));
			installCluster(unitCluster, unit);
			return unit;
		} else {
			SoftwareUnit unit = new SoftwareUnit(name);
			AutonomousSoftwareDistribution main = distributions.get(MAIN_PROGRAM);
			installCluster(main, unit);
			return unit;
		}
	}

	private void installCluster(AutonomousSoftwareDistribution cluster, SoftwareUnit unit) {
		unitsByName.put(unit.name, unit);
		distributionsByUnit.put(unit, cluster);
		cluster.addUnit(unit);
	}
}
