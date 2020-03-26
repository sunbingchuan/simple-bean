package com.bc.simple.bean.common.reactor.po;

import java.io.Serializable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

import com.bc.simple.bean.common.util.BeanUtils;
import com.bc.simple.bean.core.support.SimpleException;

public class Beaker implements Serializable {

	private static final long serialVersionUID = 3246905936675168489L;

	private volatile ConcurrentHashMap<Integer, Object> elements;

	private final CountDownLatch count;

	private final int size;

	private Beaker(int size) {
		this.count = new CountDownLatch(size);
		this.elements = new ConcurrentHashMap<>(size);
		this.size = size;
	}

	public static Beaker fromSize(int size) {
		return new Beaker(size);
	}

	public Beaker(Object... elements) {
		if (elements == null || elements.length == 0) {
			throw new IllegalArgumentException("illegal elements to construct elements!");
		}
		this.elements = new ConcurrentHashMap<>(elements.length);
		for (int i = 0; i < elements.length; i++) {
			this.elements.put(i, elements[i]);
		}
		this.size = elements.length;
		this.count = new CountDownLatch(0);
	}

	public Map<Integer, Object> getElements() {
		return elements;
	}

	public Object setElement(int index, Object element) {
		Object old;
		if (elements.get(index) == null) {
			old = elements.put(index, element);
			this.count.countDown();
		} else {
			old = elements.put(index, element);
		}
		return old;
	}

	public void addElement(Object element) {
		elements.put(elements.size(), element);
		this.count.countDown();
	}

	public void addElement(int index, Object element) {
		if (index >= this.size) {
			throw new IllegalArgumentException("the elements of index " + index + " not exists!");
		}
		if (elements.get(index) == null) {
			elements.put(index, element);
			this.count.countDown();
		} else {
			throw new SimpleException("element " + index + " has been seted!");
		}
	}

	public void waitForElement() throws InterruptedException {
		this.count.await();
	}

	/**
	 * get the element indicated by @index as the type indicated by @clazz
	 */
	public <T> T as(Class<T> clazz, int index) {
		if (index >= elements.size()) {
			throw new IndexOutOfBoundsException("element " + index + " not exists!");
		}
		return BeanUtils.as(clazz, elements.get(index));
	}

	public void addAll(Beaker beaker) {
		for (Object element : beaker.getElements().values()) {
			addElement(element);
		}
	}

	@Override
	public String toString() {
		return elements.toString();
	}

}
