package com.bc.simple.bean.core.workshop;

import com.bc.simple.bean.core.AbstractBeanFactory;

public abstract class Workshop {
	protected AbstractBeanFactory factory;

	private Workshop next;

	public Workshop(AbstractBeanFactory factory) {
		this.factory = factory;
	}

	public Workshop next(Workshop next) {
		if (this.next!=null) {
			next.next=this.next;
		}
		this.next = next;
		return next;
	}

	public Workshop next() {
		return this.next;
	}
	public Workshop next(int step) {
		Workshop next=this.next;
		for (int i = 0; i<step ; i++) {
			next=next.next;
		}
		return next;
	}
	
	
	public void produce() {
		produceWorkshop();
		if (next!=null) {
			next.produceWorkshop();
		}
	}

	public  abstract void produceWorkshop();
}
