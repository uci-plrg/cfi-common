package edu.uci.eecs.crowdsafe.common.data.graph.execution.loader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.uci.eecs.crowdsafe.common.data.dist.ConfiguredSoftwareDistributions;
import edu.uci.eecs.crowdsafe.common.data.dist.SoftwareDistributionUnit;
import edu.uci.eecs.crowdsafe.common.data.graph.execution.ModuleInstance;
import edu.uci.eecs.crowdsafe.common.data.graph.execution.ProcessExecutionModuleSet;
import edu.uci.eecs.crowdsafe.common.datasource.ProcessTraceDataSource;
import edu.uci.eecs.crowdsafe.common.datasource.ProcessTraceStreamType;
import edu.uci.eecs.crowdsafe.common.exception.InvalidGraphException;
import edu.uci.eecs.crowdsafe.common.exception.OverlapModuleException;
import edu.uci.eecs.crowdsafe.common.log.Log;

public class ProcessModuleLoader {

	private static class PendingModuleKey {
		final SoftwareDistributionUnit unit;
		final long start;

		PendingModuleKey(String moduleName, long start) {
			this.unit = ConfiguredSoftwareDistributions.getInstance().establishUnit(moduleName);
			this.start = start;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((unit == null) ? 0 : unit.name.hashCode());
			result = prime * result + (int) (start ^ (start >>> 32));
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
			if (start != other.start)
				return false;
			return true;
		}
	}

	private class PendingModule {
		final PendingModuleKey key;
		final long blockLoadTime;
		final long edgeLoadTime;
		final long crossModuleEdgeLoadTime;
		final long end;

		boolean unloaded = false;

		public PendingModule(Matcher matcher) {
			key = new PendingModuleKey(matcher.group(4).toLowerCase(), Long.parseLong(matcher.group(5), 16));
			blockLoadTime = Long.parseLong(matcher.group(1));
			edgeLoadTime = Long.parseLong(matcher.group(2));
			crossModuleEdgeLoadTime = Long.parseLong(matcher.group(3));
			end = Long.parseLong(matcher.group(6), 16);
		}
	}

	private static final Pattern LOAD_PARSER = Pattern
			.compile("\\(([0-9]+),([0-9]+),([0-9]+)\\) Loaded module ([a-zA-Z_0-9<>\\-\\.\\+]+): 0x([0-9A-Fa-f]+) - 0x([0-9A-Fa-f]+)");
	private static final Pattern UNLOAD_PARSER = Pattern
			.compile("\\(([0-9]+),([0-9]+),([0-9]+)\\) Unloaded module ([a-zA-Z_0-9<>\\-\\.\\+]+): 0x([0-9A-Fa-f]+) - 0x([0-9A-Fa-f]+)");

	private final Map<PendingModuleKey, PendingModule> pendingModules = new HashMap<PendingModuleKey, PendingModule>();

	/**
	 * Assume the module file is organized in the follwoing way: Module USERENV.dll: 0x722a0000 - 0x722b7000
	 * 
	 * @param fileName
	 * @return
	 * @throws OverlapModuleException
	 */
	public ProcessExecutionModuleSet loadModules(ProcessTraceDataSource dataSource) throws IOException {
		ProcessExecutionModuleSet modules = new ProcessExecutionModuleSet();
		BufferedReader reader = new BufferedReader(new InputStreamReader(
				dataSource.getDataInputStream(ProcessTraceStreamType.MODULE)));

		String line;
		while ((line = reader.readLine()) != null) {
			Matcher matcher = LOAD_PARSER.matcher(line);
			if (matcher.matches()) {
				PendingModule module = new PendingModule(matcher);
				pendingModules.put(module.key, module);

				for (PendingModule pending : new ArrayList<PendingModule>(pendingModules.values())) {
					if (pending.unloaded) {
						modules.add(new ModuleInstance(pending.key.unit, pending.key.start, pending.end,
								pending.blockLoadTime, module.blockLoadTime, pending.edgeLoadTime, module.edgeLoadTime,
								pending.crossModuleEdgeLoadTime, module.crossModuleEdgeLoadTime));
						pendingModules.remove(pending.key);
					}
				}
			} else {
				matcher = UNLOAD_PARSER.matcher(line);
				if (!matcher.matches()) {
					throw new InvalidGraphException(
							"Module loader failed to match line '%s' against the unload pattern--exiting now!", line);
				}

				PendingModule pending = pendingModules.get(new PendingModuleKey(matcher.group(4).toLowerCase(), Long
						.parseLong(matcher.group(5), 16)));
				pending.unloaded = true;
				// long blockUnloadTime = Long.parseLong(matcher.group(1));
				// long edgeUnloadTime = Long.parseLong(matcher.group(2));
				// long crossModuleEdgeUnloadTime = Long.parseLong(matcher.group(3));
				//
				// modules.add(new ModuleInstance(pending.key.unit, pending.key.start, pending.end,
				// pending.blockLoadTime,
				// blockUnloadTime, pending.edgeLoadTime, edgeUnloadTime, pending.crossModuleEdgeLoadTime,
				// crossModuleEdgeUnloadTime));
			}
		}

		for (PendingModule pending : pendingModules.values()) {
			modules.add(new ModuleInstance(pending.key.unit, pending.key.start, pending.end, pending.blockLoadTime,
					Long.MAX_VALUE, pending.edgeLoadTime, Long.MAX_VALUE, pending.crossModuleEdgeLoadTime,
					Long.MAX_VALUE));
		}

		return modules;
	}
}
