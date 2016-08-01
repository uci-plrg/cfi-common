package edu.uci.plrg.cfi.common.util;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class IdCounter<IdType> {
	
	private static class Counter {
		int count = 1;
	}

	private int size = 0;
	private final Map<IdType, Counter> counters = new HashMap<IdType, Counter>();

	public void increment(IdType id) {
		Counter counter = counters.get(id);
		if (counter == null) {
			counters.put(id, new Counter());
		} else {
			counter.count++;
		}
		size++;
	}
	
	public Collection<IdType> idSet() {
		return counters.keySet();
	}
	
	public int get(IdType id) {
		Counter counter = counters.get(id);
		if (counter == null) {
			return 0;
		} else {
			return counter.count;
		}
	}
	
	public int getIdCount() {
		return counters.keySet().size();
	}
	
	public int getTotalCount() {
		return size;
	}
}