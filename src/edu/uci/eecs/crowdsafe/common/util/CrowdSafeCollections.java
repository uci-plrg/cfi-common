package edu.uci.eecs.crowdsafe.common.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class CrowdSafeCollections {

	public static <T> Collection<T> createSortedCopy(Collection<T> collection, Comparator<T> comparator) {
		List<T> sorted = new ArrayList<T>(collection);
		Collections.sort(sorted, comparator);
		return sorted;
	}
}
