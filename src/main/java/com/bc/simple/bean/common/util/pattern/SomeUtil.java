package com.bc.simple.bean.common.util.pattern;

import java.util.UUID;

public class SomeUtil  implements Util{

	@Override
	public String getDomain() {
		return "some";
	}

	public String getUuid(String what) {
		return UUID.randomUUID().toString();
	}
	
	
	
}
