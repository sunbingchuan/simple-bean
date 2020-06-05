package com.bc.simple.bean.core.support;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Parameter;

import com.bc.simple.bean.common.util.ObjectUtils;

public class DependencyDescriptor {

	
	protected Parameter methodParameter;

	
	protected Field field;

	
	private volatile Annotation[] fieldAnnotations;

	private final Class<?> declaringClass;

	
	private String methodName;

	
	private Class<?>[] parameterTypes;

	private int parameterIndex;

	
	private String fieldName;

	private final boolean required;

	
	private Class<?> containingClass;


	
	public DependencyDescriptor(Parameter methodParameter, boolean required) {
		this(methodParameter, required, true);
	}

	
	public DependencyDescriptor(Parameter methodParameter, boolean required, boolean eager) {

		this.methodParameter = methodParameter;
		this.declaringClass = methodParameter.getDeclaringExecutable().getDeclaringClass();
		if (methodParameter.getDeclaringExecutable() != null) {
			this.methodName = methodParameter.getDeclaringExecutable().getName();
		}
		this.parameterTypes = methodParameter.getDeclaringExecutable().getParameterTypes();
		this.containingClass = methodParameter.getDeclaringExecutable().getDeclaringClass();
		this.required = required;
	}

	
	public DependencyDescriptor(Field field, boolean required) {
		this(field, required, true);
	}

	
	public DependencyDescriptor(Field field, boolean required, boolean eager) {
		this.field = field;
		this.declaringClass = field.getDeclaringClass();
		this.fieldName = field.getName();
		this.required = required;
	}

	
	public DependencyDescriptor(DependencyDescriptor original) {
		this.methodParameter = original.methodParameter;
		this.field = original.field;
		this.fieldAnnotations = original.fieldAnnotations;
		this.declaringClass = original.declaringClass;
		this.methodName = original.methodName;
		this.parameterTypes = original.parameterTypes;
		this.parameterIndex = original.parameterIndex;
		this.fieldName = original.fieldName;
		this.containingClass = original.containingClass;
		this.required = original.required;
	}

	
	public boolean isRequired() {
		return this.required;
	}

	
	public void setContainingClass(Class<?> containingClass) {
		this.containingClass = containingClass;
	}


	
	public String getDependencyName() {
		return (this.field != null ? this.field.getName() : getMethodParameter().getName());
	}

	
	public Class<?> getDependencyType() {
		if (this.field != null) {
			return this.field.getType();
		} else {
			return getMethodParameter().getType();
		}
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (getClass() != other.getClass()) {
			return false;
		}
		DependencyDescriptor otherPoint = (DependencyDescriptor) other;
		return (ObjectUtils.nullSafeEquals(this.field, otherPoint.field)
				&& ObjectUtils.nullSafeEquals(this.methodParameter, otherPoint.methodParameter)
				&& this.required == otherPoint.required 
				&& this.containingClass == otherPoint.containingClass);
	}

	@Override
	public int hashCode() {
		int hash = (this.field != null ? this.field.hashCode() : ObjectUtils.nullSafeHashCode(this.methodParameter));
		return 31 * hash + ObjectUtils.nullSafeHashCode(this.containingClass);
	}


	private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
		// Rely on default serialization; just initialize state after deserialization.
		ois.defaultReadObject();

		// Restore reflective handles (which are unfortunately not serializable)
		try {
			if (this.fieldName != null) {
				this.field = this.declaringClass.getDeclaredField(this.fieldName);
			} else {
				if (this.methodName != null) {
					this.methodParameter = this.declaringClass.getDeclaredMethod(this.methodName, this.parameterTypes)
							.getParameters()[this.parameterIndex];
				} else {
					this.methodParameter = this.declaringClass.getDeclaredConstructor(this.parameterTypes)
							.getParameters()[this.parameterIndex];
				}
			}
		} catch (Throwable ex) {
			throw new IllegalStateException("Could not find original class structure", ex);
		}
	}

	
	
	public Parameter getMethodParameter() {
		return this.methodParameter;
	}

	
	
	public Field getField() {
		return this.field;
	}

	
	
	public <A extends Annotation> A getAnnotation(Class<A> annotationType) {
		return (this.field != null ? this.field.getAnnotation(annotationType)
				: getMethodParameter().getAnnotation(annotationType));
	}

	
	public Class<?> getDeclaredType() {
		return (this.field != null ? this.field.getType() : getMethodParameter().getType());
	}

	
	public Member getMember() {
		return (this.field != null ? this.field : getMethodParameter().getDeclaringExecutable());
	}

	
	public AnnotatedElement getAnnotatedElement() {
		return (this.field != null ? this.field : getMethodParameter());
	}

	@Override
	public String toString() {
		return (this.field != null ? "field '" + this.field.getName() + "'" : String.valueOf(this.methodParameter));
	}

}
