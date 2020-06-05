package com.bc.simple.bean.core.assembler;

import com.bc.simple.bean.core.BeanFactory;
import com.bc.simple.bean.core.workshop.BeanCreateWorkshop;
import com.bc.simple.bean.core.workshop.BeanFittingWorkshop;
import com.bc.simple.bean.core.workshop.Workshop;

public class GetBeanAssembler implements Assembler<BeanFactory> {

	private BeanFactory factory;
	
	private String key;
	
	@Override
	public String assemble(BeanFactory factory) {
		this.factory=factory;
		Workshop getBean = new BeanCreateWorkshop(factory);
		getBean.next(new BeanFittingWorkshop(factory));
		this.key = factory.addWorkshop(getBean);
		this.factory.setGetBeanAssembler(this);
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
