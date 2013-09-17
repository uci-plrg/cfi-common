package edu.uci.eecs.crowdsafe.common.data.graph.execution.packer;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

import edu.uci.eecs.crowdsafe.common.io.LittleEndianInputStream;
import edu.uci.eecs.crowdsafe.common.io.LittleEndianOutputStream;
import edu.uci.eecs.crowdsafe.common.util.ArgumentStack;
import edu.uci.eecs.crowdsafe.common.util.OptionArgumentMap;

public class ProcessExecutionGraphPacker {

	private static final OptionArgumentMap.StringOption recordSizeOption = OptionArgumentMap.createStringOption('s',
			true);

	private final ArgumentStack args;
	private final int recordSize;

	public ProcessExecutionGraphPacker(ArgumentStack args) {
		this.args = args;

		OptionArgumentMap.populateOptions(args, recordSizeOption);
		recordSize = Integer.parseInt(recordSizeOption.getValue());

		if ((recordSize != 2) && (recordSize != 3)) {
			throw new IllegalArgumentException(String.format("The %s only supports records of size 2 or 3!", getClass()
					.getSimpleName()));
		}
	}

	private ProcessExecutionGraphRecord.Factory createFactory(LittleEndianInputStream input) {
		switch (recordSize) {
			case 2:
				return new ProcessExecutionGraphRecord.TwoWordFactory(input);
			case 3:
				return new ProcessExecutionGraphRecord.ThreeWordFactory(input);
			default:
				throw new IllegalArgumentException(String.format("The %s only supports records of size 2 or 3!",
						getClass().getSimpleName()));
		}
	}

	private ProcessExecutionGraphRecord.Writer createWriter(LittleEndianOutputStream output) {
		switch (recordSize) {
			case 2:
				return new ProcessExecutionGraphRecord.TwoWordWriter(output);
			case 3:
				return new ProcessExecutionGraphRecord.ThreeWordWriter(output);
			default:
				throw new IllegalArgumentException(String.format("The %s only supports records of size 2 or 3!",
						getClass().getSimpleName()));
		}
	}

	private void run() {
		try {
			Set<ProcessExecutionGraphRecord> records = new HashSet<ProcessExecutionGraphRecord>();

			while (args.size() > 0) {
				String filename = args.pop();
				LittleEndianInputStream input = new LittleEndianInputStream(new File(filename));
				ProcessExecutionGraphRecord.Factory factory = createFactory(input);

				while (factory.hasMoreRecords()) {
					records.add(factory.createRecord());
				}

				File outputFile = new File(filename + ".pack");
				LittleEndianOutputStream output = new LittleEndianOutputStream(outputFile);
				ProcessExecutionGraphRecord.Writer writer = createWriter(output);
				for (ProcessExecutionGraphRecord record : records) {
					writer.writeRecord(record);
				}
				writer.flush();
				records.clear();
			}
		} catch (Throwable t) {
			t.printStackTrace();
		}
	}

	public static void main(String[] args) {
		ProcessExecutionGraphPacker packer = new ProcessExecutionGraphPacker(new ArgumentStack(args));
		packer.run();
	}
}
