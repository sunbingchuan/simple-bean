package com.bc.test.aop;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

import com.bc.simple.bean.common.annotation.Component;

@Component
public class ProxyInvocationHandlerBean implements InvocationHandler{
	@Override
	public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
		System.out.println("bean"+method.getName());
		return method.invoke(proxy, args);
	}
	
}