package com.bc.test;

import com.bc.simple.bean.ApplicationContext;
import com.bc.simple.bean.BeanFactory;
import com.bc.simple.bean.common.stereotype.Autowired;
import com.bc.simple.bean.common.stereotype.Component;

@Component
public class SecondBean {
	@Autowired
	public BeanFactory factory;
	@Autowired
	public ApplicationContext applicationContext;

	@Autowired
	public SecondBean(String m) {
		System.out.println("has injected:" + m);
	}
}
