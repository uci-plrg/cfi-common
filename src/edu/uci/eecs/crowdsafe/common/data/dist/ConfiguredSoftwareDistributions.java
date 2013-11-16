package edu.uci.eecs.crowdsafe.common.data.dist;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
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

	private static final String BLACK_BOX_OWNER_FILENAME = "anonymous-blackbox-owners.cfg";

	public static final AutonomousSoftwareDistribution MAIN_PROGRAM = new AutonomousSoftwareDistribution(
			"<main-program>", "main-program");
	public static final AutonomousSoftwareDistribution DYNAMORIO_CLUSTER = new AutonomousSoftwareDistribution(
			SoftwareUnit.DYNAMORIO_UNIT_NAME, SoftwareUnit.DYNAMORIO_UNIT_NAME);
	public static final AutonomousSoftwareDistribution ANONYMOUS_CLUSTER = new AutonomousSoftwareDistribution(
			SoftwareUnit.ANONYMOUS_UNIT_NAME, SoftwareUnit.ANONYMOUS_UNIT_NAME, true);

	public final File configDir;
	public final ClusterMode clusterMode;
	public final Map<String, AutonomousSoftwareDistribution> distributions = new HashMap<String, AutonomousSoftwareDistribution>();
	public final Map<String, SoftwareUnit> unitsByName = new HashMap<String, SoftwareUnit>();
	public final Map<Long, SoftwareUnit> unitsByAnonymousEntryHash = new HashMap<Long, SoftwareUnit>();
	public final Map<Long, SoftwareUnit> unitsByAnonymousExitHash = new HashMap<Long, SoftwareUnit>();
	public final Map<Long, SoftwareUnit> unitsByInterceptionHash = new HashMap<Long, SoftwareUnit>();
	public final Map<SoftwareUnit, AutonomousSoftwareDistribution> distributionsByUnit = new HashMap<SoftwareUnit, AutonomousSoftwareDistribution>();
	public final List<SoftwareUnit> anonymousBlackBoxOwners = new ArrayList<SoftwareUnit>();

	private ConfiguredSoftwareDistributions(ClusterMode clusterMode, File configDir) {
		this.clusterMode = clusterMode;
		this.configDir = configDir;

		if (clusterMode == ClusterMode.GROUP) {
			distributions.put(MAIN_PROGRAM.name, MAIN_PROGRAM);
		} else {
			distributions.put(DYNAMORIO_CLUSTER.name, DYNAMORIO_CLUSTER);
			installCluster(DYNAMORIO_CLUSTER, SoftwareUnit.DYNAMORIO);
		}
	}

	private void loadAnonymousBlackBoxOwners() throws IOException {
		File blackBoxConfigFile = new File(configDir, BLACK_BOX_OWNER_FILENAME);
		if (blackBoxConfigFile.exists() && blackBoxConfigFile.isFile()) {
			BufferedReader input = new BufferedReader(new FileReader(blackBoxConfigFile));
			try {
				String line;
				while ((line = input.readLine()) != null) {
					SoftwareUnit ownerModuleName = establishUnitByFileSystemName(line);
					anonymousBlackBoxOwners.add(ownerModuleName);
				}
			} finally {
				input.close();
			}
		}
	}

	private void loadDistributions() {
		if (clusterMode == ClusterMode.UNIT) {
			installCluster(DYNAMORIO_CLUSTER, SoftwareUnit.DYNAMORIO);
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
					unitsByAnonymousEntryHash.put(unit.anonymousEntryHash, unit);
					unitsByAnonymousExitHash.put(unit.anonymousExitHash, unit);
					unitsByInterceptionHash.put(unit.interceptionHash, unit);
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
			if (name.equals(ANONYMOUS_CLUSTER.name))
				cluster = ANONYMOUS_CLUSTER;
			else
				cluster = new AutonomousSoftwareDistribution(name, name);
			distributions.put(name, cluster);
		}
		return cluster;
	}

	public SoftwareUnit establishUnitByName(String unitName) {
		if (unitName.startsWith(SoftwareModule.ANONYMOUS_MODULE_NAME))
			unitName = unitName.replace(SoftwareModule.ANONYMOUS_MODULE_NAME, SoftwareUnit.ANONYMOUS_UNIT_NAME);

		if (unitName.startsWith(SoftwareUnit.DYNAMORIO_UNIT_NAME)
				|| unitName.contains(SoftwareModule.DYNAMORIO_MODULE_NAME))
			throw new IllegalArgumentException("The DynamoRIO software unit is static!");

		/*
		 * if (unitName.startsWith(SoftwareUnit.DYNAMORIO_UNIT_NAME)) throw new IllegalArgumentException(String.format(
		 * "Name collision: file %s will look like the dynamorio module!", unitName)); if
		 * (unitName.contains(SoftwareModule.DYNAMORIO_MODULE_NAME)) unitName =
		 * unitName.replace(SoftwareModule.DYNAMORIO_MODULE_NAME, SoftwareUnit.DYNAMORIO_UNIT_NAME);
		 */

		return establishUnitByFileSystemName(unitName);
	}

	public synchronized SoftwareUnit establishUnitByFileSystemName(String name) {
		if (name.startsWith(SoftwareUnit.DYNAMORIO_UNIT_NAME))
			return SoftwareUnit.DYNAMORIO;

		SoftwareUnit existing = unitsByName.get(name);
		if (existing != null)
			return existing;

		if (clusterMode == ClusterMode.UNIT) {
			AutonomousSoftwareDistribution unitCluster = establishCluster(name);
			boolean isDynamic = name.startsWith(SoftwareUnit.ANONYMOUS_UNIT_NAME);
			SoftwareUnit unit = new SoftwareUnit(name, isDynamic);
			installCluster(unitCluster, unit);
			return unit;
		} else {
			SoftwareUnit unit = new SoftwareUnit(name);
			AutonomousSoftwareDistribution main = distributions.get(MAIN_PROGRAM);
			installCluster(main, unit);
			return unit;
		}
	}

	public AutonomousSoftwareDistribution getClusterByAnonymousEntryHash(long hash) {
		SoftwareUnit unit = unitsByAnonymousEntryHash.get(hash);
		if (unit == null)
			return null;
		else
			return distributionsByUnit.get(unit);
	}

	public AutonomousSoftwareDistribution getClusterByAnonymousExitHash(long hash) {
		SoftwareUnit unit = unitsByAnonymousExitHash.get(hash);
		if (unit == null)
			return null;
		else
			return distributionsByUnit.get(unit);
	}

	public AutonomousSoftwareDistribution getClusterByInterceptionHash(long hash) {
		SoftwareUnit unit = unitsByInterceptionHash.get(hash);
		if (unit == null)
			return null;
		else
			return distributionsByUnit.get(unit);
	}

	private void installCluster(AutonomousSoftwareDistribution cluster, SoftwareUnit unit) {
		unitsByName.put(unit.name, unit);
		unitsByAnonymousEntryHash.put(unit.anonymousEntryHash, unit);
		unitsByAnonymousExitHash.put(unit.anonymousExitHash, unit);
		unitsByInterceptionHash.put(unit.interceptionHash, unit);
		distributionsByUnit.put(unit, cluster);
		cluster.addUnit(unit);
	}
}
