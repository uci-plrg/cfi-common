package edu.uci.plrg.cfi.common.util;

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

	public void add(int val) {
		this.innerVal += val;
	}
	
	public void subtract(int val) {
		this.innerVal -= val;
	}

	public String toString() {
		return Integer.toString(innerVal);
	}
}
