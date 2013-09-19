package edu.uci.eecs.crowdsafe.common.data.graph.cluster;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.uci.eecs.crowdsafe.common.data.dist.SoftwareDistributionUnit;

public class ClusterModuleList {

	private static class ModuleKey {
		SoftwareDistributionUnit unit;
		String version;

		public ModuleKey() {
		}

		ModuleKey(SoftwareDistributionUnit unit, String version) {
			this.unit = unit;
			this.version = version;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((unit == null) ? 0 : unit.hashCode());
			result = prime * result + ((version == null) ? 0 : version.hashCode());
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
			ModuleKey other = (ModuleKey) obj;
			if (unit == null) {
				if (other.unit != null)
					return false;
			} else if (!unit.equals(other.unit))
				return false;
			if (version == null) {
				if (other.version != null)
					return false;
			} else if (!version.equals(other.version))
				return false;
			return true;
		}
	}

	private static class IdSorter implements Comparator<ClusterModule> {
		static final IdSorter INSTANCE = new IdSorter();

		@Override
		public int compare(ClusterModule first, ClusterModule second) {
			return first.id - second.id;
		}
	}

	private final Map<ModuleKey, ClusterModule> modules = new HashMap<ModuleKey, ClusterModule>();
	private final List<ClusterModule> moduleList = new ArrayList<ClusterModule>();

	private final ModuleKey lookupKey = new ModuleKey();

	public ClusterModule addModule(SoftwareDistributionUnit unit, String version) {
		ClusterModule module = new ClusterModule(moduleList.size(), unit, version);
		moduleList.add(module);
		modules.put(new ModuleKey(module.unit, module.version), module);
		return module;
	}

	public synchronized ClusterModule establishModule(SoftwareDistributionUnit unit, String version) {
		lookupKey.unit = unit;
		lookupKey.version = version;
		ClusterModule module = modules.get(lookupKey);
		if (module == null)
			return addModule(unit, version);
		else
			return module;
	}

	public synchronized ClusterModule getModule(SoftwareDistributionUnit unit, String version) {
		lookupKey.unit = unit;
		lookupKey.version = version;
		return modules.get(lookupKey);
	}

	public List<ClusterModule> sortById() {
		List<ClusterModule> moduleList = new ArrayList<ClusterModule>(modules.values());
		Collections.sort(moduleList, IdSorter.INSTANCE);
		return moduleList;
	}
	
	public Collection<ClusterModule> getModules() {
		return modules.values();
	}
	
	public ClusterModule getModule(int index) {
		return moduleList.get(index);
	}
}
