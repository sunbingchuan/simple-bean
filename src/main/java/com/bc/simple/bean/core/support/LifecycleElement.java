package com.bc.simple.bean.core.support;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import com.bc.simple.bean.common.util.BeanUtils;


public class LifecycleElement {

	private String lifecycleType;

	private final Method method;

	private final String identifier;

	public LifecycleElement(Method method, String lifecycleType) {
		if (method.getParameterCount() != 0) {
			throw new IllegalStateException("Lifecycle method annotation requires a no-arg method: " + method);
		}
		this.method = method;
		this.identifier = (Modifier.isPrivate(method.getModifiers()) ? BeanUtils.getQualifiedMethodName(method)
				: method.getName());
		this.lifecycleType = lifecycleType;
	}

	public Method getMethod() {
		return this.method;
	}

	public String getIdentifier() {
		return this.identifier;
	}

	public void invoke(Object target) throws Throwable {
		this.method.setAccessible(true);
		this.method.invoke(target, (Object[]) null);
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof LifecycleElement)) {
			return false;
		}
		LifecycleElement otherElement = (LifecycleElement) other;
		return (this.identifier.equals(otherElement.identifier));
	}

	@Override
	public int hashCode() {
		return this.identifier.hashCode();
	}

	public String getLifecycleType() {
		return lifecycleType;
	}

	public void setLifecycleType(String lifecycleType) {
		this.lifecycleType = lifecycleType;
	}


}
