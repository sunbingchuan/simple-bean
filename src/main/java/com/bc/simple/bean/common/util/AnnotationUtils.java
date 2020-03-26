package com.bc.simple.bean.common.util;

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
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.bc.simple.bean.BeanDefinition;
import com.bc.simple.bean.BeanFactory;
import com.bc.simple.bean.common.annotation.AliasFor;
import com.bc.simple.bean.common.annotation.Configuration;
import com.bc.simple.bean.common.annotation.DependsOn;
import com.bc.simple.bean.common.annotation.Lazy;
import com.bc.simple.bean.common.annotation.Order;
import com.bc.simple.bean.common.annotation.Primary;
import com.bc.simple.bean.common.annotation.Scope;
import com.bc.simple.bean.common.annotation.Value;
import com.bc.simple.bean.common.config.ConfigLoader.Node;
import com.bc.simple.bean.core.processor.AutowiredAnnotationProcessor;
import com.bc.simple.bean.core.processor.ConfigurationClassProcessor;
import com.bc.simple.bean.core.support.AnnotationMetaData;
import com.bc.simple.bean.core.support.DependencyDescriptor;

@SuppressWarnings("unchecked")
public class AnnotationUtils {

	private static final Log LOG = LogFactory.getLog(AnnotationUtils.class);

	/** Cache for @Order value (or NOT_ANNOTATED marker) per Class. */
	private static final Map<Class<?>, Object> orderCache = new ConcurrentHashMap<>(64);

	/** Cache for @Priority value (or NOT_ANNOTATED marker) per Class. */
	private static final Map<Class<?>, Object> priorityCache = new ConcurrentHashMap<>();

	private static final Map<AnnotationCacheKey, Annotation> findAnnotationCache = new ConcurrentHashMap<>(256);


	public static final String VALUE = "value";

	private static final Map<AnnotatedElement, Annotation[]> declaredAnnotationsCache = new ConcurrentHashMap<>(256);

	private static final Map<Class<?>, Set<Method>> annotatedBaseTypeCache = new ConcurrentHashMap<>(256);

	private static final Map<Class<? extends Annotation>, List<Method>> attributeMethodsCache =
			new ConcurrentHashMap<>(256);

	private static final Map<Method, Map<Class<?>, Set<String>>> aliasDescriptorCache = new ConcurrentHashMap<>(256);

	/** Cache marker for a non-annotated Class. */
	private static final Object NOT_ANNOTATED = new Object();

	private static Class<? extends Annotation> priorityAnnotationType;

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

	public static boolean hasMetadata(AnnotationMetaData metadata, List<String> filters) {
		for (String filter : filters) {
			if (filter.equals(metadata.getClassName())) {
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
		if (metadata.isAnnotated(Order.class.getName())) {
			Map<String, Object> attrs = metadata.getAttributes(Order.class.getCanonicalName());
			Integer order = StringUtils.switchInteger(attrs.get(Constant.ATTR_VALUE));
			if (order != null) {
				abd.setBeanOrder(order);
			}
		}
		Map<String, Object> dependsOn = attributesFor(metadata, DependsOn.class);
		if (dependsOn != null) {
			abd.setDependsOn(StringUtils.splitByStr(StringUtils.switchString(dependsOn.get(Constant.ATTR_VALUE)),
					StringUtils.COMMA));
		}

	}

	private static Map<String, Object> attributesFor(AnnotationMetaData metadata, Class<?> annotationClass) {
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


	public static Object getValue(Annotation annotation) {
		return getValue(annotation, VALUE);
	}


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
			LOG.info("Could not obtain value for annotation attribute '" + attributeName + "' in " + annotation, ex);
		} catch (Throwable ex) {
		}
		return null;
	}


	public static <A extends Annotation> A findAnnotation(Class<?> clazz, Class<A> annotationType) {
		return findAnnotation(clazz, annotationType, true);
	}

	private static Map<AnnotatedElement, Map<Class<?>, Map<String, Object>>> parseAnnotationCache =
			new ConcurrentHashMap<>(256);

	public static Map<Class<?>, Map<String, Object>> parseAnnotation(AnnotatedElement annotatedElement) {
		Map<Class<?>, Map<String, Object>> attrs = parseAnnotationCache.get(annotatedElement);
		if (attrs == null) {
			attrs = new HashMap<Class<?>, Map<String, Object>>();
			parseAnnotation(annotatedElement, attrs);
			parseAnnotationCache.put(annotatedElement, attrs);
		}
		return attrs;
	}

	private static void parseAnnotation(AnnotatedElement annotatedElement, Map<Class<?>, Map<String, Object>> attrs) {
		for (Annotation anno : getDeclaredAnnotations(annotatedElement)) {
			if (!isInJavaLangAnnotationPackage(anno.annotationType().getCanonicalName())) {
				parseAnnotation(anno, attrs);
			}
		}
	}

	private static void parseAnnotation(Annotation anno, Map<Class<?>, Map<String, Object>> attrs) {
		Class<?> clazz = anno.annotationType();
		parseAnnotationMethod(anno, attrs);
		parseAnnotation(clazz, attrs);
		for (Class<?> ifc : clazz.getInterfaces()) {
			parseAnnotation(ifc, attrs);
		}
		Class<?> superclass = clazz.getSuperclass();
		if (superclass != null) {
			parseAnnotation(superclass, attrs);
		}
	}

	private static void parseAnnotationMethod(Annotation anno, Map<Class<?>, Map<String, Object>> attrs) {
		Class<?> clazz = anno.annotationType();
		Map<String, Object> attr = attrs.get(clazz);
		if (attr == null) {
			attr = new HashMap<String, Object>();
			attrs.put(clazz, attr);
		}
		for (Method method : clazz.getDeclaredMethods()) {
			try {
				method.setAccessible(true);
				Object value = method.invoke(anno);
				if (value == null || (StringUtils.isEmpty(value.toString()))) {
					continue;
				}
				Map<Class<? extends Annotation>, Set<String>> alias = from(method);
				for (Entry<Class<? extends Annotation>, Set<String>> entry : alias.entrySet()) {
					Class<?> annotationClass = entry.getKey();
					Set<String> attributes = entry.getValue();
					Map<String, Object> aliasAttr = attrs.get(annotationClass);
					if (aliasAttr == null) {
						aliasAttr = new HashMap<String, Object>();
						attrs.put(annotationClass, aliasAttr);
					}
					for (String attribute : attributes) {
						aliasAttr.put(attribute, value);
					}
				}
			} catch (Exception e) {
				// ignore
			}
		}
	}


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

	public static Map<Class<? extends Annotation>, Set<String>> from(String annotationType, String attribute) {
		return from(BeanUtils.forName(annotationType), attribute);
	}

	public static Map<Class<? extends Annotation>, Set<String>> from(Class<?> annotationType, String attribute) {
		Method attrMethod;
		try {
			attrMethod = annotationType.getDeclaredMethod(attribute);
		} catch (Exception e) {
			return null;
		}
		return from(attrMethod);
	}



	@SuppressWarnings("rawtypes")
	public static Map<Class<? extends Annotation>, Set<String>> from(Method attribute) {
		Map descriptor = aliasDescriptorCache.get(attribute);
		if (descriptor != null) {
			return descriptor;
		}
		descriptor = new HashMap<Class<? extends Annotation>, Set<String>>();
		from(attribute, descriptor);
		aliasDescriptorCache.put(attribute, descriptor);
		return descriptor;
	}

	private static void from(Method attribute, Map<Class<?>, Set<String>> descriptor) {
		Set<String> set = descriptor.get(attribute.getDeclaringClass());
		if (set == null) {
			set = new HashSet<String>();
			descriptor.put(attribute.getDeclaringClass(), set);
		}
		if (!set.add(attribute.getName())) {
			return;
		}
		AliasFor aliasFor = attribute.getAnnotation(AliasFor.class);
		if (aliasFor == null) {
			return;
		}
		Class<?> declaringClass = attribute.getDeclaringClass();
		Class<?> aliasForType = Annotation.class == aliasFor.annotation() ? declaringClass : aliasFor.annotation();
		String aliasForValue = aliasFor.attribute();
		if (StringUtils.isEmpty(aliasForValue)) {
			aliasForValue = aliasFor.value();
		}
		if (StringUtils.isEmpty(aliasForValue)) {
			aliasForValue = attribute.getName();
		}
		try {
			Method method = aliasForType.getDeclaredMethod(aliasForValue);
			from(method, descriptor);
		} catch (Exception e) {
			return;
		}
	}


	public static <A extends Annotation> A findAnnotation(AnnotatedElement annotatedElement, Class<A> annotationType) {
		// Do NOT store result in the findAnnotationCache since doing so could break
		// findAnnotation(Class, Class) and findAnnotation(Method, Class).
		A ann = findAnnotation(annotatedElement, annotationType, new HashSet<>());
		return ann;
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


	public static <A extends Annotation> A findAnnotation(Method method, Class<A> annotationType) {
		if (annotationType == null) {
			return null;
		}

		AnnotationCacheKey cacheKey = new AnnotationCacheKey(method, annotationType);
		A result = (A) findAnnotationCache.get(cacheKey);

		if (result == null) {
			Method resolvedMethod = findOriginalMethod(method);
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
				result = findOverrideAnnotatedMethod(method, annotationType, clazz);
				if (result != null) {
					break;
				} else {
					result = searchOnInterfaces(method, annotationType, clazz.getInterfaces());
				}
			}

			if (result != null) {
				findAnnotationCache.put(cacheKey, result);
			}
		}

		return result;
	}


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
			A annotation = findOverrideAnnotatedMethod(method, annotationType, ifc);
			if (annotation != null) {
				return annotation;
			}
		}
		return null;
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
	 * Get a single {@link Annotation} of {@code annotationType} from the supplied annotation:
	 * either the given annotation itself or a direct meta-annotation thereof.
	 * <p>
	 * Note that this method supports only a single level of meta-annotations. For support for
	 * arbitrary levels of meta-annotations, use one of the {@code find*()} methods instead.
	 * 
	 * @param annotation the Annotation to check
	 * @param annotationType the annotation type to look for, both locally and as a meta-annotation
	 * @return the first matching annotation, or {@code null} if not found
	 * @since 4.0
	 */

	public static <A extends Annotation> A getAnnotation(Annotation annotation, Class<A> annotationType) {
		if (annotationType.isInstance(annotation)) {
			return (A) annotation;
		}
		Class<? extends Annotation> annotatedElementType = annotation.annotationType();
		try {
			A metaAnn = annotatedElementType.getAnnotation(annotationType);
			return metaAnn;
		} catch (Throwable ex) {
			return null;
		}
	}


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



	public static <A extends Annotation> A getAnnotation(Method method, Class<A> annotationType) {
		Method resolvedMethod = findOriginalMethod(method);
		return getAnnotation((AnnotatedElement) resolvedMethod, annotationType);
	}


	public static Annotation[] getAnnotations(AnnotatedElement annotatedElement) {
		try {
			return annotatedElement.getAnnotations();
		} catch (Throwable ex) {
			return null;
		}
	}


	public static Annotation[] getAnnotations(Method method) {
		try {
			return findOriginalMethod(method).getAnnotations();
		} catch (Throwable ex) {
			return null;
		}
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


	public static boolean isFullConfigurationCandidate(AnnotationMetaData metadata) {
		return metadata.isAnnotated(Configuration.class.getName());
	}


	public static Map<String, Object> retrieveAnnotationAttributes(Object annotatedElement, Annotation annotation,
			boolean classValuesAsString, boolean nestedAnnotationsAsMap) {
		if (annotation == null) {
			return null;
		}
		Class<? extends Annotation> annotationType = annotation.annotationType();
		Map<String, Object> attributes = new HashMap<>(8);
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



	private static Object adaptValue(Object annotatedElement, Object value, boolean classValuesAsString,
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
					mappedAnnotations[i] =
							retrieveAnnotationAttributes(annotatedElement, annotations[i], classValuesAsString, true);
				}
				return mappedAnnotations;
			} else {
				return annotations;
			}
		}
		return value;
	}


	private static List<Method> getAttributeMethods(Class<? extends Annotation> annotationType) {
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


	public static Object findValue(Annotation[] annotationsToSearch) {
		if (annotationsToSearch.length > 0) { // qualifier annotations have to be local
			AnnotatedElement element = forAnnotations(annotationsToSearch);
			Map<String, Object> attr =
					retrieveAnnotationAttributes(element, element.getAnnotation(Value.class), false, true);
			if (attr != null) {
				return attr.get(Constant.ATTR_VALUE);
			}
		}
		return null;
	}


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



}
