package edu.uci.eecs.crowdsafe.common.util;

public class MutableInteger {
	private int innerVal;

	public MutableInteger(int innerVal) {
		this.innerVal = innerVal;
	}

	public void setVal(int val) {
		this.innerVal = val;
	}

	public int getVal() {
		return innerVal;
	}
	
	public void increment() {
		innerVal++;
	}
	
	public void decrement() {
		innerVal--;
	}

	public String toString() {
		return Integer.toString(innerVal);
	}
}
