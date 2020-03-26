package com.bc.test.b;

import java.lang.reflect.Method;

import com.bc.simple.bean.common.annotation.Around;
import com.bc.simple.bean.common.annotation.Aspect;
import com.bc.test.aop.ProxyInvocationHandler;

@Aspect("aopTest")
public class AspectTest {
	@Around("public void com.bc.test.b.AspectBean.angry(..)")
	public Object around(Object proxy, Method method, Object[] args) {
		try {
			System.out.println("doaround");
			return method.invoke(proxy, args);
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		} 
	}
	
	@Around("com.bc.test.b.AspectBeanA")
	public Class<?> handlerClass() {
		return ProxyInvocationHandler.class;
	}
	
	@Around("public void com.bc.test.b.AspectBeanB.new(..)")
	public String handlerRef() {
		return  "proxyInvocationHandlerBean";
	}
	
}
