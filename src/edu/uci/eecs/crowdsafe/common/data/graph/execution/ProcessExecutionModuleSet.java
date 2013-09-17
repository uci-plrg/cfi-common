package edu.uci.eecs.crowdsafe.common.data.graph.execution;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;

import edu.uci.eecs.crowdsafe.common.data.dist.SoftwareDistributionUnit;

public class ProcessExecutionModuleSet {

	private final Multimap<SoftwareDistributionUnit, ModuleInstance> instancesByUnit = ArrayListMultimap.create();
	private ModuleInstance modules[] = null;

	public void add(ModuleInstance module) {
		if (modules != null)
			throw new IllegalStateException("This set of modules has been frozen, new modules cannot be added now!");
		instancesByUnit.put(module.unit, module);
	}

	public Collection<ModuleInstance> getUnitInstances(SoftwareDistributionUnit unit) {
		return instancesByUnit.get(unit);
	}

	public void freeze() {
		List<ModuleInstance> instances = new ArrayList<ModuleInstance>(instancesByUnit.values());
		modules = instances.toArray(new ModuleInstance[] {});
	}

	public boolean hashOverlap() {
		List<ModuleInstance> list = new ArrayList<ModuleInstance>();
		for (int i = 0; i < list.size(); i++) {
			for (int j = i + 1; j < list.size(); j++) {
				ModuleInstance mod1 = list.get(i), mod2 = list.get(j);
				if ((mod1.start < mod2.start && mod1.end > mod2.start)
						|| (mod1.start < mod2.end && mod1.end > mod2.end)) {
					return true;
				}
			}
		}
		return false;
	}

	public ModuleInstance getModuleForLoadedBlock(long tag, long tagIndex) {
		ModuleInstance activeModule = ModuleInstance.UNKNOWN;
		for (int i = 0; i < modules.length; i++) {
			ModuleInstance instance = modules[i];
			if ((tag >= instance.start) && (tag <= instance.end) && (tagIndex >= instance.blockSpan.loadTimestamp)
					&& (tagIndex < instance.blockSpan.unloadTimestamp))
				activeModule = instance;
		}
		return activeModule;
	}

	public ModuleInstance getModuleForLoadedEdge(long tag, long edgeIndex) {
		ModuleInstance activeModule = ModuleInstance.UNKNOWN;
		for (int i = 0; i < modules.length; i++) {
			ModuleInstance instance = modules[i];
			if ((tag >= instance.start) && (tag <= instance.end) && (edgeIndex >= instance.edgeSpan.loadTimestamp)
					&& (edgeIndex < instance.edgeSpan.unloadTimestamp))
				activeModule = instance;
		}
		return activeModule;
	}

	public ModuleInstance getModuleForLoadedCrossModuleEdge(long tag, long edgeIndex) {
		ModuleInstance activeModule = ModuleInstance.UNKNOWN;
		for (int i = 0; i < modules.length; i++) {
			ModuleInstance instance = modules[i];
			if ((tag >= instance.start) && (tag <= instance.end)
					&& (edgeIndex >= instance.crossModuleEdgeSpan.loadTimestamp)
					&& (edgeIndex < instance.crossModuleEdgeSpan.unloadTimestamp))
				activeModule = instance;
		}
		return activeModule;
	}
}
