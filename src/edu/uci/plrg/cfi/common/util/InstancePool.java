package edu.uci.plrg.cfi.common.util;

import java.util.ArrayList;
import java.util.List;

public class InstancePool<T extends InstancePool.Item<T>> {

	public static abstract class Item<T extends Item<T>> {
		InstancePool<T> owner;

		public void release() {
			synchronized (owner) {
				owner.items.add(this);
			}
		}
	}

	public static interface Factory<T extends Item<T>> {
		T createItem();
	}

	private final Factory<T> factory;
	private final int batchCount;

	private final List<Item<T>> items = new ArrayList<Item<T>>();

	public InstancePool(Factory<T> factory, int batchCount) {
		this.factory = factory;
		this.batchCount = batchCount;
	}

	@SuppressWarnings("unchecked")
	public synchronized T checkout() {
		if (items.isEmpty()) {
			for (int i = 0; i < (batchCount - 1); i++) {
				T item = factory.createItem();
				item.owner = this;
				items.add(item);
			}

			T item = factory.createItem();
			item.owner = this;
			return item;
		}

		return (T) items.remove(items.size() - 1);
	}
}
