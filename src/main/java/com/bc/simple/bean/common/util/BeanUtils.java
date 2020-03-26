package com.bc.simple.bean.common.util;

import java.io.Closeable;
import java.io.Externalizable;
import java.io.IOException;
import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.URL;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.bc.simple.bean.ApplicationContext;
import com.bc.simple.bean.BeanDefinition;
import com.bc.simple.bean.BeanFactory;
import com.bc.simple.bean.common.annotation.Component;
import com.bc.simple.bean.common.annotation.Qualifier;
import com.bc.simple.bean.core.asm.ClassReader;
import com.bc.simple.bean.core.asm.ClassVisitor;
import com.bc.simple.bean.core.asm.Label;
import com.bc.simple.bean.core.asm.MethodVisitor;
import com.bc.simple.bean.core.asm.Opcodes;
import com.bc.simple.bean.core.asm.SpringAsmInfo;
import com.bc.simple.bean.core.support.SimpleException;

public class BeanUtils {

	public static final String CLOSE_METHOD_NAME = "close";

	public static final String SHUTDOWN_METHOD_NAME = "shutdown";


	public static final String CGLIB_RENAMED_METHOD_PREFIX = "CGLIB$";

	public static final Method[] NO_METHODS = {};

	public static final Field[] NO_FIELDS = {};

	public static final String FACTORY_BEAN_PREFIX = "&";

	/** Suffix for array class names: {@code "[]"}. */
	public static final String ARRAY_SUFFIX = "[]";

	/** Prefix for internal array class names: {@code "["}. */
	private static final String INTERNAL_ARRAY_PREFIX = "[";

	/** Prefix for internal non-primitive array class names: {@code "[L"}. */
	private static final String NON_PRIMITIVE_ARRAY_PREFIX = "[L";

	/** The package separator character: {@code '.'}. */
	private static final char PACKAGE_SEPARATOR = '.';

	/** The path separator character: {@code '/'}. */
	private static final char PATH_SEPARATOR = '/';

	/** The inner class separator character: {@code '$'}. */
	private static final char INNER_CLASS_SEPARATOR = '$';

	/** The PROXY class separator: {@code "$$"}. */
	public static final String PROXY_CLASS_SEPARATOR = "$$";

	/** The ".class" file suffix. */
	public static final String CLASS_FILE_SUFFIX = ".class";

	public static final String GENERATED_BEAN_NAME_SEPARATOR = "#";

	private static final Map<Class<?>, Field[]> declaredFieldsCache = new ConcurrentHashMap<>(256);


	private static final Map<Class<?>, Class<?>> primitiveWrapperTypeMap = new IdentityHashMap<>(8);


	private static final Map<Class<?>, Class<?>> primitiveTypeToWrapperMap = new IdentityHashMap<>(8);


	private static final Map<String, Class<?>> primitiveTypeNameMap = new HashMap<>(32);


	private static final Map<String, Class<?>> commonClassCache = new HashMap<>(64);


	private static final Set<Class<?>> javaLanguageInterfaces;

	private static final Set<Class<? extends Annotation>> qualifierTypes = new LinkedHashSet<>(2);

	private static final Map<Class<?>, Map<Member, String[]>> parameterNamesCache = new ConcurrentHashMap<>(32);

	static {
		primitiveWrapperTypeMap.put(Boolean.class, boolean.class);
		primitiveWrapperTypeMap.put(Byte.class, byte.class);
		primitiveWrapperTypeMap.put(Character.class, char.class);
		primitiveWrapperTypeMap.put(Double.class, double.class);
		primitiveWrapperTypeMap.put(Float.class, float.class);
		primitiveWrapperTypeMap.put(Integer.class, int.class);
		primitiveWrapperTypeMap.put(Long.class, long.class);
		primitiveWrapperTypeMap.put(Short.class, short.class);

		// Map entry iteration is less expensive to initialize than forEach with lambdas
		for (Map.Entry<Class<?>, Class<?>> entry : primitiveWrapperTypeMap.entrySet()) {
			primitiveTypeToWrapperMap.put(entry.getValue(), entry.getKey());
			registerCommonClasses(entry.getKey());
		}

		Set<Class<?>> primitiveTypes = new HashSet<>(32);
		primitiveTypes.addAll(primitiveWrapperTypeMap.values());
		Collections.addAll(primitiveTypes, boolean[].class, byte[].class, char[].class, double[].class, float[].class,
				int[].class, long[].class, short[].class);
		primitiveTypes.add(void.class);
		for (Class<?> primitiveType : primitiveTypes) {
			primitiveTypeNameMap.put(primitiveType.getName(), primitiveType);
		}

		registerCommonClasses(Boolean[].class, Byte[].class, Character[].class, Double[].class, Float[].class,
				Integer[].class, Long[].class, Short[].class);
		registerCommonClasses(Number.class, Number[].class, String.class, String[].class, Class.class, Class[].class,
				Object.class, Object[].class);
		registerCommonClasses(Throwable.class, Exception.class, RuntimeException.class, Error.class,
				StackTraceElement.class, StackTraceElement[].class);
		registerCommonClasses(Enum.class, Iterable.class, Iterator.class, Enumeration.class, Collection.class,
				List.class, Set.class, Map.class, Map.Entry.class, Optional.class);

		Class<?>[] javaLanguageInterfaceArray = {Serializable.class, Externalizable.class, Closeable.class,
				AutoCloseable.class, Cloneable.class, Comparable.class};
		registerCommonClasses(javaLanguageInterfaceArray);
		javaLanguageInterfaces = new HashSet<>(Arrays.asList(javaLanguageInterfaceArray));

		qualifierTypes.add(Qualifier.class);
	}


	private static void registerCommonClasses(Class<?>... commonClasses) {
		for (Class<?> clazz : commonClasses) {
			commonClassCache.put(clazz.getName(), clazz);
		}
	}


	public static <T> T instantiateClass(Class<T> clazz) {
		if (clazz.isInterface()) {
			throw new SimpleException("Specified class is an interface");
		}
		try {
			return instantiateClass(clazz.getDeclaredConstructor());
		} catch (NoSuchMethodException ex) {

			throw new SimpleException("No default constructor found", ex);
		} catch (LinkageError err) {
			throw new SimpleException("Unresolvable class definition", err);
		}
	}


	@SuppressWarnings("unchecked")
	public static <T> T instantiateClass(Class<?> clazz, Class<T> assignableTo) {
		return (T) instantiateClass(clazz);
	}


	public static <T> T instantiateClass(Constructor<T> ctor, Object... args) {
		try {
			ctor.setAccessible(true);
			return ctor.newInstance(args);
		} catch (InstantiationException ex) {
			throw new SimpleException("Is it an abstract class?", ex);
		} catch (IllegalAccessException ex) {
			throw new SimpleException("Is the constructor accessible?", ex);
		} catch (IllegalArgumentException ex) {
			throw new SimpleException("Illegal arguments for constructor", ex);
		} catch (Exception ex) {
			throw new SimpleException("Constructor threw exception", ex);
		}
	}


	public static BeanDefinition createBeanDefinition(String className, ClassLoader classLoader)
			throws ClassNotFoundException {

		BeanDefinition bd = new BeanDefinition();
		if (className != null) {
			bd.setBeanClassName(className);
			if (classLoader != null) {
				bd.setBeanClass(forName(className, classLoader));
			}
		}
		return bd;
	}

	public static Class<?> forName(String name) {
		return forName(name, ApplicationContext.class.getClassLoader());
	}


	public static Class<?> forName(String name, ClassLoader classLoader) {
		if (classLoader == null) {
			classLoader = ApplicationContext.class.getClassLoader();
		}
		Class<?> clazz = null;
		// Most class names will be quite long, considering that they
		// SHOULD sit in a package, so a length check is worthwhile.
		if (name != null && name.length() <= 8) {
			// Could be a primitive - likely.
			clazz = primitiveTypeNameMap.get(name);
		}
		if (clazz == null) {
			clazz = commonClassCache.get(name);
		}
		if (clazz != null) {
			return clazz;
		}

		// "java.lang.String[]" style arrays
		if (name.endsWith(ARRAY_SUFFIX)) {
			String elementClassName = name.substring(0, name.length() - ARRAY_SUFFIX.length());
			Class<?> elementClass = forName(elementClassName, classLoader);
			return Array.newInstance(elementClass, 0).getClass();
		}

		// "[Ljava.lang.String;" style arrays
		if (name.startsWith(NON_PRIMITIVE_ARRAY_PREFIX) && name.endsWith(";")) {
			String elementName = name.substring(NON_PRIMITIVE_ARRAY_PREFIX.length(), name.length() - 1);
			Class<?> elementClass = forName(elementName, classLoader);
			return Array.newInstance(elementClass, 0).getClass();
		}

		// "[[I" or "[[Ljava.lang.String;" style arrays
		if (name.startsWith(INTERNAL_ARRAY_PREFIX)) {
			String elementName = name.substring(INTERNAL_ARRAY_PREFIX.length());
			Class<?> elementClass = forName(elementName, classLoader);
			return Array.newInstance(elementClass, 0).getClass();
		}

		ClassLoader clToUse = classLoader;
		try {
			return (clToUse != null ? clToUse.loadClass(name) : Class.forName(name));
		} catch (ClassNotFoundException ex) {
			int lastDotIndex = name.lastIndexOf(PACKAGE_SEPARATOR);
			if (lastDotIndex != -1) {
				String innerClassName =
						name.substring(0, lastDotIndex) + INNER_CLASS_SEPARATOR + name.substring(lastDotIndex + 1);
				try {
					return (clToUse != null ? clToUse.loadClass(innerClassName) : Class.forName(innerClassName));
				} catch (ClassNotFoundException ex2) {
					// Swallow - let original exception get through
				}
			}
		}
		return null;
	}


	public static String generateBeanName(BeanDefinition definition, BeanFactory registry, boolean isInnerBean) {

		String generatedBeanName = definition.getBeanClassName();
		if (generatedBeanName == null) {
			if (definition.getFactoryBeanName() != null) {
				generatedBeanName = definition.getFactoryBeanName() + "$created";
			}
		}
		if (!StringUtils.hasText(generatedBeanName)) {
			throw new SimpleException("can't generate bean name!");
		}

		String id = generatedBeanName;
		if (isInnerBean) {
			// Inner bean: generate identity hashcode suffix.
			id = generatedBeanName + GENERATED_BEAN_NAME_SEPARATOR + ObjectUtils.getIdentityHexString(definition);
		} else {
			// Top-level bean: use plain class name.
			// Increase counter until the id is unique.
			int counter = -1;
			while (counter == -1 || registry.containsBeanDefinition(id)) {
				counter++;
				id = generatedBeanName + GENERATED_BEAN_NAME_SEPARATOR + counter;
			}
		}
		return id;
	}

	public static String generateAnnotatedBeanName(BeanDefinition definition, BeanFactory registry) {
		Class<?> beanClass = definition.getBeanClass();
		if (beanClass != null) {
			Map<Class<?>, Map<String, Object>> attrs = AnnotationUtils.parseAnnotation(beanClass);
			String beanName = null;
			Map<String, Object> attr = attrs.get(Component.class);
			if (attr != null) {
				beanName = StringUtils.toString(attr.get(Constant.ATTR_VALUE));
			}
			if (StringUtils.hasText(beanName)) {
				// Explicit bean name found.
				return beanName;
			}
		}
		return generateBeanName(definition.getBeanClassName());
	}

	public static String generateBeanName(String className) {
		return decapitalize(getShortName(className));
	}


	public static String getShortName(String className) {
		int lastDotIndex = className.lastIndexOf(PACKAGE_SEPARATOR);
		int nameEndIndex = className.indexOf(PROXY_CLASS_SEPARATOR);
		if (nameEndIndex == -1) {
			nameEndIndex = className.length();
		}
		String shortName = className.substring(lastDotIndex + 1, nameEndIndex);
		shortName = shortName.replace(INNER_CLASS_SEPARATOR, PACKAGE_SEPARATOR);
		return shortName;
	}


	public static String decapitalize(String name) {
		if (name == null || name.length() == 0) {
			return name;
		}
		if (name.length() > 1 && Character.isUpperCase(name.charAt(1)) && Character.isUpperCase(name.charAt(0))) {
			return name;
		}
		char chars[] = name.toCharArray();
		chars[0] = Character.toLowerCase(chars[0]);
		return new String(chars);
	}


	public static String convertResourcePathToClassName(String resourcePath) {
		return resourcePath.replace(PATH_SEPARATOR, PACKAGE_SEPARATOR);
	}


	public static String convertClassNameToResourcePath(String className) {
		return className.replace(PACKAGE_SEPARATOR, PATH_SEPARATOR);
	}



	public static Field findField(Class<?> clazz, String name) {
		return findField(clazz, name, null);
	}


	public static Field findField(Class<?> clazz, String name, Class<?> type) {
		Class<?> searchType = clazz;
		while (Object.class != searchType && searchType != null) {
			Field[] fields = getDeclaredFields(searchType);
			for (Field field : fields) {
				if ((name == null || name.equals(field.getName())) && (type == null || type.equals(field.getType()))) {
					return field;
				}
			}
			searchType = searchType.getSuperclass();
		}
		return null;
	}


	private static Field[] getDeclaredFields(Class<?> clazz) {
		Field[] result = declaredFieldsCache.get(clazz);
		if (result == null) {
			try {
				result = clazz.getDeclaredFields();
				declaredFieldsCache.put(clazz, (result.length == 0 ? NO_FIELDS : result));
			} catch (Throwable ex) {
				throw new IllegalStateException("Failed to introspect Class [" + clazz.getName()
						+ "] from ClassLoader [" + clazz.getClassLoader() + "]", ex);
			}
		}
		return result;
	}


	public static boolean isJavaLanguageInterface(Class<?> ifc) {
		return javaLanguageInterfaces.contains(ifc);
	}


	public static String getQualifiedMethodName(Method method) {
		return getQualifiedMethodName(method, null);
	}


	public static String getQualifiedMethodName(Method method, Class<?> clazz) {
		return (clazz != null ? clazz : method.getDeclaringClass()).getName() + '.' + method.getName();
	}


	public static boolean isSimpleProperty(Class<?> clazz) {
		return isSimpleValueType(clazz) || (clazz.isArray() && isSimpleValueType(clazz.getComponentType()));
	}


	public static boolean isSimpleValueType(Class<?> clazz) {
		return (isPrimitiveOrWrapper(clazz) || Enum.class.isAssignableFrom(clazz)
				|| CharSequence.class.isAssignableFrom(clazz) || Number.class.isAssignableFrom(clazz)
				|| Date.class.isAssignableFrom(clazz) || URI.class == clazz || URL.class == clazz
				|| Locale.class == clazz || Class.class == clazz);
	}


	public static boolean isPrimitiveWrapper(Class<?> clazz) {
		return primitiveWrapperTypeMap.containsKey(clazz);
	}


	public static boolean isPrimitiveOrWrapper(Class<?> clazz) {
		return (clazz.isPrimitive() || isPrimitiveWrapper(clazz));
	}

	public static <R> R skipException(ErrorSkiper<R> errorSkiper) {
		try {
			return errorSkiper.skip();
		} catch (Exception e) {
			// ignore
		}
		return null;
	}

	public interface ErrorSkiper<R> {
		R skip() throws Exception;
	}


	public static ClassLoader getDefaultClassLoader() {
		ClassLoader cl = null;
		try {
			cl = Thread.currentThread().getContextClassLoader();
		} catch (Throwable ex) {
			// Cannot access thread context ClassLoader - falling back...
		}
		if (cl == null) {
			// No thread context class loader -> use class loader of this class.
			cl = BeanUtils.class.getClassLoader();
			if (cl == null) {
				// getClassLoader() returning null indicates the bootstrap ClassLoader
				try {
					cl = ClassLoader.getSystemClassLoader();
				} catch (Throwable ex) {
					// Cannot access system ClassLoader - oh well, maybe the caller can live with
					// null...
				}
			}
		}
		return cl;
	}


	public static boolean hasDestroyMethod(Object bean, BeanDefinition beanDefinition) {
		if (bean instanceof AutoCloseable) {
			return true;
		}
		String destroyMethodName = beanDefinition.getDestroyMethodName();
		if (BeanDefinition.INFER_METHOD.equals(destroyMethodName)) {
			return (hasMethod(bean.getClass(), CLOSE_METHOD_NAME) || hasMethod(bean.getClass(), SHUTDOWN_METHOD_NAME));
		}
		return StringUtils.hasLength(destroyMethodName) && hasMethod(bean.getClass(), destroyMethodName);
	}


	public static boolean hasConstructor(Class<?> clazz, Class<?>... paramTypes) {
		return (getConstructorIfAvailable(clazz, paramTypes) != null);
	}



	public static <T> Constructor<T> getConstructorIfAvailable(Class<T> clazz, Class<?>... paramTypes) {
		try {
			return clazz.getConstructor(paramTypes);
		} catch (NoSuchMethodException ex) {
			return null;
		}
	}


	public static boolean hasMethod(Class<?> clazz, String methodName, Class<?>... paramTypes) {
		return (getMethodIfAvailable(clazz, methodName, paramTypes) != null);
	}


	public static Method getMethod(Class<?> clazz, String methodName, Class<?>... paramTypes) {
		if (paramTypes != null) {
			try {
				return clazz.getMethod(methodName, paramTypes);
			} catch (NoSuchMethodException ex) {
				throw new IllegalStateException("Expected method not found: " + ex);
			}
		} else {
			Set<Method> candidates = new HashSet<>(1);
			Method[] methods = clazz.getMethods();
			for (Method method : methods) {
				if (methodName.equals(method.getName())) {
					candidates.add(method);
				}
			}
			if (candidates.size() == 1) {
				return candidates.iterator().next();
			} else if (candidates.isEmpty()) {
				throw new IllegalStateException("Expected method not found: " + clazz.getName() + '.' + methodName);
			} else {
				throw new IllegalStateException("No unique method found: " + clazz.getName() + '.' + methodName);
			}
		}
	}

	public static Set<Method> getMethods(Class<?> clazz, String methodName) {
		Set<Method> candidates = new HashSet<>(1);
		Method[] methods = clazz.getMethods();
		for (Method method : methods) {
			if (methodName.equals(method.getName())) {
				candidates.add(method);
			}
		}
		return candidates;
	}



	public static Method getMethodIfAvailable(Class<?> clazz, String methodName, Class<?>... paramTypes) {
		if (paramTypes != null) {
			try {
				return clazz.getMethod(methodName, paramTypes);
			} catch (NoSuchMethodException ex) {
				return null;
			}
		} else {
			Set<Method> candidates = new HashSet<>(1);
			Method[] methods = clazz.getMethods();
			for (Method method : methods) {
				if (methodName.equals(method.getName())) {
					candidates.add(method);
				}
			}
			if (candidates.size() == 1) {
				return candidates.iterator().next();
			}
			return null;
		}
	}


	public static int getMethodCountForName(Class<?> clazz, String methodName) {
		int count = 0;
		Method[] declaredMethods = clazz.getDeclaredMethods();
		for (Method method : declaredMethods) {
			if (methodName.equals(method.getName())) {
				count++;
			}
		}
		Class<?>[] ifcs = clazz.getInterfaces();
		for (Class<?> ifc : ifcs) {
			count += getMethodCountForName(ifc, methodName);
		}
		if (clazz.getSuperclass() != null) {
			count += getMethodCountForName(clazz.getSuperclass(), methodName);
		}
		return count;
	}


	public static boolean hasAtLeastOneMethodWithName(Class<?> clazz, String methodName) {
		Method[] declaredMethods = clazz.getDeclaredMethods();
		for (Method method : declaredMethods) {
			if (method.getName().equals(methodName)) {
				return true;
			}
		}
		Class<?>[] ifcs = clazz.getInterfaces();
		for (Class<?> ifc : ifcs) {
			if (hasAtLeastOneMethodWithName(ifc, methodName)) {
				return true;
			}
		}
		return (clazz.getSuperclass() != null && hasAtLeastOneMethodWithName(clazz.getSuperclass(), methodName));
	}


	public static Method getMostSpecificMethod(Method method, Class<?> targetClass) {
		if (targetClass != null && targetClass != method.getDeclaringClass() && isOverridable(method, targetClass)) {
			try {
				if (Modifier.isPublic(method.getModifiers())) {
					try {
						return targetClass.getMethod(method.getName(), method.getParameterTypes());
					} catch (NoSuchMethodException ex) {
						return method;
					}
				} else {
					Method specificMethod = findMethod(targetClass, method.getName(), method.getParameterTypes());
					return (specificMethod != null ? specificMethod : method);
				}
			} catch (SecurityException ex) {
				// Security settings are disallowing reflective access; fall back to 'method'
				// below.
			}
		}
		return method;
	}


	public static Method findMethod(Class<?> clazz, String name, Class<?>... paramTypes) {
		Class<?> searchType = clazz;
		Method result = null;
		while (searchType != null) {
			Method[] methods = (searchType.isInterface() ? searchType.getMethods() : searchType.getDeclaredMethods());
			for (Method method : methods) {
				if (name.equals(method.getName())
						&& (((paramTypes == null || paramTypes.length == 0) && method.getParameterTypes().length == 0)
								|| paramsFit(paramTypes, method.getParameterTypes()))) {
					if (result != null) {
						if (((result.getModifiers() ^ method.getModifiers()) & Modifier.PUBLIC) > 0) {
							if (Modifier.isPublic(method.getModifiers())) {
								result = method;
							}
						} else {
							if (method.getParameterCount() < result.getParameterCount()) {
								result = method;
							}
						}
					} else {
						result = method;
					}
				}
			}
			if (result == null) {
				searchType = searchType.getSuperclass();
			} else {
				searchType = null;
			}
		}
		return result;
	}

	public static Constructor<?> findConstructor(Class<?> clazz, Class<?>... paramTypes) {
		Class<?> searchType = clazz;
		Constructor<?> result = null;
		while (searchType != null) {
			Constructor<?>[] constructors = clazz.getDeclaredConstructors();
			for (Constructor<?> constructor : constructors) {
				if (((paramTypes == null || paramTypes.length == 0) && constructor.getParameterTypes().length == 0)
						|| paramsFit(paramTypes, constructor.getParameterTypes())) {
					if (result != null) {
						if (((result.getModifiers() ^ constructor.getModifiers()) & Modifier.PUBLIC) > 0) {
							if (Modifier.isPublic(constructor.getModifiers())) {
								result = constructor;
							}
						}
					} else {
						result = constructor;
					}
				}
			}
			if (result == null) {
				searchType = searchType.getSuperclass();
			} else {
				searchType = null;
			}
		}
		return result;
	}

	public static boolean paramsFit(Class<?>[] current, Class<?>[] restrict) {
		if (current == null || restrict == null) {
			throw new IllegalArgumentException("params could not be null");
		}
		if (current.length != restrict.length) {
			return false;
		}
		for (int i = 0; i < restrict.length; i++) {
			if (!BeanUtils.resolvePrimitiveIfNecessary(restrict[i])
					.isAssignableFrom(BeanUtils.resolvePrimitiveIfNecessary(current[i]))) {
				return false;
			}
		}
		return true;
	}

	public static boolean paramsEqual(Class<?>[] a, Class<?>[] b) {
		if (a == null || b == null) {
			throw new IllegalArgumentException("params could not be null");
		}
		if (a.length != b.length) {
			return false;
		}
		for (int i = 0; i < b.length; i++) {
			if (!a[i].equals(b[i])) {
				return false;
			}
		}
		return true;
	}


	public static Method getInterfaceMethodIfPossible(Method method) {
		if (Modifier.isPublic(method.getModifiers()) && !method.getDeclaringClass().isInterface()) {
			Class<?> current = method.getDeclaringClass();
			while (current != null && current != Object.class) {
				Class<?>[] ifcs = current.getInterfaces();
				for (Class<?> ifc : ifcs) {
					try {
						return ifc.getMethod(method.getName(), method.getParameterTypes());
					} catch (NoSuchMethodException ex) {
						// ignore
					}
				}
				current = current.getSuperclass();
			}
		}
		return method;
	}


	public static boolean isUserLevelMethod(Method method) {
		return (method.isBridge() || (!method.isSynthetic() && !isGroovyObjectMethod(method)));
	}

	private static boolean isGroovyObjectMethod(Method method) {
		return method.getDeclaringClass().getName().equals("groovy.lang.GroovyObject");
	}


	private static boolean isOverridable(Method method, Class<?> targetClass) {
		if (Modifier.isPrivate(method.getModifiers())) {
			return false;
		}
		if (Modifier.isPublic(method.getModifiers()) || Modifier.isProtected(method.getModifiers())) {
			return true;
		}
		return (targetClass == null || getPackageName(method.getDeclaringClass()).equals(getPackageName(targetClass)));
	}



	public static Method getStaticMethod(Class<?> clazz, String methodName, Class<?>... args) {
		try {
			Method method = clazz.getMethod(methodName, args);
			return Modifier.isStatic(method.getModifiers()) ? method : null;
		} catch (NoSuchMethodException ex) {
			return null;
		}
	}


	public static String getPackageName(Class<?> clazz) {
		return getPackageName(clazz.getName());
	}


	public static String getPackageName(String fqClassName) {
		int lastDotIndex = fqClassName.lastIndexOf(PACKAGE_SEPARATOR);
		return (lastDotIndex != -1 ? fqClassName.substring(0, lastDotIndex) : "");
	}


	public static Class<?> resolvePrimitiveIfNecessary(Class<?> clazz) {
		return (clazz.isPrimitive() && clazz != void.class ? primitiveTypeToWrapperMap.get(clazz) : clazz);
	}


	public static Class<?> getUserClass(Object instance) {
		return getUserClass(instance.getClass());
	}


	public static Class<?> getUserClass(Class<?> clazz) {
		if (clazz.getName().contains(PROXY_CLASS_SEPARATOR)) {
			Class<?> superclass = clazz.getSuperclass();
			if (superclass != null && superclass != Object.class) {
				return superclass;
			}
		}
		return clazz;
	}

	public static Executable getCurrentMethod(Object... args) {
		StackTraceElement stack = Thread.currentThread().getStackTrace()[2];
		String methodName = stack.getMethodName();
		Class<?> clazz = BeanUtils.forName(stack.getClassName(), null);
		Class<?>[] paramTypes = null;
		if (args != null) {
			paramTypes = Arrays.asList(args).stream().map(Object::getClass).toArray(Class[]::new);
		}
		if ("<init>".equals(methodName)) {
			return findConstructor(clazz, paramTypes);
		}
		return findMethod(clazz, methodName, paramTypes);
	}


	public static void sortFactoryMethods(Method[] factoryMethods) {
		Arrays.sort(factoryMethods, (fm1, fm2) -> {
			boolean p1 = Modifier.isPublic(fm1.getModifiers());
			boolean p2 = Modifier.isPublic(fm2.getModifiers());
			if (p1 != p2) {
				return (p1 ? -1 : 1);
			}
			int c1pl = fm1.getParameterCount();
			int c2pl = fm2.getParameterCount();
			return (c1pl < c2pl ? 1 : (c1pl > c2pl ? -1 : 0));
		});
	}

	public static String[] getParameterNames(Executable executable) {

		Parameter[] parameters = executable.getParameters();
		Class<?> owner = executable.getDeclaringClass();
		String[] parameterNames = null;
		Map<Member, String[]> map = parameterNamesCache.get(executable.getDeclaringClass());
		if (map != null) {
			parameterNames = map.get(executable);
		}
		if (parameterNames == null) {
			parameterNames = new String[parameters.length];
		}
		for (int i = 0; i < parameters.length; i++) {
			Parameter param = parameters[i];
			if (!param.isNamePresent()) {
				break;
			}
			parameterNames[i] = param.getName();
		}
		if (StringUtils.isEmpty(parameterNames[0])) {
			try {
				Map<Member, String[]> cache = new HashMap<>();
				ClassReader reader = new ClassReader(owner.getResourceAsStream(BeanUtils.getClassFileName(owner)));
				reader.accept(new ClassVisitor(SpringAsmInfo.ASM_VERSION) {

					@Override
					public MethodVisitor visitMethod(int access, String methodName, String descriptor, String signature,
							String[] exceptions) {

						com.bc.simple.bean.core.asm.Type[] args =
								com.bc.simple.bean.core.asm.Type.getArgumentTypes(descriptor);
						String[] paras = new String[args.length];
						int[] lvtSlotIndex = computeLvtSlotIndices((access & Opcodes.ACC_STATIC) > 0, args);
						return new MethodVisitor(SpringAsmInfo.ASM_VERSION) {
							private static final String CONSTRUCTOR = "<init>";
							private static final String CLINIT = "<clinit>";

							@Override
							public void visitLocalVariable(String name, String descriptor, String signature,
									Label start, Label end, int index) {
								for (int i = 0; i < lvtSlotIndex.length; i++) {
									if (lvtSlotIndex[i] == index) {
										paras[i] = name;
									}
								}

							}

							@Override
							public void visitEnd() {
								if (!methodName.equals(CLINIT)) {
									cache.put(resolveMember(), paras);
								}
							}

							private Member resolveMember() {
								ClassLoader loader = owner.getClassLoader();
								Class<?>[] argTypes = new Class<?>[args.length];
								for (int i = 0; i < args.length; i++) {
									argTypes[i] = BeanUtils.forName(args[i].getClassName(), loader);
								}
								try {
									if (CONSTRUCTOR.equals(methodName)) {
										return owner.getDeclaredConstructor(argTypes);
									}
									return owner.getDeclaredMethod(methodName, argTypes);
								} catch (NoSuchMethodException ex) {
									throw new IllegalStateException("Method [" + methodName
											+ "] was discovered in the .class file but cannot be resolved in the class object",
											ex);
								}
							}
						};
					}

					private int[] computeLvtSlotIndices(boolean isStatic,
							com.bc.simple.bean.core.asm.Type[] paramTypes) {
						int[] lvtIndex = new int[paramTypes.length];
						int nextIndex = (isStatic ? 0 : 1);
						for (int i = 0; i < paramTypes.length; i++) {
							lvtIndex[i] = nextIndex;
							if (isWideType(paramTypes[i])) {
								nextIndex += 2;
							} else {
								nextIndex++;
							}
						}
						return lvtIndex;
					}

					private boolean isWideType(com.bc.simple.bean.core.asm.Type aType) {
						// float is not a wide type
						return (aType == com.bc.simple.bean.core.asm.Type.LONG_TYPE
								|| aType == com.bc.simple.bean.core.asm.Type.DOUBLE_TYPE);
					}
				}, 0);
				parameterNames = cache.get(executable);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		if (StringUtils.isEmpty(parameterNames[0])) {
			Map<Member, String[]> cache = new HashMap<>();
			cache.put(executable, parameterNames);
			parameterNamesCache.put(owner, cache);
		}
		return parameterNames;
	}


	public static boolean isQualifier(Class<? extends Annotation> annotationType) {
		for (Class<? extends Annotation> qualifierType : qualifierTypes) {
			if (annotationType.equals(qualifierType) || annotationType.isAnnotationPresent(qualifierType)) {
				return true;
			}
		}
		return false;
	}

	private static final Map<Class<?>, Map<Set<ClassGenericParameter>, Class<?>>> genericCache = new HashMap<>();

	/**
	 * find the generic type of {@code source} declared by {@code declare}
	 * 
	 * @param source the target class
	 * @param declare the class declared the generic param
	 * @param index the generic params' index of the target
	 * @return
	 */
	public static Class<?> getGeneric(Class<?> source, Class<?> declare, int index) {
		Map<Set<ClassGenericParameter>, Class<?>> map = getGenericMap(source);
		for (Set<ClassGenericParameter> key : map.keySet()) {
			if (key.contains(new ClassGenericParameter(declare, index))) {
				return map.get(key);
			}
		}
		return null;
	}

	/**
	 * @see BeanUtils#getGeneric(Class, Class, int)
	 * @param source
	 * @param declare
	 * @return
	 */
	public static List<Class<?>> getGenerics(Class<?> source, Class<?> declare) {
		Map<Set<ClassGenericParameter>, Class<?>> map = getGenericMap(source);
		Type[] params = declare.getTypeParameters();
		List<Class<?>> list = new ArrayList<Class<?>>(params.length);
		for (int i = 0; i < params.length; i++) {
			for (Set<ClassGenericParameter> key : map.keySet()) {
				if (key.contains(new ClassGenericParameter(declare, i))) {
					list.add(map.get(key));
				}
			}
		}
		return list;
	}

	/**
	 * @see BeanUtils#getGeneric(Class, Class, int)
	 * @param source
	 * @return
	 */
	public static Map<Set<ClassGenericParameter>, Class<?>> getGenericMap(Class<?> source) {
		Map<Set<ClassGenericParameter>, Class<?>> map;
		if (!genericCache.containsKey(source)) {
			map = new HashMap<>();
			walkClass(source, map);
			genericCache.put(source, map);
		} else {
			map = genericCache.get(source);
		}
		return map;
	}

	/**
	 * to identify a generic param of specific class
	 */
	public static class ClassGenericParameter {
		private Class<?> clazz;
		private int index;

		public ClassGenericParameter(Class<?> clazz, int index) {
			if (clazz == null) {
				throw new InvalidParameterException("param clazz shuld not be null!");
			}
			this.clazz = clazz;
			this.index = index;
		}

		@Override
		public int hashCode() {
			return this.clazz.hashCode() + this.index;
		}

		@Override
		public boolean equals(Object obj) {
			if (this.getClass().isInstance(obj)) {
				ClassGenericParameter cgp = (ClassGenericParameter) obj;
				return cgp.clazz.equals(this.clazz) && cgp.index == this.index;
			}
			return false;
		}

		@Override
		public String toString() {
			return this.clazz.toString() + "." + this.index;
		}
	}

	private static void walkClass(Class<?> source, Map<Set<ClassGenericParameter>, Class<?>> map) {
		if (source == null || source.equals(Object.class)) {
			return;
		}
		Type[] interfaces = source.getGenericInterfaces();
		Type superClass = source.getGenericSuperclass();
		if (superClass instanceof ParameterizedType) {
			ParameterizedType pt = (ParameterizedType) superClass;
			handleParameterizedType(map, pt);
		}
		for (Type type : interfaces) {
			if (type instanceof ParameterizedType) {
				ParameterizedType pt = (ParameterizedType) type;
				handleParameterizedType(map, pt);
			}
		}
		walkClass(source.getSuperclass(), map);
		for (Class<?> clazz : source.getInterfaces()) {
			walkClass(clazz, map);
		}

	}

	private static void handleParameterizedType(Map<Set<ClassGenericParameter>, Class<?>> map, ParameterizedType pt) {
		Type rawType = pt.getRawType();
		int index = 0;
		for (Type param : pt.getActualTypeArguments()) {
			if (param instanceof Class) {
				if (rawType instanceof Class) {
					Set<ClassGenericParameter> set = new HashSet<>();
					findCorrespondParams((Class<?>) rawType, index, set);
					map.put(set, (Class<?>) param);
				}
			}
			index++;
		}
	}

	/**
	 * find correspond generic param {@link ClassGenericParameter} set of one class
	 * 
	 * @param clazz target class
	 * @param index the index of the generic param
	 * @return
	 */
	public static Set<ClassGenericParameter> findCorrespondParams(Class<?> clazz, int index) {
		Set<ClassGenericParameter> set = new HashSet<>();
		findCorrespondParams(clazz, index, set);
		return set;
	}

	private static void findCorrespondParams(Class<?> clazz, int index, Set<ClassGenericParameter> set) {
		set.add(new ClassGenericParameter(clazz, index));
		Type param = clazz.getTypeParameters()[index];
		Type[] interfaces = clazz.getGenericInterfaces();
		Type superClass = clazz.getGenericSuperclass();
		if (superClass instanceof ParameterizedType) {
			ParameterizedType pt = (ParameterizedType) superClass;
			Type rawType = pt.getRawType();
			int i = 0;
			for (Type type : pt.getActualTypeArguments()) {
				if (type.equals(param) && rawType instanceof Class<?>) {
					findCorrespondParams((Class<?>) rawType, i, set);
				}
				i++;
			}

		}
		for (Type itfe : interfaces) {
			if (itfe instanceof ParameterizedType) {
				ParameterizedType pt = (ParameterizedType) itfe;
				Type rawType = pt.getRawType();
				int i = 0;
				for (Type type : pt.getActualTypeArguments()) {
					if (type.equals(param) && rawType instanceof Class<?>) {
						findCorrespondParams((Class<?>) rawType, i, set);
					}
					i++;
				}
			}
		}
	}


	public static boolean isAssignable(Class<?> lhsType, Class<?> rhsType) {
		if (lhsType.isAssignableFrom(rhsType)) {
			return true;
		}
		if (lhsType.isPrimitive()) {
			Class<?> resolvedPrimitive = primitiveWrapperTypeMap.get(rhsType);
			if (lhsType == resolvedPrimitive) {
				return true;
			}
		} else {
			Class<?> resolvedWrapper = primitiveTypeToWrapperMap.get(rhsType);
			if (resolvedWrapper != null && lhsType.isAssignableFrom(resolvedWrapper)) {
				return true;
			}
		}
		return false;
	}


	public static boolean isAssignableValue(Class<?> type, Object value) {
		return (value != null ? isAssignable(type, value.getClass()) : !type.isPrimitive());
	}


	public static int getTypeDifferenceWeight(Class<?>[] paramTypes, Object[] args) {
		int result = 0;
		for (int i = 0; i < paramTypes.length; i++) {
			if (!isAssignableValue(paramTypes[i], args[i])) {
				return Integer.MAX_VALUE;
			}
			if (args[i] != null) {
				Class<?> paramType = paramTypes[i];
				Class<?> superClass = args[i].getClass().getSuperclass();
				while (superClass != null) {
					if (paramType.equals(superClass)) {
						result = result + 2;
						superClass = null;
					} else if (isAssignable(paramType, superClass)) {
						result = result + 2;
						superClass = superClass.getSuperclass();
					} else {
						superClass = null;
					}
				}
				if (paramType.isInterface()) {
					result = result + 1;
				}
			}
		}
		return result;
	}


	public static int getTypeDifferenceWeight(Class<?> son, Class<?> parrent) {
		int result = -1;
		if (!parrent.isAssignableFrom(son)) {
			return result;
		}
		result++;
		if (parrent.equals(son)) {
			return result;
		}
		Class<?> tmp = son;
		while (!parrent.equals(tmp)) {
			Class<?> parent = tmp.getSuperclass();
			if (parrent.isAssignableFrom(parent)) {
				tmp = parent;
				result++;
				continue;
			}
			for (Class<?> iface : tmp.getInterfaces()) {
				if (parrent.isAssignableFrom(iface)) {
					tmp = iface;
					result++;
					break;
				}
			}
		}
		return result;
	}


	public static void sortConstructors(Constructor<?>[] constructors) {
		Arrays.sort(constructors, (c1, c2) -> {
			boolean p1 = Modifier.isPublic(c1.getModifiers());
			boolean p2 = Modifier.isPublic(c2.getModifiers());
			if (p1 != p2) {
				return (p1 ? -1 : 1);
			}
			int c1pl = c1.getParameterCount();
			int c2pl = c2.getParameterCount();
			return (c1pl < c2pl ? 1 : (c1pl > c2pl ? -1 : 0));
		});
	}


	public static String getClassFileName(Class<?> clazz) {
		String className = clazz.getName();
		int lastDotIndex = className.lastIndexOf(PACKAGE_SEPARATOR);
		return className.substring(lastDotIndex + 1) + CLASS_FILE_SUFFIX;
	}


	public static <T> T as(Class<T> clazz, Object obj) {
		if (clazz.isInstance(obj)) {
			return clazz.cast(obj);
		}
		throw new ClassCastException(obj + " can't be cast to type " + clazz);
	}

}
