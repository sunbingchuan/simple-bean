package com.bc.test.a;

import com.bc.simple.bean.common.annotation.Autowired;
import com.bc.simple.bean.common.annotation.Service;

@Service
public class SelfReference {

	@Autowired
	public
	SelfReference self;

	public SelfReference() {

	}

}
