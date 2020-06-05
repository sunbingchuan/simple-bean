package com.bc.simple.bean.core.assembler;

import com.bc.simple.bean.core.Factory;

public interface Assembler<T extends Factory> {
	 String assemble(T factory) ;
	
	 String getKey();
	 
	 void work();
}
