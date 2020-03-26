package com.bc.simple.bean.common.support.proxy;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import com.bc.simple.bean.common.util.BeanUtils;
import com.bc.simple.bean.common.util.StringUtils;

public class CommonInvocationHandler implements InvocationHandler {

	private static final String PROXY_CONSTRUCTOR = "init$SUPER";


	private static Map<Executable, InvocationHandler> defines =
			new ConcurrentHashMap<Executable, InvocationHandler>();

	private static Map<Method, InvocationHandler> routes = new ConcurrentHashMap<Method, InvocationHandler>();

	@Override
	public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
		InvocationHandler handler = routes.get(method);
		Executable key=null;
		if (handler == null) {
			for (Entry<Executable, InvocationHandler> entry : defines.entrySet()) {
				if (method.getName().equals(PROXY_CONSTRUCTOR) && entry.getKey() instanceof Constructor) {
					Constructor<?> constructor = (Constructor<?>) entry.getKey();
					if (classEquals(constructor, method) && BeanUtils.paramsEqual(method.getParameterTypes(),
							constructor.getParameterTypes())) {
						handler=entry.getValue();
						key=entry.getKey();
					}
				} else if (entry.getKey() instanceof Method && methodEquals(method, (Method) entry.getKey())) {
					handler=entry.getValue();
					key=entry.getKey();
				}

			}
		}
		if (handler != null) {
			routes.put(method, handler);
			defines.remove(key);
			return handler.invoke(proxy, method, args);
		}
		return method.invoke(proxy, args);
	}

	private boolean classEquals(Executable origin, Executable proxy) {
		String originClassName = origin.getDeclaringClass().getCanonicalName();
		String proxyClassName = proxy.getDeclaringClass().getCanonicalName();
		if (proxyClassName.contains(originClassName + Proxy.LINK_STR)) {
			return true;
		}
		return false;
	}

	private boolean methodEquals(Method proxy, Method original) {
		if (!proxy.getName().replace(Proxy.PROXY_SUFFIX, StringUtils.EMPTY).equals(original.getName())) {
			return false;
		}
		if (!BeanUtils.paramsEqual(proxy.getParameterTypes(), original.getParameterTypes())) {
			return false;
		}
		return true;
	}

	public static void register(Executable exec, InvocationHandler handler) {
		defines.put(exec, handler);
	}
	
	public static void registerAll(Map<? extends Executable, InvocationHandler> registers) {
		defines.putAll(registers);
	}
	
	public static void register(Executable[] execs, InvocationHandler handler) {
		for (Executable exec : execs) {
			defines.put(exec, handler);
		}
	}

}
