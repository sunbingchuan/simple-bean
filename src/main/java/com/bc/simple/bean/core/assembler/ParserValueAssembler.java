package com.bc.simple.bean.core.assembler;

import com.bc.simple.bean.core.BeanFactory;
import com.bc.simple.bean.core.workshop.ParserValueWorkshop;
import com.bc.simple.bean.core.workshop.Workshop;

public class ParserValueAssembler implements Assembler<BeanFactory> {

	private BeanFactory factory;
	
	private String key;
	
	@Override
	public String assemble(BeanFactory factory) {
		this.factory=factory;
		Workshop parserValue = new ParserValueWorkshop(factory);
		this.key = factory.addWorkshop(parserValue);
		this.factory.setParserValueAssembler(this);
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
