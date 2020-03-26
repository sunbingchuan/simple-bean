package com.bc.test.a;

import com.bc.simple.bean.common.annotation.Order;
import com.bc.simple.bean.common.annotation.Service;

//@Component("55555")
@Service("66666")
@Order(1)
public class FiveBean {

	public  String flag="0";
	
	public FiveBean() {
	}
	
	@Override
	public String toString() {
		return super.toString()+flag;
	}
	
}
