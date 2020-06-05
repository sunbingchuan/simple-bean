package com.bc.simple.bean.common.support.proxy;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

public class ProxyHelper {

	private static final Proxy proxy = new Proxy();

	public static Object instance(InvocationHandler handler, Class<?>[] interfaceClasses) {
		return proxy.instance(handler, interfaceClasses);
	}

	public static Object instance(InvocationHandler handler, Class<?> parent, Class<?>[] interfaceClasses) {
		return proxy.instance(handler, parent, interfaceClasses);
	}

	public static Object instance(InvocationHandler handler, Class<?> parent, Method[] overrides,
			Class<?>[] interfaceClasses) {
		return proxy.instance(handler, parent, overrides, interfaceClasses);
	}

	public static Object proxy(Class<?> target, Class<? extends InvocationHandler> handler) {
		return proxy.proxy(target, handler);
	}

	public void setInvoker(Object factory, InvocationHandler invoker) {
		proxy.setInvoker(factory, invoker);
	}

}
