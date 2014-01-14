package edu.uci.eecs.crowdsafe.common.main;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import edu.uci.eecs.crowdsafe.common.data.DataMessageType;
import edu.uci.eecs.crowdsafe.common.data.dist.AutonomousSoftwareDistribution;
import edu.uci.eecs.crowdsafe.common.data.graph.ModuleGraphCluster;
import edu.uci.eecs.crowdsafe.common.data.graph.cluster.loader.ClusterGraphLoadSession;
import edu.uci.eecs.crowdsafe.common.data.graph.cluster.metadata.ClusterMetadataExecution;
import edu.uci.eecs.crowdsafe.common.data.graph.cluster.metadata.ClusterMetadataSequence;
import edu.uci.eecs.crowdsafe.common.data.graph.cluster.metadata.ClusterUIBInterval;
import edu.uci.eecs.crowdsafe.common.data.graph.cluster.metadata.EvaluationType;
import edu.uci.eecs.crowdsafe.common.data.graph.execution.ProcessExecutionGraph;
import edu.uci.eecs.crowdsafe.common.data.graph.execution.loader.ProcessGraphLoadSession;
import edu.uci.eecs.crowdsafe.common.data.results.Graph;
import edu.uci.eecs.crowdsafe.common.io.cluster.ClusterTraceDataSource;
import edu.uci.eecs.crowdsafe.common.io.cluster.ClusterTraceDirectory;
import edu.uci.eecs.crowdsafe.common.io.execution.ExecutionTraceDataSource;
import edu.uci.eecs.crowdsafe.common.io.execution.ExecutionTraceDirectory;
import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.crowdsafe.common.util.ArgumentStack;
import edu.uci.eecs.crowdsafe.common.util.OptionArgumentMap;
import edu.uci.eecs.crowdsafe.common.util.OptionArgumentMap.OptionMode;

public class GraphSummaryPrinter {

	private static final OptionArgumentMap.StringOption outputOption = OptionArgumentMap.createStringOption('o',
			OptionMode.REQUIRED);

	private final ArgumentStack args;
	private final CommonMergeOptions options;

	private GraphSummaryPrinter(ArgumentStack args) {
		this.args = args;
		this.options = new CommonMergeOptions(args, CommonMergeOptions.crowdSafeCommonDir,
				CommonMergeOptions.restrictedClusterOption, CommonMergeOptions.unitClusterOption,
				CommonMergeOptions.excludeClusterOption, outputOption);
	}

	private void run() {
		try {
			Log.addOutput(System.out);
			options.parseOptions();
			options.initializeGraphEnvironment();

			String path = args.pop();
			File directory = new File(path.substring(path.indexOf(':') + 1));
			if (!(directory.exists() && directory.isDirectory())) {
				Log.log("Illegal argument '" + directory + "'; no such directory.");
				printUsageAndExit();
			}

			File outputFile = new File(outputOption.getValue());
			if (outputFile.getParentFile() == null)
				outputFile = new File(new File("."), outputOption.getValue());
			if (!outputFile.getParentFile().exists()) {
				Log.log("Illegal argument '" + outputOption.getValue() + "'; no such directory.");
				printUsageAndExit();
			}

			Graph.Process process = null;
			switch (path.charAt(0)) {
				case 'c':
					process = summarizeClusterGraph(directory);
					break;
				case 'e':
					process = summarizeExecutionGraph(directory);
					break;
			}
			FileOutputStream out = new FileOutputStream(outputFile);
			out.write(DataMessageType.PROCESS_GRAPH.id);
			process.writeTo(out);
			out.flush();
			out.close();
		} catch (Throwable t) {
			t.printStackTrace();
		}
	}

	private Graph.Process summarizeClusterGraph(File directory) throws IOException {
		Graph.Process.Builder processBuilder = Graph.Process.newBuilder();
		processBuilder.setName(directory.getName());

		ModuleGraphCluster<?> mainGraph = null;

		ClusterTraceDataSource dataSource = new ClusterTraceDirectory(directory).loadExistingFiles();
		ClusterGraphLoadSession loadSession = new ClusterGraphLoadSession(dataSource);
		for (AutonomousSoftwareDistribution cluster : dataSource.getReprsentedClusters()) {
			ModuleGraphCluster<?> graph = loadSession.loadClusterGraph(cluster);
			processBuilder.addCluster(graph.summarize(cluster.isAnonymous()));

			if (graph.metadata.isMain())
				mainGraph = graph;
		}

		if (mainGraph != null) {
			processBuilder.setMetadata(mainGraph.metadata.summarizeIntervals());
		}

		return processBuilder.build();
	}

	private Graph.Process summarizeExecutionGraph(File directory) throws IOException {
		ExecutionTraceDataSource dataSource = new ExecutionTraceDirectory(directory,
				ProcessExecutionGraph.EXECUTION_GRAPH_FILE_TYPES,
				ProcessExecutionGraph.EXECUTION_GRAPH_REQUIRED_FILE_TYPES);
		ProcessGraphLoadSession loadSession = new ProcessGraphLoadSession();
		ProcessExecutionGraph graph = loadSession.loadGraph(dataSource, null);
		return graph.summarizeProcess();
	}

	private void printUsageAndExit() {
		System.out.println(String.format("Usage: %s -o <output-file> {c: | e:}<run-dir>", getClass().getSimpleName()));
		System.exit(1);
	}

	public static void main(String[] args) {
		ArgumentStack stack = new ArgumentStack(args);
		GraphSummaryPrinter printer = new GraphSummaryPrinter(stack);
		printer.run();
	}
}
