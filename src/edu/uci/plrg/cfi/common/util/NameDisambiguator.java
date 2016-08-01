package edu.uci.plrg.cfi.common.util;

import java.util.HashSet;
import java.util.Set;

public class NameDisambiguator {

	private final Set<String> usedNames = new HashSet<String>();
	private final String separator;

	public NameDisambiguator() {
		this("_");
	}

	public NameDisambiguator(String separator) {
		this.separator = separator;
	}

	public void reset() {
		usedNames.clear();
	}

	public String disambiguateName(String name) {
		int suffix = 0;
		String disambiguated = name;
		while (usedNames.contains(disambiguated)) {
			disambiguated = String.format("%s%s%d", name, separator, suffix++);
		}
		usedNames.add(disambiguated);
		
		return disambiguated;
	}
}
