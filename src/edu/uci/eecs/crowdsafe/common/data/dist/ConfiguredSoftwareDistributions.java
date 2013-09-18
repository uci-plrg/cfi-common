package edu.uci.eecs.crowdsafe.common.data.dist;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.uci.eecs.crowdsafe.common.config.CrowdSafeConfiguration;

public class ConfiguredSoftwareDistributions {

	public static void initialize() {
		initialize(new File(new File(
				CrowdSafeConfiguration.getInstance().environmentValues
						.get(CrowdSafeConfiguration.Environment.CROWD_SAFE_COMMON_DIR)), "config"));
	}

	public static void initialize(File configDir) {
		INSTANCE = new ConfiguredSoftwareDistributions(configDir);
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

	public static final String MAIN_PROGRAM = "<main-program>";

	public final File configDir;
	public final Map<String, AutonomousSoftwareDistribution> distributions = new HashMap<String, AutonomousSoftwareDistribution>();
	public final Map<String, SoftwareDistributionUnit> unitsByName = new HashMap<String, SoftwareDistributionUnit>();
	public final Map<SoftwareDistributionUnit, AutonomousSoftwareDistribution> distributionsByUnit = new HashMap<SoftwareDistributionUnit, AutonomousSoftwareDistribution>();

	private ConfiguredSoftwareDistributions(File configDir) {
		this.configDir = configDir;
		distributions.put(MAIN_PROGRAM, new AutonomousSoftwareDistribution(MAIN_PROGRAM, "main-program"));
	}

	private void loadDistributions() {
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

	public synchronized SoftwareDistributionUnit establishUnit(String name) {
		SoftwareDistributionUnit existing = unitsByName.get(name);
		if (existing == null)
			existing = unitsByName.get(getFilename(name));
		if (existing != null)
			return existing;

		SoftwareDistributionUnit unit = new SoftwareDistributionUnit(name);
		AutonomousSoftwareDistribution main = distributions.get(MAIN_PROGRAM);
		unitsByName.put(unit.name, unit);
		distributionsByUnit.put(unit, main);
		main.distributionUnits.add(unit);
		return unit;
	}
}
