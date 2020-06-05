package com.bc.simple.bean.core;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.bc.simple.bean.core.assembler.Assembler;
import com.bc.simple.bean.core.workshop.Workshop;

public class Factory {
	protected Map<String,Workshop> bootWorkshops=new ConcurrentHashMap<>();

	public String addWorkshop(Workshop bootWorkshop) {
		String key = UUID.randomUUID().toString();
		bootWorkshops.put(key, bootWorkshop);
		return key;
	}
	
	public Workshop getBootWorkshop(String key) {
		return bootWorkshops.get(key);
	}
	
	public void produce(String key) {
		Workshop bootWorkshop = bootWorkshops.get(key);
		bootWorkshop.produce();
	}
	
	public <T extends Factory> String registerAssembler(Assembler<T> assembler,T factory) {
		return assembler.assemble(factory);
	}
	
}
