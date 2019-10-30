package com.bc.simple.bean.common.support.cglib;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

public class CglibFactory {
	private InvocationHandler invocationHandler = null;

	public InvocationHandler getInvocationHandler() {
		return invocationHandler;
	}

	private static Map<Method, Method> mapping = new HashMap<>();

	public CglibFactory() {
		init();
	}

	private void init() {
		Class<?> thiz = this.getClass();
		Method[] methods = thiz.getDeclaredMethods();
		for (Method method : methods) {
			if (method.getName().indexOf("$SUPER") >= 0) {
				String sunMethodName = method.getName().replace("$SUPER", "");
				Method sunMethod = null;
				try {
					sunMethod = thiz.getDeclaredMethod(sunMethodName, method.getParameterTypes());
				} catch (NoSuchMethodException | SecurityException e) {
					e.printStackTrace();
				} finally {
					if (sunMethod != null) {
						mapping.put(sunMethod, method);
					}
				}
			}
		}
	}

	public void setInvocationHandler(InvocationHandler invocationHandler) {
		this.invocationHandler = invocationHandler;
	}

	public Object invoke(Method method, Object... args) {
		try {
			Method superMethod = mapping.get(method);
			return invocationHandler.invoke(this, superMethod != null ? superMethod : method, args);
		} catch (Throwable e) {
			throw new RuntimeException(e);
		}
	}

}
