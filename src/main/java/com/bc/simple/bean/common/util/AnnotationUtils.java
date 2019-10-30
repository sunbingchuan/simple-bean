package com.bc.simple.bean.common.util;

import static com.bc.simple.bean.common.util.Constant.ATTR_ALIASES_ANNOTATION_TYPE;
import static com.bc.simple.bean.common.util.Constant.ATTR_ALIASES_ATTRIBUTE;
import static com.bc.simple.bean.common.util.Constant.ATTR_SOURCE_ATTRIBUTE;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.processing.Processor;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.bc.simple.bean.BeanDefinition;
import com.bc.simple.bean.BeanFactory;
import com.bc.simple.bean.common.config.ConfigLoader.Node;
import com.bc.simple.bean.common.stereotype.AliasFor;
import com.bc.simple.bean.common.stereotype.Configuration;
import com.bc.simple.bean.common.stereotype.DependsOn;
import com.bc.simple.bean.common.stereotype.Lazy;
import com.bc.simple.bean.common.stereotype.Order;
import com.bc.simple.bean.common.stereotype.Primary;
import com.bc.simple.bean.common.stereotype.Scope;
import com.bc.simple.bean.common.stereotype.Value;
import com.bc.simple.bean.core.processor.AutowiredAnnotationProcessor;
import com.bc.simple.bean.core.processor.ConfigurationClassProcessor;
import com.bc.simple.bean.core.support.AnnotationMetaData;
import com.bc.simple.bean.core.support.DependencyDescriptor;
import com.bc.simple.bean.core.support.Ordered;

@SuppressWarnings("unchecked")
public class AnnotationUtils {

	private static final Log LOG = LogFactory.getLog(AnnotationUtils.class);

	private static final List<String> annotationFilters = new LinkedList<>();

	/** Cache for @Order value (or NOT_ANNOTATED marker) per Class. */
	private static final Map<Class<?>, Object> orderCache = new ConcurrentHashMap<>(64);

	/** Cache for @Priority value (or NOT_ANNOTATED marker) per Class. */
	private static final Map<Class<?>, Object> priorityCache = new ConcurrentHashMap<>();

	private static final Map<AnnotationCacheKey, Annotation> findAnnotationCache = new ConcurrentHashMap<>(256);

	/**
	 * The attribute name for annotations with a single element.
	 */
	public static final String VALUE = "value";

	private static final Map<AnnotatedElement, Annotation[]> declaredAnnotationsCache = new ConcurrentHashMap<>(256);

	private static final Map<Class<?>, Set<Method>> annotatedBaseTypeCache = new ConcurrentHashMap<>(256);

	private static final Map<Class<? extends Annotation>, List<Method>> attributeMethodsCache = new ConcurrentHashMap<>(
			256);

	private static final Map<Method, Map<String, Object>> aliasDescriptorCache = new ConcurrentHashMap<>(256);

	/** Cache marker for a non-annotated Class. */
	private static final Object NOT_ANNOTATED = new Object();

	private static Class<? extends Annotation> priorityAnnotationType;

	static {
		try {
			annotationFilters.add("com.bc.spring.reduce.common.stereotype.Component");
			priorityAnnotationType = (Class<? extends Annotation>) BeanUtils.forName("javax.annotation.Priority",
					AnnotationUtils.class.getClassLoader());
		} catch (Throwable ex) {
			// javax.annotation.Priority not available
			priorityAnnotationType = null;
		}
	}

	/**
	 * Determine if the {@link Annotation} with the supplied name is defined in the
	 * core JDK {@code java.lang.annotation} package.
	 * 
	 * @param annotationType the name of the annotation type to check
	 * @return {@code true} if the annotation is in the {@code java.lang.annotation}
	 *         package
	 * @since 4.2
	 */
	public static boolean isInJavaLangAnnotationPackage(String annotationType) {
		return (annotationType != null && annotationType.startsWith("java.lang.annotation"));
	}

	public static boolean hasMetadata(AnnotationMetaData metadata, List<String> filters) {
		for (String filter : filters) {
			if (filter.equals(metadata.getClassName())) {
				return true;
			}
		}
		return false;
	}

	public static boolean isComponent(AnnotationMetaData metadata) {
		for (String include : annotationFilters) {
			if (metadata.hasAnnotation(include)) {
				return true;
			}
		}
		return false;
	}

	public static void processCommonDefinitionAnnotations(BeanDefinition abd, AnnotationMetaData metadata) {
		Map<String, Object> scope = attributesFor(metadata, Scope.class);
		if (scope != null) {
			abd.setScope(StringUtils.switchString(scope.get("value")));
		}
		Map<String, Object> lazy = attributesFor(metadata, Lazy.class);
		if (lazy != null) {
			abd.setLazyInit(StringUtils.switchBoolean(lazy.get("value")));
		}
		if (metadata.isAnnotated(Primary.class.getName())) {
			abd.setPrimary(true);
		}
		Map<String, Object> dependsOn = attributesFor(metadata, DependsOn.class);
		if (dependsOn != null) {
			abd.setDependsOn(
					StringUtils.splitByStr(StringUtils.switchString(dependsOn.get("value")), StringUtils.COMMA));
		}

	}

	static Map<String, Object> attributesFor(AnnotationMetaData metadata, Class<?> annotationClass) {
		return metadata.getAttributes(annotationClass.getName());
	}

	public static void registerAnnotationConfigProcessors(Node root, BeanFactory beanFactory) {
		beanFactory.addProcessor(new AutowiredAnnotationProcessor());
		beanFactory.setHasInstantiationAwareBeanProcessors(true);
		beanFactory.addProcessor(new ConfigurationClassProcessor());
	}

	public static Integer findOrder(Object obj) {
		// Check for regular Ordered interface
		Integer order = null;
		if (obj instanceof Ordered) {
			order = ((Ordered) obj).getOrder();
		}
		if (order != null) {
			return order;
		}

		// Check for @Order and @Priority on various kinds of elements
		if (obj instanceof Class) {
			return getOrder((Class<?>) obj);
		} else if (obj instanceof Method) {
			Order ann = findAnnotation((Method) obj, Order.class);
			if (ann != null) {
				return ann.value();
			}
		} else if (obj instanceof AnnotatedElement) {
			Order ann = getAnnotation((AnnotatedElement) obj, Order.class);
			if (ann != null) {
				return ann.value();
			}
		} else {
			order = getOrder(obj.getClass());
		}
		return order;
	}

	/**
	 * Return the order on the specified {@code type}.
	 * <p>
	 * Takes care of {@link Order @Order} and {@code @javax.annotation.Priority}.
	 * 
	 * @param type the type to handle
	 * @return the order value, or {@code null} if none can be found
	 * @see #getPriority(Class)
	 */
	public static Integer getOrder(Class<?> type) {
		Object cached = orderCache.get(type);
		if (cached != null) {
			return (cached instanceof Integer ? (Integer) cached : null);
		}
		Order order = AnnotationUtils.findAnnotation(type, Order.class);
		Integer result;
		if (order != null) {
			result = order.value();
		} else {
			result = getPriority(type);
		}
		orderCache.put(type, (result != null ? result : NOT_ANNOTATED));
		return result;
	}

	/**
	 * Return the value of the {@code javax.annotation.Priority} annotation declared
	 * on the specified type, or {@code null} if none.
	 * 
	 * @param type the type to handle
	 * @return the priority value if the annotation is declared, or {@code null} if
	 *         none
	 */
	public static Integer getPriority(Class<?> type) {
		if (priorityAnnotationType == null) {
			return null;
		}
		Object cached = priorityCache.get(type);
		if (cached != null) {
			return (cached instanceof Integer ? (Integer) cached : null);
		}
		Annotation priority = AnnotationUtils.findAnnotation(type, priorityAnnotationType);
		Integer result = null;
		if (priority != null) {
			result = (Integer) AnnotationUtils.getValue(priority);
		}
		priorityCache.put(type, (result != null ? result : NOT_ANNOTATED));
		return result;
	}

	/**
	 * Retrieve the <em>value</em> of the {@code value} attribute of a
	 * single-element Annotation, given an annotation instance.
	 * 
	 * @param annotation the annotation instance from which to retrieve the value
	 * @return the attribute value, or {@code null} if not found unless the
	 *         attribute value cannot be retrieved due to an
	 *         {@link AnnotationConfigurationException}, in which case such an
	 *         exception will be rethrown
	 * @see #getValue(Annotation, String)
	 */
	public static Object getValue(Annotation annotation) {
		return getValue(annotation, VALUE);
	}

	/**
	 * Retrieve the <em>value</em> of a named attribute, given an annotation
	 * instance.
	 * 
	 * @param annotation    the annotation instance from which to retrieve the value
	 * @param attributeName the name of the attribute value to retrieve
	 * @return the attribute value, or {@code null} if not found unless the
	 *         attribute value cannot be retrieved due to an
	 *         {@link AnnotationConfigurationException}, in which case such an
	 *         exception will be rethrown
	 * @see #getValue(Annotation)
	 * @see #rethrowAnnotationConfigurationException(Throwable)
	 */
	public static Object getValue(Annotation annotation, String attributeName) {
		if (annotation == null || !StringUtils.hasText(attributeName)) {
			return null;
		}
		try {
			Method method = annotation.annotationType().getDeclaredMethod(attributeName);
			method.setAccessible(true);
			return method.invoke(annotation);
		} catch (NoSuchMethodException ex) {
		} catch (InvocationTargetException ex) {
			LOG.info(
					"Could not obtain value for annotation attribute '" + attributeName + "' in " + annotation, ex);
		} catch (Throwable ex) {
		}
		return null;
	}

	/**
	 * Find a single {@link Annotation} of {@code annotationType} on the supplied
	 * {@link Class}, traversing its interfaces, annotations, and superclasses if
	 * the annotation is not <em>directly present</em> on the given class itself.
	 * <p>
	 * This method explicitly handles class-level annotations which are not declared
	 * as {@link java.lang.annotation.Inherited inherited} <em>as well as
	 * meta-annotations and annotations on interfaces</em>.
	 * <p>
	 * The algorithm operates as follows:
	 * <ol>
	 * <li>Search for the annotation on the given class and return it if found.
	 * <li>Recursively search through all annotations that the given class declares.
	 * <li>Recursively search through all interfaces that the given class declares.
	 * <li>Recursively search through the superclass hierarchy of the given class.
	 * </ol>
	 * <p>
	 * Note: in this context, the term <em>recursively</em> means that the search
	 * process continues by returning to step #1 with the current interface,
	 * annotation, or superclass as the class to look for annotations on.
	 * 
	 * @param clazz          the class to look for annotations on
	 * @param annotationType the type of annotation to look for
	 * @return the first matching annotation, or {@code null} if not found
	 */
	public static <A extends Annotation> A findAnnotation(Class<?> clazz, Class<A> annotationType) {
		return findAnnotation(clazz, annotationType, true);
	}

	/**
	 * Perform the actual work for {@link #findAnnotation(AnnotatedElement, Class)},
	 * honoring the {@code synthesize} flag.
	 * 
	 * @param clazz          the class to look for annotations on
	 * @param annotationType the type of annotation to look for
	 * @param synthesize     {@code true} if the result should be
	 *                       {@linkplain #synthesizeAnnotation(Annotation)
	 *                       synthesized}
	 * @return the first matching annotation, or {@code null} if not found
	 * @since 4.2.1
	 */
	public static <A extends Annotation> A findAnnotation(Class<?> clazz, Class<A> annotationType, boolean synthesize) {
		if (annotationType == null) {
			return null;
		}

		AnnotationCacheKey cacheKey = new AnnotationCacheKey(clazz, annotationType);
		A result = (A) findAnnotationCache.get(cacheKey);
		if (result == null) {
			result = findAnnotation(clazz, annotationType, new HashSet<>());
			if (result != null && synthesize) {
				findAnnotationCache.put(cacheKey, result);
			}
		}
		return result;
	}

	/**
	 * Perform the search algorithm for {@link #findAnnotation(Class, Class)},
	 * avoiding endless recursion by tracking which annotations have already been
	 * <em>visited</em>.
	 * 
	 * @param clazz          the class to look for annotations on
	 * @param annotationType the type of annotation to look for
	 * @param visited        the set of annotations that have already been visited
	 * @return the first matching annotation, or {@code null} if not found
	 */
	public static <A extends Annotation> A findAnnotation(Class<?> clazz, Class<A> annotationType,
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

	/**
	 * Retrieve a potentially cached array of declared annotations for the given
	 * element.
	 * 
	 * @param element the annotated element to introspect
	 * @return a potentially cached array of declared annotations (only for internal
	 *         iteration purposes, not for external exposure)
	 * @since 5.1
	 */
	public static Annotation[] getDeclaredAnnotations(AnnotatedElement element) {
		if (element instanceof Class || element instanceof Member) {
			// Class/Field/Method/Constructor returns a defensively cloned array from
			// getDeclaredAnnotations.
			// Since we use our result for internal iteration purposes only, it's safe to
			// use a
			// shared copy.
			return declaredAnnotationsCache.computeIfAbsent(element, AnnotatedElement::getDeclaredAnnotations);
		}
		return element.getDeclaredAnnotations();
	}

	/**
	 * Cache key for the AnnotatedElement cache.
	 */
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

	/**
	 * Create an {@code AliasDescriptor} <em>from</em> the declaration of
	 * {@code @AliasFor} on the supplied annotation attribute and validate the
	 * configuration of {@code @AliasFor}.
	 * 
	 * @param attribute the annotation attribute that is annotated with
	 *                  {@code @AliasFor}
	 * @return an alias descriptor, or {@code null} if the attribute is not
	 *         annotated with {@code @AliasFor}
	 * @see #validateAgainst
	 */
	@SuppressWarnings("rawtypes")
	public static Map<String, Object> from(Method attribute) {
		Map descriptor = aliasDescriptorCache.get(attribute);
		if (descriptor != null) {
			return descriptor;
		}
		descriptor = new HashMap<String, Object>();
		AliasFor aliasFor = attribute.getAnnotation(AliasFor.class);
		if (aliasFor == null) {
			return null;
		}
		Class<?> declaringClass = attribute.getDeclaringClass();
		descriptor.put(ATTR_SOURCE_ATTRIBUTE, attribute.getName());
		descriptor.put(ATTR_ALIASES_ANNOTATION_TYPE,
				(Annotation.class == aliasFor.annotation() ? declaringClass : aliasFor.annotation()));
		descriptor.put(ATTR_ALIASES_ATTRIBUTE, aliasFor.attribute());
		aliasDescriptorCache.put(attribute, descriptor);
		return descriptor;
	}

	/**
	 * Find a single {@link Annotation} of {@code annotationType} on the supplied
	 * {@link AnnotatedElement}.
	 * <p>
	 * Meta-annotations will be searched if the annotation is not <em>directly
	 * present</em> on the supplied element.
	 * <p>
	 * <strong>Warning</strong>: this method operates generically on annotated
	 * elements. In other words, this method does not execute specialized search
	 * algorithms for classes or methods. If you require the more specific semantics
	 * of {@link #findAnnotation(Class, Class)} or
	 * {@link #findAnnotation(Method, Class)}, invoke one of those methods instead.
	 * 
	 * @param annotatedElement the {@code AnnotatedElement} on which to find the
	 *                         annotation
	 * @param annotationType   the annotation type to look for, both locally and as
	 *                         a meta-annotation
	 * @return the first matching annotation, or {@code null} if not found
	 * @since 4.2
	 */
	public static <A extends Annotation> A findAnnotation(AnnotatedElement annotatedElement, Class<A> annotationType) {
		// Do NOT store result in the findAnnotationCache since doing so could break
		// findAnnotation(Class, Class) and findAnnotation(Method, Class).
		A ann = findAnnotation(annotatedElement, annotationType, new HashSet<>());
		return ann;
	}

	/**
	 * Perform the search algorithm for
	 * {@link #findAnnotation(AnnotatedElement, Class)} avoiding endless recursion
	 * by tracking which annotations have already been <em>visited</em>.
	 * 
	 * @param annotatedElement the {@code AnnotatedElement} on which to find the
	 *                         annotation
	 * @param annotationType   the annotation type to look for, both locally and as
	 *                         a meta-annotation
	 * @param visited          the set of annotations that have already been visited
	 * @return the first matching annotation, or {@code null} if not found
	 * @since 4.2
	 */
	public static <A extends Annotation> A findAnnotation(AnnotatedElement annotatedElement, Class<A> annotationType,
			Set<Annotation> visited) {
		try {
			A annotation = annotatedElement.getDeclaredAnnotation(annotationType);
			if (annotation != null) {
				return annotation;
			}
			for (Annotation declaredAnn : getDeclaredAnnotations(annotatedElement)) {
				Class<? extends Annotation> declaredType = declaredAnn.annotationType();
				if (!isInJavaLangAnnotationPackage(declaredType.getName()) && visited.add(declaredAnn)) {
					annotation = findAnnotation((AnnotatedElement) declaredType, annotationType, visited);
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

	/**
	 * Find a single {@link Annotation} of {@code annotationType} on the supplied
	 * {@link Method}, traversing its super methods (i.e. from superclasses and
	 * interfaces) if the annotation is not <em>directly present</em> on the given
	 * method itself.
	 * <p>
	 * Correctly handles bridge {@link Method Methods} generated by the compiler.
	 * <p>
	 * Meta-annotations will be searched if the annotation is not <em>directly
	 * present</em> on the method.
	 * <p>
	 * Annotations on methods are not inherited by default, so we need to handle
	 * this explicitly.
	 * 
	 * @param method         the method to look for annotations on
	 * @param annotationType the annotation type to look for
	 * @return the first matching annotation, or {@code null} if not found
	 * @see #getAnnotation(Method, Class)
	 */
	public static <A extends Annotation> A findAnnotation(Method method, Class<A> annotationType) {
		if (annotationType == null) {
			return null;
		}

		AnnotationCacheKey cacheKey = new AnnotationCacheKey(method, annotationType);
		A result = (A) findAnnotationCache.get(cacheKey);

		if (result == null) {
			Method resolvedMethod = findBridgedMethod(method);
			result = findAnnotation((AnnotatedElement) resolvedMethod, annotationType);
			if (result == null) {
				result = searchOnInterfaces(method, annotationType, method.getDeclaringClass().getInterfaces());
			}

			Class<?> clazz = method.getDeclaringClass();
			while (result == null) {
				clazz = clazz.getSuperclass();
				if (clazz == null || clazz == Object.class) {
					break;
				}
				Set<Method> annotatedMethods = getAnnotatedMethodsInBaseType(clazz);
				if (!annotatedMethods.isEmpty()) {
					for (Method annotatedMethod : annotatedMethods) {
						if (isOverride(method, annotatedMethod)) {
							Method resolvedSuperMethod = findBridgedMethod(annotatedMethod);
							result = findAnnotation((AnnotatedElement) resolvedSuperMethod, annotationType);
							if (result != null) {
								break;
							}
						}
					}
				}
				if (result == null) {
					result = searchOnInterfaces(method, annotationType, clazz.getInterfaces());
				}
			}

			if (result != null) {
				findAnnotationCache.put(cacheKey, result);
			}
		}

		return result;
	}

	/**
	 * Does the given method override the given candidate method?
	 * 
	 * @param method    the overriding method
	 * @param candidate the potentially overridden method
	 * @since 5.0.8
	 */
	static boolean isOverride(Method method, Method candidate) {
		if (!candidate.getName().equals(method.getName())
				|| candidate.getParameterCount() != method.getParameterCount()) {
			return false;
		}
		Class<?>[] paramTypes = method.getParameterTypes();
		Class<?>[] validateParamTypes = candidate.getParameterTypes();
		if (Arrays.equals(candidate.getParameterTypes(), paramTypes)) {
			return true;
		}
		for (int i = 0; i < paramTypes.length; i++) {
			if (paramTypes[i] != validateParamTypes[i]) {
				return false;
			}
		}
		return true;
	}

	public static <A extends Annotation> A searchOnInterfaces(Method method, Class<A> annotationType,
			Class<?>... ifcs) {
		for (Class<?> ifc : ifcs) {
			Set<Method> annotatedMethods = getAnnotatedMethodsInBaseType(ifc);
			if (!annotatedMethods.isEmpty()) {
				for (Method annotatedMethod : annotatedMethods) {
					if (isOverride(method, annotatedMethod)) {
						A annotation = getAnnotation(annotatedMethod, annotationType);
						if (annotation != null) {
							return annotation;
						}
					}
				}
			}
		}
		return null;
	}

	/**
	 * Determine the methods on the given type with searchable annotations on them.
	 * 
	 * @param baseType the superclass or interface to search
	 * @return the cached set of annotated methods
	 * @since 5.0.5
	 */
	static Set<Method> getAnnotatedMethodsInBaseType(Class<?> baseType) {
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

	/**
	 * Determine whether the specified method has searchable annotations, i.e. not
	 * just {@code java.lang} or {@code org.springframework.lang} annotations such
	 * as {@link Deprecated} and {@link Nullable}.
	 * 
	 * @param ifcMethod the interface method to check
	 * @@since 5.0.5
	 */
	public static boolean hasSearchableAnnotations(Method ifcMethod) {
		Annotation[] anns = getDeclaredAnnotations(ifcMethod);
		if (anns.length == 0) {
			return false;
		}
		for (Annotation ann : anns) {
			String name = ann.annotationType().getName();
			if (!name.startsWith("java.lang.") && !name.startsWith("org.springframework.lang.")) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Get a single {@link Annotation} of {@code annotationType} from the supplied
	 * annotation: either the given annotation itself or a direct meta-annotation
	 * thereof.
	 * <p>
	 * Note that this method supports only a single level of meta-annotations. For
	 * support for arbitrary levels of meta-annotations, use one of the
	 * {@code find*()} methods instead.
	 * 
	 * @param annotation     the Annotation to check
	 * @param annotationType the annotation type to look for, both locally and as a
	 *                       meta-annotation
	 * @return the first matching annotation, or {@code null} if not found
	 * @since 4.0
	 */

	public static <A extends Annotation> A getAnnotation(Annotation annotation, Class<A> annotationType) {
		if (annotationType.isInstance(annotation)) {
			return (A) annotation;
		}
		Class<? extends Annotation> annotatedElement = annotation.annotationType();
		try {
			A metaAnn = annotatedElement.getAnnotation(annotationType);
			return metaAnn;
		} catch (Throwable ex) {
			return null;
		}
	}

	/**
	 * Get a single {@link Annotation} of {@code annotationType} from the supplied
	 * {@link AnnotatedElement}, where the annotation is either <em>present</em> or
	 * <em>meta-present</em> on the {@code AnnotatedElement}.
	 * <p>
	 * Note that this method supports only a single level of meta-annotations. For
	 * support for arbitrary levels of meta-annotations, use
	 * {@link #findAnnotation(AnnotatedElement, Class)} instead.
	 * 
	 * @param annotatedElement the {@code AnnotatedElement} from which to get the
	 *                         annotation
	 * @param annotationType   the annotation type to look for, both locally and as
	 *                         a meta-annotation
	 * @return the first matching annotation, or {@code null} if not found
	 * @since 3.1
	 */
	public static <A extends Annotation> A getAnnotation(AnnotatedElement annotatedElement, Class<A> annotationType) {
		try {
			A annotation = annotatedElement.getAnnotation(annotationType);
			if (annotation == null) {
				for (Annotation metaAnn : annotatedElement.getAnnotations()) {
					annotation = metaAnn.annotationType().getAnnotation(annotationType);
					if (annotation != null) {
						break;
					}
				}
			}
			return annotation;
		} catch (Throwable ex) {
			return null;
		}
	}

	/**
	 * Get a single {@link Annotation} of {@code annotationType} from the supplied
	 * {@link Method}, where the annotation is either <em>present</em> or
	 * <em>meta-present</em> on the method.
	 * <p>
	 * Correctly handles bridge {@link Method Methods} generated by the compiler.
	 * <p>
	 * Note that this method supports only a single level of meta-annotations. For
	 * support for arbitrary levels of meta-annotations, use
	 * {@link #findAnnotation(Method, Class)} instead.
	 * 
	 * @param method         the method to look for annotations on
	 * @param annotationType the annotation type to look for
	 * @return the first matching annotation, or {@code null} if not found
	 * @see org.springframework.core.BridgeMethodResolver#findBridgedMethod(Method)
	 * @see #getAnnotation(AnnotatedElement, Class)
	 */

	public static <A extends Annotation> A getAnnotation(Method method, Class<A> annotationType) {
		Method resolvedMethod = findBridgedMethod(method);
		return getAnnotation((AnnotatedElement) resolvedMethod, annotationType);
	}

	/**
	 * Get all {@link Annotation Annotations} that are <em>present</em> on the
	 * supplied {@link AnnotatedElement}.
	 * <p>
	 * Meta-annotations will <em>not</em> be searched.
	 * 
	 * @param annotatedElement the Method, Constructor or Field to retrieve
	 *                         annotations from
	 * @return the annotations found, an empty array, or {@code null} if not
	 *         resolvable (e.g. because nested Class values in annotation attributes
	 *         failed to resolve at runtime)
	 * @since 4.0.8
	 * @see AnnotatedElement#getAnnotations()
	 */
	public static Annotation[] getAnnotations(AnnotatedElement annotatedElement) {
		try {
			return annotatedElement.getAnnotations();
		} catch (Throwable ex) {
			return null;
		}
	}

	/**
	 * Get all {@link Annotation Annotations} that are <em>present</em> on the
	 * supplied {@link Method}.
	 * <p>
	 * Correctly handles bridge {@link Method Methods} generated by the compiler.
	 * <p>
	 * Meta-annotations will <em>not</em> be searched.
	 * 
	 * @param method the Method to retrieve annotations from
	 * @return the annotations found, an empty array, or {@code null} if not
	 *         resolvable (e.g. because nested Class values in annotation attributes
	 *         failed to resolve at runtime)
	 * @see org.springframework.core.BridgeMethodResolver#findBridgedMethod(Method)
	 * @see AnnotatedElement#getAnnotations()
	 */
	public static Annotation[] getAnnotations(Method method) {
		try {
			return findBridgedMethod(method).getAnnotations();
		} catch (Throwable ex) {
			return null;
		}
	}

	/**
	 * Find the original method for the supplied {@link Method bridge Method}.
	 * <p>
	 * It is safe to call this method passing in a non-bridge {@link Method}
	 * instance. In such a case, the supplied {@link Method} instance is returned
	 * directly to the caller. Callers are <strong>not</strong> required to check
	 * for bridging before calling this method.
	 * 
	 * @param bridgeMethod the method to introspect
	 * @return the original method (either the bridged method or the passed-in
	 *         method if no more specific one could be found)
	 */
	public static Method findBridgedMethod(Method bridgeMethod) {
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

	/**
	 * Check the given metadata for a full configuration class candidate (i.e. a
	 * class annotated with {@code @Configuration}).
	 * 
	 * @param metadata the metadata of the annotated class
	 * @return {@code true} if the given class is to be processed as a full
	 *         configuration class, including cross-method call interception
	 */
	public static boolean isFullConfigurationCandidate(AnnotationMetaData metadata) {
		return metadata.isAnnotated(Configuration.class.getName());
	}

	/**
	 * Retrieve the given annotation's attributes as an {@link AnnotationAttributes}
	 * map.
	 * <p>
	 * This method provides fully recursive annotation reading capabilities on par
	 * with the reflection-based
	 * {@link org.springframework.core.type.StandardAnnotationMetadata}.
	 * <p>
	 * <strong>NOTE</strong>: This variant of {@code getAnnotationAttributes()} is
	 * only intended for use within the framework. The following special rules
	 * apply:
	 * <ol>
	 * <li>Default values will be replaced with default value placeholders.</li>
	 * <li>The resulting, merged annotation attributes should eventually be
	 * {@linkplain #postProcessAnnotationAttributes post-processed} in order to
	 * ensure that placeholders have been replaced by actual default values and in
	 * order to enforce {@code @AliasFor} semantics.</li>
	 * </ol>
	 * 
	 * @param annotatedElement       the element that is annotated with the supplied
	 *                               annotation; may be {@code null} if unknown
	 * @param annotation             the annotation to retrieve the attributes for
	 * @param classValuesAsString    whether to convert Class references into
	 *                               Strings (for compatibility with
	 *                               {@link org.springframework.core.type.AnnotationMetadata})
	 *                               or to preserve them as Class references
	 * @param nestedAnnotationsAsMap whether to convert nested annotations into
	 *                               {@link AnnotationAttributes} maps (for
	 *                               compatibility with
	 *                               {@link org.springframework.core.type.AnnotationMetadata})
	 *                               or to preserve them as {@code Annotation}
	 *                               instances
	 * @return the annotation attributes (a specialized Map) with attribute names as
	 *         keys and corresponding attribute values as values (never
	 *         {@code null})
	 * @since 4.2
	 * @see #postProcessAnnotationAttributes
	 */
	public static Map<String, Object> retrieveAnnotationAttributes(Object annotatedElement, Annotation annotation,
			boolean classValuesAsString, boolean nestedAnnotationsAsMap) {

		Class<? extends Annotation> annotationType = annotation.annotationType();
		Map<String, Object> attributes = new HashMap<>();
		attributes.put(Constant.ATTR_ANNOTATION_TYPE, annotationType);

		for (Method method : getAttributeMethods(annotationType)) {
			try {
				Object attributeValue = method.invoke(annotation);
				Object defaultValue = method.getDefaultValue();
				if (attributeValue == null && defaultValue != null) {
					attributeValue = defaultValue;
				}
				attributes.put(method.getName(),
						adaptValue(annotatedElement, attributeValue, classValuesAsString, nestedAnnotationsAsMap));
			} catch (Throwable ex) {
				LOG.info("Could not obtain annotation attribute value for " + method, ex);
			}
		}

		return attributes;
	}

	/**
	 * Adapt the given value according to the given class and nested annotation
	 * settings.
	 * <p>
	 * Nested annotations will be
	 * {@linkplain #synthesizeAnnotation(Annotation, AnnotatedElement) synthesized}.
	 * 
	 * @param annotatedElement       the element that is annotated, used for
	 *                               contextual logging; may be {@code null} if
	 *                               unknown
	 * @param value                  the annotation attribute value
	 * @param classValuesAsString    whether to convert Class references into
	 *                               Strings (for compatibility with
	 *                               {@link org.springframework.core.type.AnnotationMetadata})
	 *                               or to preserve them as Class references
	 * @param nestedAnnotationsAsMap whether to convert nested annotations into
	 *                               {@link AnnotationAttributes} maps (for
	 *                               compatibility with
	 *                               {@link org.springframework.core.type.AnnotationMetadata})
	 *                               or to preserve them as {@code Annotation}
	 *                               instances
	 * @return the adapted value, or the original value if no adaptation is needed
	 */

	public static Object adaptValue(Object annotatedElement, Object value, boolean classValuesAsString,
			boolean nestedAnnotationsAsMap) {

		if (classValuesAsString) {
			if (value instanceof Class) {
				return ((Class<?>) value).getName();
			} else if (value instanceof Class[]) {
				Class<?>[] clazzArray = (Class<?>[]) value;
				String[] classNames = new String[clazzArray.length];
				for (int i = 0; i < clazzArray.length; i++) {
					classNames[i] = clazzArray[i].getName();
				}
				return classNames;
			}
		}

		if (value instanceof Annotation) {
			Annotation annotation = (Annotation) value;
			if (nestedAnnotationsAsMap) {
				return retrieveAnnotationAttributes(annotatedElement, annotation, classValuesAsString, true);
			} else {
				return value;
			}
		}

		if (value instanceof Annotation[]) {
			Annotation[] annotations = (Annotation[]) value;
			if (nestedAnnotationsAsMap) {
				Object[] mappedAnnotations = new Object[annotations.length];
				for (int i = 0; i < annotations.length; i++) {
					mappedAnnotations[i] = retrieveAnnotationAttributes(annotatedElement, annotations[i],
							classValuesAsString, true);
				}
				return mappedAnnotations;
			} else {
				return annotations;
			}
		}
		return value;
	}

	/**
	 * Get all methods declared in the supplied {@code annotationType} that match
	 * Java's requirements for annotation <em>attributes</em>.
	 * <p>
	 * All methods in the returned list will be
	 * {@linkplain ReflectionUtils#makeAccessible(Method) made accessible}.
	 * 
	 * @param annotationType the type in which to search for attribute methods
	 *                       (never {@code null})
	 * @return all annotation attribute methods in the specified annotation type
	 *         (never {@code null} , though potentially <em>empty</em>)
	 * @since 4.2
	 */
	static List<Method> getAttributeMethods(Class<? extends Annotation> annotationType) {
		List<Method> methods = attributeMethodsCache.get(annotationType);
		if (methods != null) {
			return methods;
		}
		methods = new ArrayList<>();
		for (Method method : annotationType.getDeclaredMethods()) {
			method.setAccessible(true);
			methods.add(method);
		}
		attributeMethodsCache.put(annotationType, methods);
		return methods;
	}

	public static boolean isLazy(DependencyDescriptor descriptor) {
		for (Annotation ann : descriptor.getAnnotations()) {
			Lazy lazy = AnnotationUtils.getAnnotation(ann, Lazy.class);
			if (lazy != null && lazy.value()) {
				return true;
			}
		}
		Parameter methodParam = descriptor.getMethodParameter();
		if (methodParam != null) {
			Executable exec = methodParam.getDeclaringExecutable();
			if (exec instanceof Method) {
				Method method = (Method) exec;
				if (method == null || void.class == method.getReturnType()) {
					Lazy lazy = AnnotationUtils.getAnnotation(methodParam.getDeclaringExecutable(), Lazy.class);
					if (lazy != null && lazy.value()) {
						return true;
					}
				}
			} else if (exec instanceof Constructor) {

			}

		}
		return false;
	}

	/**
	 * Determine a suggested value from any of the given candidate annotations.
	 */
	public static Object findValue(Annotation[] annotationsToSearch) {
		if (annotationsToSearch.length > 0) { // qualifier annotations have to be local
			Map<String, Object> attr = searchWithGetSemantics(forAnnotations(annotationsToSearch), Value.class, null);
			if (attr != null) {
				return attr.get(Constant.ATTR_VALUE);
			}
		}
		return null;
	}

	/**
	 * Search for annotations of the specified {@code annotationName} or
	 * {@code annotationType} on the specified {@code element}, following <em>get
	 * semantics</em>.
	 * 
	 * @param element        the annotated element
	 * @param annotationType the annotation type to find
	 * @param annotationName the fully qualified class name of the annotation type
	 *                       to find (as an alternative to {@code annotationType})
	 * @param processor      the processor to delegate to
	 * @return the result of the processor (potentially {@code null})
	 */

	public static Map<String, Object> searchWithGetSemantics(AnnotatedElement element,
			Class<? extends Annotation> annotationType, String annotationName) {
		return searchWithGetSemantics(element,
				(annotationType != null ? Collections.singleton(annotationType) : Collections.emptySet()),
				annotationName, null);
	}

	/**
	 * Search for annotations of the specified {@code annotationName} or
	 * {@code annotationType} on the specified {@code element}, following <em>get
	 * semantics</em>.
	 * 
	 * @param element         the annotated element
	 * @param annotationTypes the annotation types to find
	 * @param annotationName  the fully qualified class name of the annotation type
	 *                        to find (as an alternative to {@code annotationType})
	 * @param containerType   the type of the container that holds repeatable
	 *                        annotations, or {@code null} if the annotation is not
	 *                        repeatable
	 * @param processor       the processor to delegate to
	 * @return the result of the processor (potentially {@code null})
	 * @since 4.3
	 */

	public static Map<String, Object> searchWithGetSemantics(AnnotatedElement element,
			Set<Class<? extends Annotation>> annotationTypes, String annotationName,
			Class<? extends Annotation> containerType) {

		try {
			return searchWithGetSemantics(element, annotationTypes, annotationName, containerType, new HashSet<>(), 0);
		} catch (Throwable ex) {
			throw new IllegalStateException("Failed to introspect annotations on " + element, ex);
		}
	}

	/**
	 * Perform the search algorithm for the {@link #searchWithGetSemantics} method,
	 * avoiding endless recursion by tracking which annotated elements have already
	 * been <em>visited</em>.
	 * <p>
	 * The {@code metaDepth} parameter is explained in the {@link Processor#process
	 * process()} method of the {@link Processor} API.
	 * 
	 * @param element         the annotated element
	 * @param annotationTypes the annotation types to find
	 * @param annotationName  the fully qualified class name of the annotation type
	 *                        to find (as an alternative to {@code annotationType})
	 * @param containerType   the type of the container that holds repeatable
	 *                        annotations, or {@code null} if the annotation is not
	 *                        repeatable
	 * @param processor       the processor to delegate to
	 * @param visited         the set of annotated elements that have already been
	 *                        visited
	 * @param metaDepth       the meta-depth of the annotation
	 * @return the result of the processor (potentially {@code null})
	 */

	public static Map<String, Object> searchWithGetSemantics(AnnotatedElement element,
			Set<Class<? extends Annotation>> annotationTypes, String annotationName,
			Class<? extends Annotation> containerType, Set<AnnotatedElement> visited, int metaDepth) {

		if (visited.add(element)) {
			try {
				// Start searching within locally declared annotations
				List<Annotation> declaredAnnotations = Arrays.asList(AnnotationUtils.getDeclaredAnnotations(element));
				Map<String, Object> result = searchWithGetSemanticsInAnnotations(element, declaredAnnotations,
						annotationTypes, annotationName, containerType, visited, metaDepth);
				if (result != null) {
					return result;
				}
				if (element instanceof Class) { // otherwise getAnnotations doesn't return anything new
					Class<?> superclass = ((Class<?>) element).getSuperclass();
					if (superclass != null && superclass != Object.class) {
						List<Annotation> inheritedAnnotations = new LinkedList<>();
						for (Annotation annotation : element.getAnnotations()) {
							if (!declaredAnnotations.contains(annotation)) {
								inheritedAnnotations.add(annotation);
							}
						}
						// Continue searching within inherited annotations
						result = searchWithGetSemanticsInAnnotations(element, inheritedAnnotations, annotationTypes,
								annotationName, containerType, visited, metaDepth);
						if (result != null) {
							return result;
						}
					}
				}
			} catch (Throwable ex) {
				LOG.info(ex);
			}
		}

		return null;
	}

	/**
	 * This method is invoked by {@link #searchWithGetSemantics} to perform the
	 * actual search within the supplied list of annotations.
	 * <p>
	 * This method should be invoked first with locally declared annotations and
	 * then subsequently with inherited annotations, thereby allowing local
	 * annotations to take precedence over inherited annotations.
	 * <p>
	 * The {@code metaDepth} parameter is explained in the {@link Processor#process
	 * process()} method of the {@link Processor} API.
	 * 
	 * @param element         the element that is annotated with the supplied
	 *                        annotations, used for contextual logging; may be
	 *                        {@code null} if unknown
	 * @param annotations     the annotations to search in
	 * @param annotationTypes the annotation types to find
	 * @param annotationName  the fully qualified class name of the annotation type
	 *                        to find (as an alternative to {@code annotationType})
	 * @param containerType   the type of the container that holds repeatable
	 *                        annotations, or {@code null} if the annotation is not
	 *                        repeatable
	 * @param processor       the processor to delegate to
	 * @param visited         the set of annotated elements that have already been
	 *                        visited
	 * @param metaDepth       the meta-depth of the annotation
	 * @return the result of the processor (potentially {@code null})
	 * @since 4.2
	 */

	public static Map<String, Object> searchWithGetSemanticsInAnnotations(AnnotatedElement element,
			List<Annotation> annotations, Set<Class<? extends Annotation>> annotationTypes, String annotationName,
			Class<? extends Annotation> containerType, Set<AnnotatedElement> visited, int metaDepth) {
		Map<String, Object> result = null;
		// Search in annotations
		for (Annotation annotation : annotations) {
			Class<? extends Annotation> currentAnnotationType = annotation.annotationType();
			if (!isInJavaLangAnnotationPackage(currentAnnotationType.getCanonicalName())) {
				if (annotationTypes.contains(currentAnnotationType)
						|| currentAnnotationType.getName().equals(annotationName)) {
					result = retrieveAnnotationAttributes(element, annotation, false, false);
					if (result != null) {
						return result;
					}
				}
				// Repeatable annotations in container?
				else if (currentAnnotationType == containerType) {
					for (Annotation contained : getRawAnnotationsFromContainer(element, annotation)) {
						Map<String, Object> containedResult = retrieveAnnotationAttributes(element, contained, false,
								false);
						if (containedResult != null) {
							if (result != null) {
								result.putAll(containedResult);
							} else {
								result = containedResult;
							}
						}
					}
				}
			}
		}
		// Recursively search in meta-annotations
		for (Annotation annotation : annotations) {
			Class<? extends Annotation> currentAnnotationType = annotation.annotationType();
			if (!StringUtils.isEmpty(hasPlainJavaAnnotationsOnly(currentAnnotationType))) {
				Map<String, Object> recursiveResult = searchWithGetSemantics(currentAnnotationType, annotationTypes,
						annotationName, containerType, visited, metaDepth + 1);
				if (recursiveResult != null) {
					result.putAll(recursiveResult);
				}
			}
		}
		return result;
	}

	/**
	 * Build an adapted {@link AnnotatedElement} for the given annotations,
	 * typically for use with other methods on {@link AnnotatedElementUtils}.
	 * 
	 * @param annotations the annotations to expose through the
	 *                    {@code AnnotatedElement}
	 * @since 4.3
	 */
	public static AnnotatedElement forAnnotations(final Annotation... annotations) {
		return new AnnotatedElement() {
			@Override
			public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
				for (Annotation ann : annotations) {
					if (ann.annotationType() == annotationClass) {
						return (T) ann;
					}
				}
				return null;
			}

			@Override
			public Annotation[] getAnnotations() {
				return annotations;
			}

			@Override
			public Annotation[] getDeclaredAnnotations() {
				return annotations;
			}
		};
	}

	/**
	 * Determine if the given annotated element is defined in a {@code java} or in
	 * the {@code org.springframework.lang} package.
	 * 
	 * @param annotatedElement the annotated element to check
	 * @return {@code true} if the given element is in a {@code java} package or in
	 *         the {@code org.springframework.lang} package
	 * @since 5.1
	 */
	public static String hasPlainJavaAnnotationsOnly(Object annotatedElement) {
		Class<?> clazz;
		if (annotatedElement instanceof Class) {
			clazz = (Class<?>) annotatedElement;
		} else if (annotatedElement instanceof Member) {
			clazz = ((Member) annotatedElement).getDeclaringClass();
		} else {
			return null;
		}
		return clazz.getName();
	}

	/**
	 * Get the array of raw (unsynthesized) annotations from the {@code value}
	 * attribute of the supplied repeatable annotation {@code container}.
	 * 
	 * @since 4.3
	 */
	public static <A extends Annotation> A[] getRawAnnotationsFromContainer(AnnotatedElement element,
			Annotation container) {

		try {
			A[] value = (A[]) AnnotationUtils.getValue(container);
			if (value != null) {
				return value;
			}
		} catch (Throwable ex) {
			LOG.info(element, ex);
		}
		// Unable to read value from repeating annotation container -> ignore it.
		return (A[]) new Annotation[0];
	}

}
