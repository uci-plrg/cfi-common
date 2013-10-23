package edu.uci.eecs.crowdsafe.common.main;

import java.io.File;

import edu.uci.eecs.crowdsafe.common.data.monitor.MonitorDatasetGenerator;
import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.crowdsafe.common.util.ArgumentStack;
import edu.uci.eecs.crowdsafe.common.util.OptionArgumentMap;
import edu.uci.eecs.crowdsafe.common.util.OptionArgumentMap.OptionMode;

public class MonitorDataTransformer {

	private static final OptionArgumentMap.BooleanOption verboseOption = OptionArgumentMap.createBooleanOption('v');
	private static final OptionArgumentMap.StringOption logOption = OptionArgumentMap.createStringOption('l');
	private static final OptionArgumentMap.StringOption outputOption = OptionArgumentMap.createStringOption('o',
			OptionMode.REQUIRED);

	private final ArgumentStack args;
	private final CommonMergeOptions options;

	private MonitorDataTransformer(ArgumentStack args) {
		this.args = args;
		this.options = new CommonMergeOptions(args, CommonMergeOptions.crowdSafeCommonDir,
				CommonMergeOptions.restrictedClusterOption, CommonMergeOptions.unitClusterOption,
				CommonMergeOptions.excludeClusterOption, verboseOption, logOption, outputOption);
	}

	private void run() {
		try {
			options.parseOptions();
			options.initializeGraphEnvironment();

			if (verboseOption.getValue() || (logOption.getValue() == null)) {
				Log.addOutput(System.out);
			}
			if (logOption.getValue() != null) {
				Log.addOutput(new File(logOption.getValue()));
			}

			String path = args.pop();
			File directory = new File(path);
			if (!(directory.exists() && directory.isDirectory())) {
				Log.log("Illegal cluster graph directory '" + directory + "'; no such directory.");
				printUsageAndExit();
			}

			File outputFile = new File(outputOption.getValue());
			if (outputFile.getParentFile() == null)
				outputFile = new File(new File("."), outputOption.getValue());
			if (!outputFile.getParentFile().exists()) {
				Log.log("Illegal output file '" + outputOption.getValue() + "'; parent directory does not exist.");
				System.out.println("Parent directory does not exist: " + outputOption.getValue());
				printUsageAndExit();
			}

			MonitorDatasetGenerator generator = new MonitorDatasetGenerator(directory, outputFile);
			generator.generateDataset();
		} catch (Throwable t) {
			t.printStackTrace();
		}
	}

	private void printUsageAndExit() {
		System.out.println(String.format("Usage: %s -o <output-file> <cluster-data-dir>", getClass().getSimpleName()));
		System.exit(1);
	}

	public static void main(String[] args) {
		ArgumentStack stack = new ArgumentStack(args);
		MonitorDataTransformer printer = new MonitorDataTransformer(stack);
		printer.run();
	}
}
