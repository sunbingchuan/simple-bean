package com.bc.test;

import com.bc.simple.bean.common.stereotype.Autowired;
import com.bc.simple.bean.common.stereotype.Bean;
import com.bc.simple.bean.common.stereotype.Configuration;

@Configuration
public class ConfigurationBeanTest {

	@Bean
	@Autowired
	public String makeSuperMan(String kernel) {
		System.out.println("invoke configuration Bean SuperMan !" + kernel);
		return "Super Man " + kernel;
	}

}
