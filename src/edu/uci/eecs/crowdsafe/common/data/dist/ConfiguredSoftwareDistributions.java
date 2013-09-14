package edu.uci.eecs.crowdsafe.common.data.dist;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.uci.eecs.crowdsafe.common.config.CrowdSafeConfiguration;

public class ConfiguredSoftwareDistributions {

	public static synchronized ConfiguredSoftwareDistributions getInstance() {
		if (INSTANCE == null) {
			INSTANCE = new ConfiguredSoftwareDistributions();
			INSTANCE.initialize();
		}
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

	private final File configDir;
	public final Map<String, AutonomousSoftwareDistribution> distributions = new HashMap<String, AutonomousSoftwareDistribution>();

	private ConfiguredSoftwareDistributions() {
		configDir = new File(new File(
				CrowdSafeConfiguration.getInstance().environmentValues
						.get(CrowdSafeConfiguration.Environment.CROWD_SAFE_COMMON_DIR)), "config");

		distributions.put(MAIN_PROGRAM, new AutonomousSoftwareDistribution(MAIN_PROGRAM));
	}

	private void initialize() {
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
		} catch (IOException e) {
			throw new IllegalStateException(String.format(
					"Error reading the autonomous software distribution configuration from %s!",
					configDir.getAbsolutePath()));
		}
	}

	public SoftwareDistributionUnit establishUnit(String name) {
		for (AutonomousSoftwareDistribution dist : distributions.values()) {
			for (SoftwareDistributionUnit unit : dist.distributionUnits) {
				if (unit.name.equals(name) || unit.name.equals(getFilename(name)))
					return unit;
			}
		}
		SoftwareDistributionUnit unit = new SoftwareDistributionUnit(name);
		distributions.get(MAIN_PROGRAM).distributionUnits.add(unit);
		return unit;
	}
}
