package com.bc.simple.bean.core.support;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Optional;

import com.bc.simple.bean.BeanFactory;
import com.bc.simple.bean.common.util.ObjectUtils;
import com.sun.istack.internal.Nullable;

@SuppressWarnings("restriction")
public class DependencyDescriptor {

	@Nullable
	protected Parameter methodParameter;

	@Nullable
	protected Field field;

	@Nullable
	private volatile Annotation[] fieldAnnotations;

	private final Class<?> declaringClass;

	@Nullable
	private String methodName;

	@Nullable
	private Class<?>[] parameterTypes;

	private int parameterIndex;

	@Nullable
	private String fieldName;

	private final boolean required;

	private final boolean eager;

	private int nestingLevel = 1;

	@Nullable
	private Class<?> containingClass;

	@Nullable
	private transient volatile Object resolvableType;

	/**
	 * Create a new descriptor for a method or constructor parameter. Considers the
	 * dependency as 'eager'.
	 * 
	 * @param methodParameter the MethodParameter to wrap
	 * @param required        whether the dependency is required
	 */
	public DependencyDescriptor(Parameter methodParameter, boolean required) {
		this(methodParameter, required, true);
	}

	/**
	 * Create a new descriptor for a method or constructor parameter.
	 * 
	 * @param methodParameter the MethodParameter to wrap
	 * @param required        whether the dependency is required
	 * @param eager           whether this dependency is 'eager' in the sense of
	 *                        eagerly resolving potential target beans for type
	 *                        matching
	 */
	public DependencyDescriptor(Parameter methodParameter, boolean required, boolean eager) {

		this.methodParameter = methodParameter;
		this.declaringClass = methodParameter.getDeclaringExecutable().getDeclaringClass();
		if (methodParameter.getDeclaringExecutable() != null) {
			this.methodName = methodParameter.getDeclaringExecutable().getName();
		}
		this.parameterTypes = methodParameter.getDeclaringExecutable().getParameterTypes();
		this.containingClass = methodParameter.getDeclaringExecutable().getDeclaringClass();
		this.required = required;
		this.eager = eager;
	}

	/**
	 * Create a new descriptor for a field. Considers the dependency as 'eager'.
	 * 
	 * @param field    the field to wrap
	 * @param required whether the dependency is required
	 */
	public DependencyDescriptor(Field field, boolean required) {
		this(field, required, true);
	}

	/**
	 * Create a new descriptor for a field.
	 * 
	 * @param field    the field to wrap
	 * @param required whether the dependency is required
	 * @param eager    whether this dependency is 'eager' in the sense of eagerly
	 *                 resolving potential target beans for type matching
	 */
	public DependencyDescriptor(Field field, boolean required, boolean eager) {
		this.field = field;
		this.declaringClass = field.getDeclaringClass();
		this.fieldName = field.getName();
		this.required = required;
		this.eager = eager;
	}

	/**
	 * Copy constructor.
	 * 
	 * @param original the original descriptor to create a copy from
	 */
	public DependencyDescriptor(DependencyDescriptor original) {
		this.methodParameter = original.getMethodParameter();
		this.field = original.field;
		this.fieldAnnotations = original.fieldAnnotations;
		this.declaringClass = original.declaringClass;
		this.methodName = original.methodName;
		this.parameterTypes = original.parameterTypes;
		this.parameterIndex = original.parameterIndex;
		this.fieldName = original.fieldName;
		this.containingClass = original.containingClass;
		this.required = original.required;
		this.eager = original.eager;
		this.nestingLevel = original.nestingLevel;
	}

	/**
	 * Return whether this dependency is required.
	 * <p>
	 * Optional semantics are derived from Java 8's {@link java.util.Optional}, any
	 * variant of a parameter-level {@code Nullable} annotation (such as from
	 * JSR-305 or the FindBugs set of annotations), or a language-level nullable
	 * type declaration in Kotlin.
	 */
	public boolean isRequired() {
		if (!this.required) {
			return false;
		}

		if (this.field != null) {
			return !(this.field.getType() == Optional.class || hasNullableAnnotation());
		} else {
			return !(obtainMethodParameter().getType() == Optional.class);
		}
	}

	/**
	 * Check whether the underlying field is annotated with any variant of a
	 * {@code Nullable} annotation, e.g. {@code javax.annotation.Nullable} or
	 * {@code edu.umd.cs.findbugs.annotations.Nullable}.
	 */
	private boolean hasNullableAnnotation() {
		for (Annotation ann : getAnnotations()) {
			if ("Nullable".equals(ann.annotationType().getSimpleName())) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Return whether this dependency is 'eager' in the sense of eagerly resolving
	 * potential target beans for type matching.
	 */
	public boolean isEager() {
		return this.eager;
	}

	/**
	 * Return whether this descriptor allows for stream-style access to result
	 * instances.
	 * <p>
	 * By default, dependencies are strictly resolved to the declaration of the
	 * injection point and therefore only resolve multiple entries if the injection
	 * point is declared as an array, collection or map. This is indicated by
	 * returning {@code false} here.
	 * <p>
	 * Overriding this method to return {@code true} indicates that the injection
	 * point declares the bean type but the resolution is meant to end up in a
	 * {@link java.util.stream.Stream} for the declared bean type, with the caller
	 * handling the multi-instance case for the injection point.
	 * 
	 * @since 5.1
	 */
	public boolean isStreamAccess() {
		return false;
	}

	/**
	 * Resolve a shortcut for this dependency against the given factory, for example
	 * taking some pre-resolved information into account.
	 * <p>
	 * The resolution algorithm will first attempt to resolve a shortcut through
	 * this method before going into the regular type matching algorithm across all
	 * beans. Subclasses may override this method to improve resolution performance
	 * based on pre-cached information while still receiving {@link InjectionPoint}
	 * exposure etc.
	 * 
	 * @param beanFactory the associated factory
	 * @return the shortcut result if any, or {@code null} if none @ if the shortcut
	 *         could not be obtained @since 4.3.1
	 */
	@Nullable
	public Object resolveShortcut(BeanFactory beanFactory) {
		return null;
	}

	/**
	 * Resolve the specified bean name, as a candidate result of the matching
	 * algorithm for this dependency, to a bean instance from the given factory.
	 * <p>
	 * The default implementation calls {@link BeanFactory#getBean(String)}.
	 * Subclasses may provide additional arguments or other customizations.
	 * 
	 * @param beanName     the bean name, as a candidate result for this dependency
	 * @param requiredType the expected type of the bean (as an assertion)
	 * @param beanFactory  the associated factory
	 * @return the bean instance (never {@code null}) @ if the bean could not be
	 *         obtained @since 4.3.2
	 * @see BeanFactory#getBean(String)
	 */
	public Object resolveCandidate(String beanName, Class<?> requiredType, BeanFactory beanFactory) {

		return beanFactory.getBean(beanName);
	}

	/**
	 * Increase this descriptor's nesting level.
	 * 
	 * @see MethodParameter#increaseNestingLevel()
	 */
	public void increaseNestingLevel() {
		this.nestingLevel++;
		this.resolvableType = null;
	}

	/**
	 * Optionally set the concrete class that contains this dependency. This may
	 * differ from the class that declares the parameter/field in that it may be a
	 * subclass thereof, potentially substituting type variables.
	 * 
	 * @since 4.0
	 */
	public void setContainingClass(Class<?> containingClass) {
		this.containingClass = containingClass;
		this.resolvableType = null;
	}

	/**
	 * Build a ResolvableType object for the wrapped parameter/field.
	 * 
	 * @since 4.0
	 */
	public Object getResolvableType() {
		Object resolvableType = this.resolvableType;
		// something
		return resolvableType;
	}

	/**
	 * Return whether a fallback match is allowed.
	 * <p>
	 * This is {@code false} by default but may be overridden to return {@code true}
	 * in order to suggest to an
	 * {@link org.Factory.beans.factory.support.AutowireCandidateResolver}
	 * that a fallback match is acceptable as well.
	 * 
	 * @since 4.0
	 */
	public boolean fallbackMatchAllowed() {
		return false;
	}

	/**
	 * Return a variant of this descriptor that is intended for a fallback match.
	 * 
	 * @since 4.0
	 * @see #fallbackMatchAllowed()
	 */
	public DependencyDescriptor forFallbackMatch() {
		return new DependencyDescriptor(this) {
			@Override
			public boolean fallbackMatchAllowed() {
				return true;
			}
		};
	}

	/**
	 * Determine the name of the wrapped parameter/field.
	 * 
	 * @return the declared name (never {@code null})
	 */
	@Nullable
	public String getDependencyName() {
		return (this.field != null ? this.field.getName() : obtainMethodParameter().getName());
	}

	/**
	 * Determine the declared (non-generic) type of the wrapped parameter/field.
	 * 
	 * @return the declared type (never {@code null})
	 */
	public Class<?> getDependencyType() {
		if (this.field != null) {
			if (this.nestingLevel > 1) {
				Type type = this.field.getGenericType();
				for (int i = 2; i <= this.nestingLevel; i++) {
					if (type instanceof ParameterizedType) {
						Type[] args = ((ParameterizedType) type).getActualTypeArguments();
						type = args[args.length - 1];
					}
				}
				if (type instanceof Class) {
					return (Class<?>) type;
				} else if (type instanceof ParameterizedType) {
					Type arg = ((ParameterizedType) type).getRawType();
					if (arg instanceof Class) {
						return (Class<?>) arg;
					}
				}
				return Object.class;
			} else {
				return this.field.getType();
			}
		} else {
			return obtainMethodParameter().getType();
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
				&& this.required == otherPoint.required && this.eager == otherPoint.eager
				&& this.nestingLevel == otherPoint.nestingLevel && this.containingClass == otherPoint.containingClass);
	}

	@Override
	public int hashCode() {
		int hash = (this.field != null ? this.field.hashCode() : ObjectUtils.nullSafeHashCode(this.methodParameter));
		return 31 * hash + ObjectUtils.nullSafeHashCode(this.containingClass);
	}

	// ---------------------------------------------------------------------
	// Serialization support
	// ---------------------------------------------------------------------

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

	/**
	 * Return the wrapped MethodParameter, if any.
	 * <p>
	 * Note: Either MethodParameter or Field is available.
	 * 
	 * @return the MethodParameter, or {@code null} if none
	 */
	@Nullable
	public Parameter getMethodParameter() {
		return this.methodParameter;
	}

	/**
	 * Return the wrapped Field, if any.
	 * <p>
	 * Note: Either MethodParameter or Field is available.
	 * 
	 * @return the Field, or {@code null} if none
	 */
	@Nullable
	public Field getField() {
		return this.field;
	}

	/**
	 * Return the wrapped MethodParameter, assuming it is present.
	 * 
	 * @return the MethodParameter (never {@code null})
	 * @throws IllegalStateException if no MethodParameter is available
	 * @since 5.0
	 */
	protected final Parameter obtainMethodParameter() {
		return this.methodParameter;
	}

	/**
	 * Obtain the annotations associated with the wrapped field or
	 * method/constructor parameter.
	 */
	public Annotation[] getAnnotations() {
		if (this.field != null) {
			Annotation[] fieldAnnotations = this.fieldAnnotations;
			if (fieldAnnotations == null) {
				fieldAnnotations = this.field.getAnnotations();
				this.fieldAnnotations = fieldAnnotations;
			}
			return fieldAnnotations;
		} else {
			return obtainMethodParameter().getAnnotations();
		}
	}

	/**
	 * Retrieve a field/parameter annotation of the given type, if any.
	 * 
	 * @param annotationType the annotation type to retrieve
	 * @return the annotation instance, or {@code null} if none found
	 * @since 4.3.9
	 */
	@Nullable
	public <A extends Annotation> A getAnnotation(Class<A> annotationType) {
		return (this.field != null ? this.field.getAnnotation(annotationType)
				: obtainMethodParameter().getAnnotation(annotationType));
	}

	/**
	 * Return the type declared by the underlying field or method/constructor
	 * parameter, indicating the injection type.
	 */
	public Class<?> getDeclaredType() {
		return (this.field != null ? this.field.getType() : obtainMethodParameter().getType());
	}

	/**
	 * Returns the wrapped member, containing the injection point.
	 * 
	 * @return the Field / Method / Constructor as Member
	 */
	public Member getMember() {
		return (this.field != null ? this.field : obtainMethodParameter().getDeclaringExecutable());
	}

	/**
	 * Return the wrapped annotated element.
	 * <p>
	 * Note: In case of a method/constructor parameter, this exposes the annotations
	 * declared on the method or constructor itself (i.e. at the method/constructor
	 * level, not at the parameter level). Use {@link #getAnnotations()} to obtain
	 * parameter-level annotations in such a scenario, transparently with
	 * corresponding field annotations.
	 * 
	 * @return the Field / Method / Constructor as AnnotatedElement
	 */
	public AnnotatedElement getAnnotatedElement() {
		return (this.field != null ? this.field : obtainMethodParameter().getDeclaringExecutable());
	}

	@Override
	public String toString() {
		return (this.field != null ? "field '" + this.field.getName() + "'" : String.valueOf(this.methodParameter));
	}

}
