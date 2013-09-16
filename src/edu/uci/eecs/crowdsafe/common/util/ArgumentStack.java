package edu.uci.eecs.crowdsafe.common.util;

import gnu.getopt.Getopt;

import java.util.ArrayList;
import java.util.List;

public class ArgumentStack {

	private final List<String> arguments = new ArrayList<String>();
	private Getopt options = null;

	public ArgumentStack(String[] args) {
		for (int i = (args.length - 1); i >= 0; i--) {
			arguments.add(args[i]);
		}
	}

	public String pop() {
		return arguments.remove(arguments.size() - 1);
	}

	public int size() {
		return arguments.size();
	}

	public Getopt parseOptions(String optionKey) {
		if (options != null)
			throw new IllegalStateException("Options parsing is already in progress!");

		options = new Getopt("", arguments.toArray(new String[] {}), optionKey);
		options.setOpterr(false);
		return options;
	}

	public void popOptions() {
		if (options == null)
			throw new IllegalStateException("Options parsing is not in progress!");

		while (options.getopt() >= 0)
			;
		for (int i = 0; i < options.getOptind(); i++) {
			pop();
		}
		options = null;
	}
}
