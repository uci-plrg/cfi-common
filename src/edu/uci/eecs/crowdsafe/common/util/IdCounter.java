package edu.uci.eecs.crowdsafe.common.util;

import java.util.HashMap;
import java.util.Map;

public class IdCounter<IdType> {
	
	private static class Counter {
		int count = 1;
	}

	private final Map<IdType, Counter> counters = new HashMap<IdType, Counter>();

	public void increment(IdType id) {
		Counter counter = counters.get(id);
		if (counter == null) {
			counters.put(id, new Counter());
		} else {
			counter.count++;
		}
	}
	public int get(IdType id) {
		Counter counter = counters.get(id);
		if (counter == null) {
			return 0;
		} else {
			return counter.count;
		}
	}
}