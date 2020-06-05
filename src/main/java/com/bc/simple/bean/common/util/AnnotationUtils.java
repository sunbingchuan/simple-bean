package com.bc.simple.bean.common.util;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.bc.simple.bean.common.annotation.Configuration;
import com.bc.simple.bean.common.annotation.Lazy;
import com.bc.simple.bean.common.annotation.Order;
import com.bc.simple.bean.common.annotation.Value;
import com.bc.simple.bean.core.support.AnnotationMetaData;
import com.bc.simple.bean.core.support.DependencyDescriptor;

@SuppressWarnings("unchecked")
public class AnnotationUtils {

	private static final Log LOG = LogFactory.getLog(AnnotationUtils.class);

	private static final Map<AnnotatedElement, Annotation[]> declaredAnnotationsCache = new ConcurrentHashMap<>(256);

	private static final Map<AnnotationCacheKey, Annotation> findAnnotationCache = new ConcurrentHashMap<>(256);
	
	private static final Map<Class<?>, Set<Method>> annotatedBaseTypeCache = new ConcurrentHashMap<>(256);

	/** Cache marker for a non-annotated Class. */
	private static final Object NOT_ANNOTATED = new Object();

	private static Class<? extends Annotation> priorityAnnotationType;
	
	/** Cache for @Order value (or NOT_ANNOTATED marker) per Class. */
	private static final Map<Class<?>, Object> orderCache = new ConcurrentHashMap<>(64);

	/** Cache for @Priority value (or NOT_ANNOTATED marker) per Class. */
	private static final Map<Class<?>, Object> priorityCache = new ConcurrentHashMap<>();

	
	private static final AnnotationTransfer annotationTransfer =new AnnotationTransfer();
	

	public static final String VALUE = "value";

	
	static {
		try {
			priorityAnnotationType = (Class<? extends Annotation>) BeanUtils.forName("javax.annotation.Priority",
					AnnotationUtils.class.getClassLoader());
		} catch (Throwable ex) {
			// javax.annotation.Priority not available
			priorityAnnotationType = null;
		}
	}
	
	public static boolean isInJavaLangAnnotationPackage(String annotationType) {
		return (annotationType != null && annotationType.startsWith("java.lang.annotation"));
	}
	public static Object findValue(AnnotatedElement annotatedElement) {
		Map<Class<?>, Map<String, Object>> define = AnnotationUtils.transferAnnotation(annotatedElement);
		Map<String, Object> v= define.get(Value.class);
		if (v!=null) { // qualifier annotations have to be local
			return v.get(VALUE);
		}
		return null;
	}
	public static Object getValue(Annotation annotation) {
		return getAttr(annotation, VALUE);
	}
	public static Object getAttr(Annotation annotation, String attributeName) {
		if (annotation == null || !StringUtils.hasText(attributeName)) {
			return null;
		}
		try {
			Method method = annotation.annotationType().getDeclaredMethod(attributeName);
			method.setAccessible(true);
			return method.invoke(annotation);
		} catch (NoSuchMethodException ex) {
		} catch (InvocationTargetException ex) {
			LOG.info("Could not obtain value for annotation attribute '" + attributeName + "' in " + annotation, ex);
		} catch (Throwable ex) {
		}
		return null;
	}
	public static Annotation[] getDeclaredAnnotations(AnnotatedElement element) {
		if (element instanceof Class || element instanceof Member) {
			return declaredAnnotationsCache.computeIfAbsent(element, AnnotatedElement::getDeclaredAnnotations);
		}
		return element.getDeclaredAnnotations();
	}
	
	public static boolean isLazy(DependencyDescriptor descriptor) {
			Lazy lazy = findAnnotation(descriptor.getAnnotatedElement(), Lazy.class);
			if (lazy != null && lazy.value()) {
				return true;
			}
			return false;
	}

	

	public static Method findOriginalMethod(Method bridgeMethod) {
		if (!bridgeMethod.isBridge()) {
			return bridgeMethod;
		}

		// Gather all methods with matching name and parameter size.
		List<Method> candidateMethods = new ArrayList<>();
		Method[] methods = bridgeMethod.getDeclaringClass().getDeclaredMethods();
		for (Method candidateMethod : methods) {
			if (!candidateMethod.isBridge() && !candidateMethod.equals(bridgeMethod)
					&& candidateMethod.getName().equals(bridgeMethod.getName())
					&& candidateMethod.getParameterCount() == bridgeMethod.getParameterCount()) {
				candidateMethods.add(candidateMethod);
			}
		}

		// Now perform simple quick check.
		if (candidateMethods.size() > 0) {
			return candidateMethods.get(0);
		}

		return null;
	}
	public static Annotation[] getAnnotations(Method method) {
		try {
			return findOriginalMethod(method).getAnnotations();
		} catch (Throwable ex) {
			return null;
		}
	}
	
	public static boolean isFullConfigurationCandidate(AnnotationMetaData metadata) {
		return metadata.isAnnotated(Configuration.class.getName());
	}
	

	@SuppressWarnings("unchecked")
	public static <A extends Annotation> A findAnnotation(AnnotatedElement annotatedElement, Class<A> annotationType) {
		if (annotationType == null) {
			return null;
		}
		AnnotationCacheKey cacheKey = new AnnotationCacheKey(annotatedElement, annotationType);
		A result = (A) findAnnotationCache.get(cacheKey);
		if (result == null) {
			if (annotatedElement instanceof Method) {
				result = findAnnotation((Method)annotatedElement, annotationType);
			}else if(annotatedElement instanceof Class) {
				result = findAnnotation((Class<?>)annotatedElement, annotationType);
			}else {
				result = findAnnotation(annotatedElement, annotationType, new HashSet<>());
			}
			if (result != null) {
				findAnnotationCache.put(cacheKey, result);
			}
		}
		return result;
	}

	private static <A extends Annotation> A findAnnotation(Class<?> clazz, Class<A> annotationType) {
		return findAnnotation(clazz, annotationType, new HashSet<>());
}
	private static <A extends Annotation> A findAnnotation(Class<?> clazz, Class<A> annotationType,
			Set<Annotation> visited) {
		try {
			A annotation = clazz.getDeclaredAnnotation(annotationType);
			if (annotation != null) {
				return annotation;
			}
			for (Annotation declaredAnn : getDeclaredAnnotations(clazz)) {
				Class<? extends Annotation> declaredType = declaredAnn.annotationType();
				if (!isInJavaLangAnnotationPackage(declaredType.getName()) && visited.add(declaredAnn)) {
					annotation = findAnnotation(declaredType, annotationType, visited);
					if (annotation != null) {
						return annotation;
					}
				}
			}
		} catch (Throwable ex) {
			return null;
		}
		for (Class<?> ifc : clazz.getInterfaces()) {
			A annotation = findAnnotation(ifc, annotationType, visited);
			if (annotation != null) {
				return annotation;
			}
		}
		Class<?> superclass = clazz.getSuperclass();
		if (superclass == null || superclass == Object.class) {
			return null;
		}
		return findAnnotation(superclass, annotationType, visited);
	}
	
	private static <A extends Annotation> A findAnnotation(Method method, Class<A> annotationType) {
		if (annotationType == null) {
			return null;
		}
			Method resolvedMethod = findOriginalMethod(method);
			A result = findAnnotation((AnnotatedElement) resolvedMethod, annotationType,new HashSet<>());
			Class<?> clazz = method.getDeclaringClass();
			while (result == null) {
				clazz = clazz.getSuperclass();
				if (clazz == null || clazz == Object.class) {
					break;
				}
				result = findOverrideAnnotatedMethod(method, annotationType, clazz);
				if (result != null) {
					break;
				} else {
					result = searchOnInterfaces(method, annotationType, clazz.getInterfaces());
				}
			}
		return result;
	}
	


	private static <A extends Annotation> A findAnnotation(AnnotatedElement annotatedElement, Class<A> annotationType,
			Set<Annotation> visited) {
		try {
			A annotation = annotatedElement.getDeclaredAnnotation(annotationType);
			if (annotation != null) {
				return annotation;
			}
			for (Annotation declaredAnn : getDeclaredAnnotations(annotatedElement)) {
				Class<? extends Annotation> declaredType = declaredAnn.annotationType();
				if (!isInJavaLangAnnotationPackage(declaredType.getName()) && visited.add(declaredAnn)) {
					annotation = findAnnotation(declaredType, annotationType, visited);
					if (annotation != null) {
						return annotation;
					}
				}
			}
		} catch (Throwable ex) {
			//
		}
		return null;
	}
	
	private static <A extends Annotation> A searchOnInterfaces(Method method, Class<A> annotationType,
			Class<?>... ifcs) {
		for (Class<?> ifc : ifcs) {
			A annotation = findOverrideAnnotatedMethod(method, annotationType, ifc);
			if (annotation != null) {
				return annotation;
			}
		}
		return null;
	}
	
	private static boolean isOverride(Method method, Method candidate) {
		if (!candidate.getName().equals(method.getName())
				|| candidate.getParameterCount() != method.getParameterCount()) {
			return false;
		}
		Class<?>[] paramTypes = method.getParameterTypes();
		Class<?>[] validateParamTypes = candidate.getParameterTypes();
		if (Arrays.equals(validateParamTypes, paramTypes)) {
			return true;
		}
		return false;
	}

	
	private static <A extends Annotation> A findOverrideAnnotatedMethod(Method method, Class<A> annotationType,
			Class<?> ifc) {
		Set<Method> annotatedMethods = getAnnotatedMethodsInBaseType(ifc);
		if (!annotatedMethods.isEmpty()) {
			for (Method annotatedMethod : annotatedMethods) {
				if (isOverride(method, annotatedMethod)) {
					A annotation = findAnnotation((AnnotatedElement) annotatedMethod, annotationType);
					if (annotation != null) {
						return annotation;
					}
				}
			}
		}
		return null;
	}
	
	private static Set<Method> getAnnotatedMethodsInBaseType(Class<?> baseType) {
		boolean ifcCheck = baseType.isInterface();
		if (ifcCheck && BeanUtils.isJavaLanguageInterface(baseType)) {
			return Collections.emptySet();
		}
		Set<Method> annotatedMethods = annotatedBaseTypeCache.get(baseType);
		if (annotatedMethods != null) {
			return annotatedMethods;
		}
		Method[] methods = (ifcCheck ? baseType.getMethods() : baseType.getDeclaredMethods());
		for (Method baseMethod : methods) {
			try {
				// Public methods on interfaces (including interface hierarchy),
				// non-private (and therefore overridable) methods on base classes
				if ((ifcCheck || !Modifier.isPrivate(baseMethod.getModifiers()))
						&& hasSearchableAnnotations(baseMethod)) {
					if (annotatedMethods == null) {
						annotatedMethods = new HashSet<>();
					}
					annotatedMethods.add(baseMethod);
				}
			} catch (Throwable ex) {
			}
		}
		if (annotatedMethods == null) {
			annotatedMethods = Collections.emptySet();
		}
		annotatedBaseTypeCache.put(baseType, annotatedMethods);
		return annotatedMethods;
	}
	
	private static boolean hasSearchableAnnotations(Method ifcMethod) {
		Annotation[] anns = getDeclaredAnnotations(ifcMethod);
		if (anns.length == 0) {
			return false;
		}
		for (Annotation ann : anns) {
			String name = ann.annotationType().getName();
			if (!name.startsWith("java.lang.")) {
				return true;
			}
		}
		return false;
	}
	
	public static Integer getOrder(Class<?> type) {
		Object cached = orderCache.get(type);
		if (cached != null) {
			return (cached instanceof Integer ? (Integer) cached : null);
		}
		Order order = findAnnotation(type, Order.class);
		Integer result;
		if (order != null) {
			result = order.value();
		} else {
			result = getPriority(type);
		}
		orderCache.put(type, (result != null ? result : NOT_ANNOTATED));
		return result;
	}


	public static Integer getPriority(Class<?> type) {
		if (priorityAnnotationType == null) {
			return null;
		}
		Object cached = priorityCache.get(type);
		if (cached != null) {
			return (cached instanceof Integer ? (Integer) cached : null);
		}
		Annotation priority = findAnnotation(type, priorityAnnotationType);
		Integer result = null;
		if (priority != null) {
			result = (Integer) getValue(priority);
		}
		priorityCache.put(type, (result != null ? result : NOT_ANNOTATED));
		return result;
	}
	
	
	private static final class AnnotationCacheKey implements Comparable<AnnotationCacheKey> {

		private final AnnotatedElement element;

		private final Class<? extends Annotation> annotationType;

		public AnnotationCacheKey(AnnotatedElement element, Class<? extends Annotation> annotationType) {
			this.element = element;
			this.annotationType = annotationType;
		}

		@Override
		public boolean equals(Object other) {
			if (this == other) {
				return true;
			}
			if (!(other instanceof AnnotationCacheKey)) {
				return false;
			}
			AnnotationCacheKey otherKey = (AnnotationCacheKey) other;
			return (this.element.equals(otherKey.element) && this.annotationType.equals(otherKey.annotationType));
		}

		@Override
		public int hashCode() {
			return (this.element.hashCode() * 29 + this.annotationType.hashCode());
		}

		@Override
		public String toString() {
			return "@" + this.annotationType + " on " + this.element;
		}

		@Override
		public int compareTo(AnnotationCacheKey other) {
			int result = this.element.toString().compareTo(other.element.toString());
			if (result == 0) {
				result = this.annotationType.getName().compareTo(other.annotationType.getName());
			}
			return result;
		}
	}
	
	public static Map<Class<?>, Map<String, Object>> transferAnnotation(AnnotatedElement annotatedElement) {
		return annotationTransfer.transferAnnotation(annotatedElement);
	}

	public  Map<Class<? extends Annotation>, Set<String>> fromAliasFor(String annotationType, String attribute) {
		return annotationTransfer.fromAliasFor(annotationType,attribute);
	}

	public  Map<Class<? extends Annotation>, Set<String>> fromAliasFor(Class<?> annotationType, String attribute) {		
		return annotationTransfer.fromAliasFor(annotationType,attribute);

	}

	public  static Map<Class<? extends Annotation>, Set<String>> fromAliasFor(Method attribute) {
		return annotationTransfer.fromAliasFor(attribute);
	}

	
}
