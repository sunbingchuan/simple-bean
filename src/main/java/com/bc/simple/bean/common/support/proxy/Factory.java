package com.bc.simple.bean.common.support.proxy;

import java.lang.reflect.Executable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

public class Factory {
	private InvocationHandler invocationHandler$FACTORY = null;

	public InvocationHandler getInvocationHandler$FACTORY() {
		return invocationHandler$FACTORY;
	}

	private static Map<Executable, Method> mapping$FACTORY;

	public Factory() {
		init$FACTORY();
	}


	public void init$FACTORY() {
		mapping$FACTORY = new HashMap<>();
		Class<?> thiz = this.getClass();
		Method[] methods = thiz.getDeclaredMethods();
		for (Method method : methods) {
			if (method.getName().indexOf("$SUPER") >= 0) {
				String proxyMethodName = method.getName().replace("$SUPER", "");
				Executable proxyMethod = null;
				try {
					if ("init".equals(proxyMethodName)) {
						proxyMethod=thiz.getDeclaredConstructor(method.getParameterTypes());
					} else {
						proxyMethod = thiz.getDeclaredMethod(proxyMethodName, method.getParameterTypes());
					}
				} catch (NoSuchMethodException | SecurityException e) {
					e.printStackTrace();
				} finally {
					if (proxyMethod != null) {
						mapping$FACTORY.put(proxyMethod, method);
					}
				}
			}
		}
	}

	public void setInvocationHandler$FACTORY(InvocationHandler invocationHandler) {
		this.invocationHandler$FACTORY = invocationHandler;
	}

	public Object invoke$FACTORY(Executable method, Object... args) {
		try {
			Method superMethod = mapping$FACTORY.get(method);
			return invocationHandler$FACTORY.invoke(this, superMethod != null ? 
					superMethod : ((method instanceof Method)?(Method)method:null), args);
		} catch (Throwable e) {
			throw new RuntimeException(e);
		}
	}

}
