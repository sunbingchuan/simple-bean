package com.bc.test.a;

import com.bc.simple.bean.common.util.StringUtils;

public class ConstructorArgTest {

	Object a = null, b = null, c = null;

	public ConstructorArgTest() {

	}

	public ConstructorArgTest(Object a, Object b, Object c) {
		this.a = a;
		this.b = b;
		this.c = c;
	}

	@Override
	public String toString() {
		return StringUtils.toString(a) + "\r\n" + StringUtils.toString(b) + "\r\n" + StringUtils.toString(c) + "\r\n";
	}

}
