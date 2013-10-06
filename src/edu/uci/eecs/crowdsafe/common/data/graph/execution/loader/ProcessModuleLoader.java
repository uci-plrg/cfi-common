package edu.uci.eecs.crowdsafe.common.data.graph.execution.loader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.uci.eecs.crowdsafe.common.data.dist.ConfiguredSoftwareDistributions;
import edu.uci.eecs.crowdsafe.common.data.dist.SoftwareDistributionUnit;
import edu.uci.eecs.crowdsafe.common.data.graph.execution.ModuleInstance;
import edu.uci.eecs.crowdsafe.common.data.graph.execution.ProcessExecutionModuleSet;
import edu.uci.eecs.crowdsafe.common.exception.InvalidGraphException;
import edu.uci.eecs.crowdsafe.common.exception.OverlapModuleException;
import edu.uci.eecs.crowdsafe.common.io.execution.ExecutionTraceDataSource;
import edu.uci.eecs.crowdsafe.common.io.execution.ExecutionTraceStreamType;

public class ProcessModuleLoader {

	private static class PendingModuleKey {
		final SoftwareDistributionUnit unit;
		final String version; // not in hash key!
		final long startAddress;

		PendingModuleKey(String moduleName, long startAddress) {
			this.unit = ConfiguredSoftwareDistributions.getInstance().establishUnit(moduleName);
			this.version = ConfiguredSoftwareDistributions.getVersion(moduleName);
			this.startAddress = startAddress;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((unit == null) ? 0 : unit.name.hashCode());
			result = prime * result + (int) (startAddress ^ (startAddress >>> 32));
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			PendingModuleKey other = (PendingModuleKey) obj;
			if (unit == null) {
				if (other.unit != null)
					return false;
			} else if (!unit.name.equals(other.unit.name))
				return false;
			if (startAddress != other.startAddress)
				return false;
			return true;
		}
	}

	private class PendingModule {
		final PendingModuleKey key;
		final long blockLoadTime;
		final long edgeLoadTime;
		final long crossModuleEdgeLoadTime;
		final long endAddress;

		public PendingModule(Matcher matcher) {
			key = new PendingModuleKey(matcher.group(4).toLowerCase(), Long.parseLong(matcher.group(5), 16));
			blockLoadTime = Long.parseLong(matcher.group(1));
			edgeLoadTime = Long.parseLong(matcher.group(2));
			crossModuleEdgeLoadTime = Long.parseLong(matcher.group(3));
			endAddress = Long.parseLong(matcher.group(6), 16);
		}
	}

	private static final Pattern LOAD_PARSER = Pattern
			.compile("\\(([0-9]+),([0-9]+),([0-9]+)\\) Loaded module ([a-zA-Z_0-9~<>\\-\\.\\+]+): 0x([0-9A-Fa-f]+) - 0x([0-9A-Fa-f]+)");
	private static final Pattern UNLOAD_PARSER = Pattern
			.compile("\\(([0-9]+),([0-9]+),([0-9]+)\\) Unloaded module ([a-zA-Z_0-9~<>\\-\\.\\+]+): 0x([0-9A-Fa-f]+) - 0x([0-9A-Fa-f]+)");

	private final Map<PendingModuleKey, PendingModule> pendingModules = new HashMap<PendingModuleKey, PendingModule>();

	/**
	 * Assume the module file is organized in the follwoing way: Module USERENV.dll: 0x722a0000 - 0x722b7000
	 * 
	 * @param fileName
	 * @return
	 * @throws OverlapModuleException
	 */
	public ProcessExecutionModuleSet loadModules(ExecutionTraceDataSource dataSource) throws IOException {
		ProcessExecutionModuleSet modules = new ProcessExecutionModuleSet();
		BufferedReader reader = new BufferedReader(new InputStreamReader(
				dataSource.getDataInputStream(ExecutionTraceStreamType.MODULE)));

		String line;
		while ((line = reader.readLine()) != null) {
			Matcher matcher = LOAD_PARSER.matcher(line);
			if (matcher.matches()) {
				PendingModule module = new PendingModule(matcher);
				pendingModules.put(module.key, module);
			} else {
				matcher = UNLOAD_PARSER.matcher(line);
				if (!matcher.matches()) {
					throw new InvalidGraphException(
							"Module loader failed to match line '%s' against the unload pattern--exiting now!", line);
				}

				PendingModule pending = pendingModules.remove(new PendingModuleKey(matcher.group(4).toLowerCase(), Long
						.parseLong(matcher.group(5), 16)));
        if (pending == null)
          throw new InvalidGraphException(String.format("Cannot unload module %s, there is no such module.", 
            matcher.group(4).toLowerCase()));
        
				long blockUnloadTime = Long.parseLong(matcher.group(1));
				long edgeUnloadTime = Long.parseLong(matcher.group(2));
				long crossModuleEdgeUnloadTime = Long.parseLong(matcher.group(3));

				modules.add(new ModuleInstance(pending.key.unit, pending.key.version, pending.key.startAddress,
						pending.endAddress, pending.blockLoadTime, blockUnloadTime, pending.edgeLoadTime,
						edgeUnloadTime, pending.crossModuleEdgeLoadTime, crossModuleEdgeUnloadTime));
			}
		}

		for (PendingModule pending : pendingModules.values()) {
			modules.add(new ModuleInstance(pending.key.unit, pending.key.version, pending.key.startAddress,
					pending.endAddress, pending.blockLoadTime, Long.MAX_VALUE, pending.edgeLoadTime, Long.MAX_VALUE,
					pending.crossModuleEdgeLoadTime, Long.MAX_VALUE));
		}

		modules.freeze();
		return modules;
	}
}
