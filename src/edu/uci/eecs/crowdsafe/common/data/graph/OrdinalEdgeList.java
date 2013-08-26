package edu.uci.eecs.crowdsafe.common.data.graph;

import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

import edu.uci.eecs.crowdsafe.common.data.graph.EdgeSet.OutgoingOrdinal;

class OrdinalEdgeList<NodeType extends Node> implements List<Edge<NodeType>> {

	private class IndexingIterator implements ListIterator<Edge<NodeType>>, Iterable<Edge<NodeType>>,
			Iterator<Edge<NodeType>> {
		private int index;

		@Override
		public Iterator<Edge<NodeType>> iterator() {
			return this;
		}

		@Override
		public boolean hasNext() {
			return (index < end) || ((index == end) && includeCallContinuation);
		}

		@Override
		public Edge<NodeType> next() {
			if (modified)
				throw new ConcurrentModificationException();

			if (index == end) {
				index++;
				return data.callContinuation;
			}
			return data.edges.get(index++);
		}

		@Override
		public boolean hasPrevious() {
			return index > start;
		}

		@Override
		public int nextIndex() {
			return index + 1;
		}

		@Override
		public Edge<NodeType> previous() {
			index--;
			if (index == end)
				return data.callContinuation;
			else
				return data.edges.get(index);
		}

		@Override
		public int previousIndex() {
			return index - 1;
		}

		@Override
		public void add(Edge<NodeType> edge) {
			throw new UnsupportedOperationException("EdgeSet lists are readonly!");
		}

		@Override
		public void set(Edge<NodeType> edge) {
			throw new UnsupportedOperationException("EdgeSet lists are readonly!");
		}

		@Override
		public void remove() {
			throw new UnsupportedOperationException(String.format("Removing is not supported in %s", getClass()
					.getName()));
		}
	}

	private final EdgeSet<NodeType> data;

	int start;
	int end;
	boolean includeCallContinuation;
	boolean modified;
	OutgoingOrdinal group;

	private final IndexingIterator iterator = new IndexingIterator();

	OrdinalEdgeList(EdgeSet data) {
		this.data = data;
	}

	@Override
	public boolean contains(Object o) {
		if (o == null)
			return false;
		if (o instanceof Edge) {
			if (includeCallContinuation && data.callContinuation.equals(o))
				return true;
			for (int i = start; i < end; i++) {
				if (data.edges.get(i).equals(o))
					return true;
			}
		}
		return false;
	}

	@Override
	public boolean containsAll(Collection<?> c) {
		for (Object o : c) {
			if (!contains(o))
				return false;
		}
		return true;
	}

	@Override
	public Edge<NodeType> get(int index) {
		if (includeCallContinuation && (index == (end - start)))
			return data.callContinuation;
		return data.edges.get(start + index);
	}

	@Override
	public int indexOf(Object o) {
		for (int i = start; i < end; i++) {
			if (data.edges.get(i).equals(o))
				return i - start;
		}
		if (includeCallContinuation && data.callContinuation.equals(o))
			return end - start;

		return -1;
	}

	@Override
	public boolean isEmpty() {
		return (start > end) || ((start == end) && !includeCallContinuation);
	}

	@Override
	public int lastIndexOf(Object o) {
		if (includeCallContinuation && data.callContinuation.equals(o))
			return end - start;
		for (int i = end - 1; i >= start; i--) {
			if (data.edges.get(i).equals(o))
				return i - start;
		}
		return -1;
	}

	@Override
	public ListIterator<Edge<NodeType>> listIterator() {
		iterator.index = start;
		return iterator;
	}

	@Override
	public ListIterator<Edge<NodeType>> listIterator(int i) {
		iterator.index = start + i;
		return iterator;
	}

	@Override
	public int size() {
		return (end - start) + (includeCallContinuation ? 1 : 0);
	}

	@Override
	public Iterator<Edge<NodeType>> iterator() {
		iterator.index = start;
		return iterator;
	}

	@Override
	public boolean add(Edge<NodeType> e) {
		throw new UnsupportedOperationException("EdgeSet lists are readonly!");
	}

	@Override
	public void add(int index, Edge<NodeType> element) {
		throw new UnsupportedOperationException("EdgeSet lists are readonly!");
	}

	@Override
	public boolean addAll(Collection<? extends Edge<NodeType>> c) {
		throw new UnsupportedOperationException("EdgeSet lists are readonly!");
	}

	@Override
	public boolean addAll(int index, Collection<? extends Edge<NodeType>> c) {
		throw new UnsupportedOperationException("EdgeSet lists are readonly!");
	}

	@Override
	public void clear() {
		throw new UnsupportedOperationException("EdgeSet lists are readonly!");
	}

	@Override
	public boolean remove(Object o) {
		throw new UnsupportedOperationException("EdgeSet lists are readonly!");
	}

	@Override
	public Edge<NodeType> remove(int index) {
		throw new UnsupportedOperationException("EdgeSet lists are readonly!");
	}

	@Override
	public boolean removeAll(Collection<?> c) {
		throw new UnsupportedOperationException("EdgeSet lists are readonly!");
	}

	@Override
	public boolean retainAll(Collection<?> c) {
		throw new UnsupportedOperationException("EdgeSet lists are readonly!");
	}

	@Override
	public Edge<NodeType> set(int index, Edge<NodeType> element) {
		throw new UnsupportedOperationException("EdgeSet lists are readonly!");
	}

	@Override
	public List<Edge<NodeType>> subList(int fromIndex, int toIndex) {
		throw new UnsupportedOperationException("EdgeSet lists are for indexing and iteration only!");
	}

	@Override
	public Object[] toArray() {
		throw new UnsupportedOperationException("EdgeSet lists are for indexing and iteration only!");
	}

	@Override
	public <T> T[] toArray(T[] a) {
		throw new UnsupportedOperationException("EdgeSet lists are for indexing and iteration only!");
	}

}
