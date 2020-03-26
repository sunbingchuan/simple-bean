package com.bc.test.a;

import com.bc.simple.bean.common.annotation.Autowired;
import com.bc.simple.bean.common.annotation.Bean;
import com.bc.simple.bean.common.annotation.Configuration;

@Configuration
public class ConfigurationBeanTest {

	@Bean
	@Autowired
	public String makeSuperMan(String kernel) {
		System.out.println("invoke configuration Bean SuperMan !" + kernel);
		return "Super Man " + kernel;
	}

}
