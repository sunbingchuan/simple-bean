package com.bc.test;

import com.bc.simple.bean.common.stereotype.Autowired;
import com.bc.simple.bean.common.stereotype.Service;

@Service
public class SelfReference {

	@Autowired
	SelfReference self;

	public SelfReference() {

	}

}
