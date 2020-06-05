package com.bc.simple.bean.common.support.proxy;

import java.lang.reflect.Executable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

public class Template {
	private InvocationHandler invocationHandler$TEMPLATE = null;

	public InvocationHandler getInvocationHandler$TEMPLATE() {
		return invocationHandler$TEMPLATE;
	}

	private static Map<Executable, Method> mapping$TEMPLATE;

	public Template() {
		init$TEMPLATE();
	}


	public void init$TEMPLATE() {
		mapping$TEMPLATE = new HashMap<>();
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
						mapping$TEMPLATE.put(proxyMethod, method);
					}
				}
			}
		}
	}

	public void setInvocationHandler$TEMPLATE(InvocationHandler invocationHandler) {
		this.invocationHandler$TEMPLATE = invocationHandler;
	}

	public Object invoke$TEMPLATE(Executable method, Object... args) {
		try {
			Method superMethod = mapping$TEMPLATE.get(method);
			return invocationHandler$TEMPLATE.invoke(this, superMethod != null ? 
					superMethod : ((method instanceof Method)?(Method)method:null), args);
		} catch (Throwable e) {
			throw new RuntimeException(e);
		}
	}

}
