package com.bc.simple.bean.common.util.pattern;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import com.bc.simple.bean.common.util.BeanUtils;

public class UtilFactory {

	public static final String GET_DOMAIN = "getDomain";

	private static class Executor {
		final Util util;
		final Method method;

		public Executor(Util util, Method method) {
			this.util = util;
			this.method = method;
		}

		private Object execute(Object... args) {
			try {
				return method.invoke(util, args);
			} catch (Exception e) {
				e.printStackTrace();
			}
			return null;
		}
		
	}

	public static Map<String, Executor> utils = new ConcurrentHashMap<>();

	public static void register(Util util) {
		if (util == null) {
			return;
		}
		for (Method method : util.getClass().getDeclaredMethods()) {
			if (Modifier.isPublic(method.getModifiers()) && !GET_DOMAIN.equals(method.getName())) {
				utils.put(buildFullName(util,method), new Executor(util, method));
			}
		}
	}

	public static String buildFullName(Util util,Method method) {
		return util.getDomain()+modifyMethodName(method.getName());
	}
	private static String modifyMethodName(String name) {
		char[] array = name.toCharArray();
		array[0] =Character.toUpperCase(array[0]);
		return new String(array);
	}
	
	
	public static Object util(Object... args) {
		StackTraceElement stack = Thread.currentThread().getStackTrace()[2];
		String methodName = stack.getMethodName();
		Class<?>[] paramTypes = null;
		if (args != null) {
			paramTypes = Arrays.asList(args).stream().map(Object::getClass).toArray(Class[]::new);
		}
		for (Entry<String, Executor> entry:utils.entrySet()) {
			if (entry.getKey().equals(methodName)){
				Method method= entry.getValue().method;
				if(method.getParameterCount()==args.length) {
					if (args.length==0||BeanUtils.paramsFit(paramTypes, method.getParameterTypes())) {
						return entry.getValue().execute(args);
					}
				}
			}
		
		}
		return null;
	}

}
