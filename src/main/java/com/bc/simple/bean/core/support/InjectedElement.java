package com.bc.simple.bean.core.support;

import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Parameter;
import java.util.Map;

public class InjectedElement {
	private Executable method;
	private Field field;
	private Parameter parameter;

	private Map<String, Object> annotationAttributes;
	private String injectName;
	private String injectObject;

	public InjectedElement() {
	}

	public InjectedElement(DependencyDescriptor desc) {
		this.field = desc.getField();
		this.parameter = desc.getMethodParameter();
		this.method = this.parameter != null ? this.parameter.getDeclaringExecutable() : null;
	}

	public Executable getMethod() {
		return method;
	}

	public void setMethod(Executable method) {
		this.method = method;
	}

	public Field getField() {
		return field;
	}

	public void setField(Field field) {
		this.field = field;
	}

	public String getInjectName() {
		return injectName;
	}

	public void setInjectName(String injectName) {
		this.injectName = injectName;
	}

	public String getInjectObject() {
		return injectObject;
	}

	public void setInjectObject(String injectObject) {
		this.injectObject = injectObject;
	}

	public Map<String, Object> getAnnotationAttributes() {
		return annotationAttributes;
	}

	public void setAnnotationAttributes(Map<String, Object> annotationAttributes) {
		this.annotationAttributes = annotationAttributes;
	}

	public Parameter getParameter() {
		return parameter;
	}

	public void setParameter(Parameter parameter) {
		this.parameter = parameter;
	}

}
