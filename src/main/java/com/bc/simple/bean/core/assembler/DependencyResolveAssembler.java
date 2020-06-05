package com.bc.simple.bean.core.assembler;

import com.bc.simple.bean.core.BeanFactory;
import com.bc.simple.bean.core.workshop.DependencyResolveWorkshop;
import com.bc.simple.bean.core.workshop.Workshop;

public class DependencyResolveAssembler implements Assembler<BeanFactory> {

	private BeanFactory factory;
	
	private String key;
	
	@Override
	public String assemble(BeanFactory factory) {
		this.factory=factory;
		Workshop dependencyResolve = new DependencyResolveWorkshop(factory);
		this.key = factory.addWorkshop(dependencyResolve);
		this.factory.setDependencyResolveAssembler(this);
		return this.key;
	}

	@Override
	public String getKey() {
		return this.key;
	}
	
	public void work() {
		this.factory.produce(this.key);
	}

}
