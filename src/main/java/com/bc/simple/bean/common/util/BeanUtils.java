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
import java.util.LinkedHashMap;
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
import com.bc.simple.bean.common.stereotype.Component;
import com.bc.simple.bean.common.stereotype.Configuration;
import com.bc.simple.bean.common.stereotype.Qualifier;
import com.bc.simple.bean.core.asm.ClassReader;
import com.bc.simple.bean.core.asm.ClassVisitor;
import com.bc.simple.bean.core.asm.Label;
import com.bc.simple.bean.core.asm.MethodVisitor;
import com.bc.simple.bean.core.asm.Opcodes;
import com.bc.simple.bean.core.asm.SpringAsmInfo;
import com.bc.simple.bean.core.support.AnnotationMetaData;
import com.bc.simple.bean.core.support.CurrencyException;

public class BeanUtils {

	public static final String CLOSE_METHOD_NAME = "close";

	public static final String SHUTDOWN_METHOD_NAME = "shutdown";

	/**
	 * Naming prefix for CGLIB-renamed methods.
	 * 
	 * @see #isCglibRenamedMethod
	 */
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

	/** The CGLIB class separator: {@code "$$"}. */
	public static final String CGLIB_CLASS_SEPARATOR = "$$";

	/** The ".class" file suffix. */
	public static final String CLASS_FILE_SUFFIX = ".class";
	/**
	 * Separator for generated bean names. If a class name or parent name is not
	 * unique, "#1", "#2" etc will be appended, until the name becomes unique.
	 */
	public static final String GENERATED_BEAN_NAME_SEPARATOR = "#";

	private static final Map<Class<?>, Field[]> declaredFieldsCache = new ConcurrentHashMap<>(256);

	/**
	 * Map with primitive wrapper type as key and corresponding primitive type as
	 * value, for example: Integer.class -> int.class.
	 */
	private static final Map<Class<?>, Class<?>> primitiveWrapperTypeMap = new IdentityHashMap<>(8);

	/**
	 * Map with primitive type as key and corresponding wrapper type as value, for
	 * example: int.class -> Integer.class.
	 */
	private static final Map<Class<?>, Class<?>> primitiveTypeToWrapperMap = new IdentityHashMap<>(8);

	/**
	 * Map with primitive type name as key and corresponding primitive type as
	 * value, for example: "int" -> "int.class".
	 */
	private static final Map<String, Class<?>> primitiveTypeNameMap = new HashMap<>(32);

	/**
	 * Map with common Java language class name as key and corresponding Class as
	 * value. Primarily for efficient deserialization of remote invocations.
	 */
	private static final Map<String, Class<?>> commonClassCache = new HashMap<>(64);

	/**
	 * Common Java language interfaces which are supposed to be ignored when
	 * searching for 'primary' user-level interfaces.
	 */
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

		Class<?>[] javaLanguageInterfaceArray = { Serializable.class, Externalizable.class, Closeable.class,
				AutoCloseable.class, Cloneable.class, Comparable.class };
		registerCommonClasses(javaLanguageInterfaceArray);
		javaLanguageInterfaces = new HashSet<>(Arrays.asList(javaLanguageInterfaceArray));

		qualifierTypes.add(Qualifier.class);
	}

	/**
	 * Register the given common classes with the ClassUtils cache.
	 */
	private static void registerCommonClasses(Class<?>... commonClasses) {
		for (Class<?> clazz : commonClasses) {
			commonClassCache.put(clazz.getName(), clazz);
		}
	}

	/**
	 * Instantiate a class using its 'primary' constructor (for Kotlin classes,
	 * potentially having default arguments declared) or its default constructor
	 * (for regular Java classes, expecting a standard no-arg setup).
	 * <p>
	 * Note that this method tries to set the constructor accessible if given a
	 * non-accessible (that is, non-public) constructor.
	 * 
	 * @param clazz the class to instantiate
	 * @return the new instance
	 * @throws BeanInstantiationException if the bean cannot be instantiated. The
	 *                                    cause may notably indicate a
	 *                                    {@link NoSuchMethodException} if no
	 *                                    primary/default constructor was found, a
	 *                                    {@link NoClassDefFoundError} or other
	 *                                    {@link LinkageError} in case of an
	 *                                    unresolvable class definition (e.g. due to
	 *                                    a missing dependency at runtime), or an
	 *                                    exception thrown from the constructor
	 *                                    invocation itself.
	 * @see Constructor#newInstance
	 */
	public static <T> T instantiateClass(Class<T> clazz) {
		if (clazz.isInterface()) {
			throw new CurrencyException("Specified class is an interface");
		}
		try {
			return instantiateClass(clazz.getDeclaredConstructor());
		} catch (NoSuchMethodException ex) {

			throw new CurrencyException("No default constructor found", ex);
		} catch (LinkageError err) {
			throw new CurrencyException("Unresolvable class definition", err);
		}
	}

	/**
	 * Instantiate a class using its no-arg constructor and return the new instance
	 * as the specified assignable type.
	 * <p>
	 * Useful in cases where the type of the class to instantiate (clazz) is not
	 * available, but the type desired (assignableTo) is known.
	 * <p>
	 * Note that this method tries to set the constructor accessible if given a
	 * non-accessible (that is, non-public) constructor.
	 * 
	 * @param clazz        class to instantiate
	 * @param assignableTo type that clazz must be assignableTo
	 * @return the new instance
	 * @throws BeanInstantiationException if the bean cannot be instantiated
	 * @see Constructor#newInstance
	 */
	@SuppressWarnings("unchecked")
	public static <T> T instantiateClass(Class<?> clazz, Class<T> assignableTo) {
		return (T) instantiateClass(clazz);
	}

	/**
	 * Convenience method to instantiate a class using the given constructor.
	 * <p>
	 * Note that this method tries to set the constructor accessible if given a
	 * non-accessible (that is, non-public) constructor, and supports Kotlin classes
	 * with optional parameters and default values.
	 * 
	 * @param ctor the constructor to instantiate
	 * @param args the constructor arguments to apply (use {@code null} for an
	 *             unspecified parameter if needed for Kotlin classes with optional
	 *             parameters and default values)
	 * @return the new instance
	 * @throws BeanInstantiationException if the bean cannot be instantiated
	 * @see Constructor#newInstance
	 */
	public static <T> T instantiateClass(Constructor<T> ctor, Object... args) {
		try {
			ctor.setAccessible(true);
			return ctor.newInstance(args);
		} catch (InstantiationException ex) {
			throw new CurrencyException("Is it an abstract class?", ex);
		} catch (IllegalAccessException ex) {
			throw new CurrencyException("Is the constructor accessible?", ex);
		} catch (IllegalArgumentException ex) {
			throw new CurrencyException("Illegal arguments for constructor", ex);
		} catch (Exception ex) {
			throw new CurrencyException("Constructor threw exception", ex);
		}
	}

	/**
	 * Create a new GenericBeanDefinition for the given parent name and class name,
	 * eagerly loading the bean class if a ClassLoader has been specified.
	 * 
	 * @param parentName  the name of the parent bean, if any
	 * @param className   the name of the bean class, if any
	 * @param classLoader the ClassLoader to use for loading bean classes (can be
	 *                    {@code null} to just register bean classes by name)
	 * @return the bean definition
	 * @throws ClassNotFoundException if the bean class could not be loaded
	 */
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

	/**
	 * Replacement for {@code Class.forName()} that also returns Class instances for
	 * primitives (e.g. "int") and array class names (e.g. "String[]"). Furthermore,
	 * it is also capable of resolving inner class names in Java source style (e.g.
	 * "java.lang.Thread.State" instead of "java.lang.Thread$State").
	 * 
	 * @param name        the name of the Class
	 * @param classLoader the class loader to use (may be {@code null}, which
	 *                    indicates the default class loader)
	 * @return a class instance for the supplied name
	 * @throws ClassNotFoundException if the class was not found
	 * @throws LinkageError           if the class file could not be loaded
	 * @see Class#forName(String, boolean, ClassLoader)
	 */
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
				String innerClassName = name.substring(0, lastDotIndex) + INNER_CLASS_SEPARATOR
						+ name.substring(lastDotIndex + 1);
				try {
					return (clToUse != null ? clToUse.loadClass(innerClassName) : Class.forName(innerClassName));
				} catch (ClassNotFoundException ex2) {
					// Swallow - let original exception get through
				}
			}
		}
		return null;
	}

	/**
	 * Generate a bean name for the given bean definition, unique within the given
	 * bean factory.
	 * 
	 * @param definition  the bean definition to generate a bean name for
	 * @param registry    the bean factory that the definition is going to be
	 *                    registered with (to check for existing bean names)
	 * @param isInnerBean whether the given bean definition will be registered as
	 *                    inner bean or as top-level bean (allowing for special name
	 *                    generation for inner beans versus top-level beans)
	 * @return the generated bean name
	 * @throws BeanDefinitionStoreException if no unique name can be generated for
	 *                                      the given bean definition
	 */
	public static String generateBeanName(BeanDefinition definition, BeanFactory registry, boolean isInnerBean) {

		String generatedBeanName = definition.getBeanClassName();
		if (generatedBeanName == null) {
			if (definition.getFactoryBeanName() != null) {
				generatedBeanName = definition.getFactoryBeanName() + "$created";
			}
		}
		if (!StringUtils.hasText(generatedBeanName)) {
			throw new CurrencyException("Unnamed bean definition specifies neither "
					+ "'class' nor 'parent' nor 'factory-bean' - can't generate bean name");
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
		AnnotationMetaData amd = definition.getMetadata();
		if (amd != null) {
			Set<String> types = amd.getAnnotationTypes();
			String beanName = null;
			for (String type : types) {
				if (type.equals(Component.class.getCanonicalName())
						|| type.equals(Configuration.class.getCanonicalName())) {
					LinkedHashMap<String, Object> attributes = amd.getAttributes(type);
					Object value = attributes.get("value");
					if (value instanceof String && StringUtils.isNotEmpty((String) value)) {
						beanName = (String) value;
					}
				}
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

	/**
	 * Get the class name without the qualified package name.
	 * 
	 * @param className the className to get the short name for
	 * @return the class name of the class without the package name
	 * @throws IllegalArgumentException if the className is empty
	 */
	public static String getShortName(String className) {
		int lastDotIndex = className.lastIndexOf(PACKAGE_SEPARATOR);
		int nameEndIndex = className.indexOf(CGLIB_CLASS_SEPARATOR);
		if (nameEndIndex == -1) {
			nameEndIndex = className.length();
		}
		String shortName = className.substring(lastDotIndex + 1, nameEndIndex);
		shortName = shortName.replace(INNER_CLASS_SEPARATOR, PACKAGE_SEPARATOR);
		return shortName;
	}

	/**
	 * Utility method to take a string and convert it to normal Java variable name
	 * capitalization. This normally means converting the first character from upper
	 * case to lower case, but in the (unusual) special case when there is more than
	 * one character and both the first and second characters are upper case, we
	 * leave it alone.
	 * <p>
	 * Thus "FooBah" becomes "fooBah" and "X" becomes "x", but "URL" stays as "URL".
	 *
	 * @param name The string to be decapitalized.
	 * @return The decapitalized version of the string.
	 */
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

	/**
	 * Convert a "/"-based resource path to a "."-based fully qualified class name.
	 * 
	 * @param resourcePath the resource path pointing to a class
	 * @return the corresponding fully qualified class name
	 */
	public static String convertResourcePathToClassName(String resourcePath) {
		return resourcePath.replace(PATH_SEPARATOR, PACKAGE_SEPARATOR);
	}

	/**
	 * Convert a "."-based fully qualified class name to a "/"-based resource path.
	 * 
	 * @param className the fully qualified class name
	 * @return the corresponding resource path, pointing to the class
	 */
	public static String convertClassNameToResourcePath(String className) {
		return className.replace(PACKAGE_SEPARATOR, PATH_SEPARATOR);
	}

	/**
	 * Attempt to find a {@link Field field} on the supplied {@link Class} with the
	 * supplied {@code name}. Searches all superclasses up to {@link Object}.
	 * 
	 * @param clazz the class to introspect
	 * @param name  the name of the field
	 * @return the corresponding Field object, or {@code null} if not found
	 */

	public static Field findField(Class<?> clazz, String name) {
		return findField(clazz, name, null);
	}

	/**
	 * Attempt to find a {@link Field field} on the supplied {@link Class} with the
	 * supplied {@code name} and/or {@link Class type}. Searches all superclasses up
	 * to {@link Object}.
	 * 
	 * @param clazz the class to introspect
	 * @param name  the name of the field (may be {@code null} if type is specified)
	 * @param type  the type of the field (may be {@code null} if name is specified)
	 * @return the corresponding Field object, or {@code null} if not found
	 */
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

	/**
	 * This variant retrieves {@link Class#getDeclaredFields()} from a local cache
	 * in order to avoid the JVM's SecurityManager check and defensive array
	 * copying.
	 * 
	 * @param clazz the class to introspect
	 * @return the cached array of fields
	 * @throws IllegalStateException if introspection fails
	 * @see Class#getDeclaredFields()
	 */
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

	/**
	 * Determine whether the given interface is a common Java language interface:
	 * {@link Serializable}, {@link Externalizable}, {@link Closeable},
	 * {@link AutoCloseable}, {@link Cloneable}, {@link Comparable} - all of which
	 * can be ignored when looking for 'primary' user-level interfaces. Common
	 * characteristics: no service-level operations, no bean property methods, no
	 * default methods.
	 * 
	 * @param ifc the interface to check
	 * @since 5.0.3
	 */
	public static boolean isJavaLanguageInterface(Class<?> ifc) {
		return javaLanguageInterfaces.contains(ifc);
	}

	/**
	 * Return the qualified name of the given method, consisting of fully qualified
	 * interface/class name + "." + method name.
	 * 
	 * @param method the method
	 * @return the qualified name of the method
	 */
	public static String getQualifiedMethodName(Method method) {
		return getQualifiedMethodName(method, null);
	}

	/**
	 * Return the qualified name of the given method, consisting of fully qualified
	 * interface/class name + "." + method name.
	 * 
	 * @param method the method
	 * @param clazz  the clazz that the method is being invoked on (may be
	 *               {@code null} to indicate the method's declaring class)
	 * @return the qualified name of the method
	 * @since 4.3.4
	 */
	public static String getQualifiedMethodName(Method method, Class<?> clazz) {
		return (clazz != null ? clazz : method.getDeclaringClass()).getName() + '.' + method.getName();
	}

	/**
	 * Check if the given type represents a "simple" property: a primitive, a String
	 * or other CharSequence, a Number, a Date, a URI, a URL, a Locale, a Class, or
	 * a corresponding array.
	 * <p>
	 * Used to determine properties to check for a "simple" dependency-check.
	 * 
	 * @param clazz the type to check
	 * @return whether the given type represents a "simple" property
	 * @see org.Factory.beans.factory.support.RootBeanDefinition#DEPENDENCY_CHECK_SIMPLE
	 * @see org.Factory.beans.factory.support.AbstractAutowireCapableBeanFactory#checkDependencies
	 */
	public static boolean isSimpleProperty(Class<?> clazz) {
		return isSimpleValueType(clazz) || (clazz.isArray() && isSimpleValueType(clazz.getComponentType()));
	}

	/**
	 * Check if the given type represents a "simple" value type: a primitive, an
	 * enum, a String or other CharSequence, a Number, a Date, a URI, a URL, a
	 * Locale or a Class.
	 * 
	 * @param clazz the type to check
	 * @return whether the given type represents a "simple" value type
	 */
	public static boolean isSimpleValueType(Class<?> clazz) {
		return (isPrimitiveOrWrapper(clazz) || Enum.class.isAssignableFrom(clazz)
				|| CharSequence.class.isAssignableFrom(clazz) || Number.class.isAssignableFrom(clazz)
				|| Date.class.isAssignableFrom(clazz) || URI.class == clazz || URL.class == clazz
				|| Locale.class == clazz || Class.class == clazz);
	}

	/**
	 * Check if the given class represents a primitive wrapper, i.e. Boolean, Byte,
	 * Character, Short, Integer, Long, Float, or Double.
	 * 
	 * @param clazz the class to check
	 * @return whether the given class is a primitive wrapper class
	 */
	public static boolean isPrimitiveWrapper(Class<?> clazz) {
		return primitiveWrapperTypeMap.containsKey(clazz);
	}

	/**
	 * Check if the given class represents a primitive (i.e. boolean, byte, char,
	 * short, int, long, float, or double) or a primitive wrapper (i.e. Boolean,
	 * Byte, Character, Short, Integer, Long, Float, or Double).
	 * 
	 * @param clazz the class to check
	 * @return whether the given class is a primitive or primitive wrapper class
	 */
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

	/**
	 * Return the default ClassLoader to use: typically the thread context
	 * ClassLoader, if available; the ClassLoader that loaded the ClassUtils class
	 * will be used as fallback.
	 * <p>
	 * Call this method if you intend to use the thread context ClassLoader in a
	 * scenario where you clearly prefer a non-null ClassLoader reference: for
	 * example, for class path resource loading (but not necessarily for
	 * {@code Class.forName}, which accepts a {@code null} ClassLoader reference as
	 * well).
	 * 
	 * @return the default ClassLoader (only {@code null} if even the system
	 *         ClassLoader isn't accessible)
	 * @see Thread#getContextClassLoader()
	 * @see ClassLoader#getSystemClassLoader()
	 */
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

	/**
	 * Check whether the given bean has any kind of destroy method to call.
	 * 
	 * @param bean           the bean instance
	 * @param beanDefinition the corresponding bean definition
	 */
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

	/**
	 * Determine whether the given class has a public constructor with the given
	 * signature.
	 * <p>
	 * Essentially translates {@code NoSuchMethodException} to "false".
	 * 
	 * @param clazz      the clazz to analyze
	 * @param paramTypes the parameter types of the method
	 * @return whether the class has a corresponding constructor
	 * @see Class#getMethod
	 */
	public static boolean hasConstructor(Class<?> clazz, Class<?>... paramTypes) {
		return (getConstructorIfAvailable(clazz, paramTypes) != null);
	}

	/**
	 * Determine whether the given class has a public constructor with the given
	 * signature, and return it if available (else return {@code null}).
	 * <p>
	 * Essentially translates {@code NoSuchMethodException} to {@code null}.
	 * 
	 * @param clazz      the clazz to analyze
	 * @param paramTypes the parameter types of the method
	 * @return the constructor, or {@code null} if not found
	 * @see Class#getConstructor
	 */

	public static <T> Constructor<T> getConstructorIfAvailable(Class<T> clazz, Class<?>... paramTypes) {
		try {
			return clazz.getConstructor(paramTypes);
		} catch (NoSuchMethodException ex) {
			return null;
		}
	}

	/**
	 * Determine whether the given class has a public method with the given
	 * signature.
	 * <p>
	 * Essentially translates {@code NoSuchMethodException} to "false".
	 * 
	 * @param clazz      the clazz to analyze
	 * @param methodName the name of the method
	 * @param paramTypes the parameter types of the method
	 * @return whether the class has a corresponding method
	 * @see Class#getMethod
	 */
	public static boolean hasMethod(Class<?> clazz, String methodName, Class<?>... paramTypes) {
		return (getMethodIfAvailable(clazz, methodName, paramTypes) != null);
	}

	/**
	 * Determine whether the given class has a public method with the given
	 * signature, and return it if available (else throws an
	 * {@code IllegalStateException}).
	 * <p>
	 * In case of any signature specified, only returns the method if there is a
	 * unique candidate, i.e. a single public method with the specified name.
	 * <p>
	 * Essentially translates {@code NoSuchMethodException} to
	 * {@code IllegalStateException}.
	 * 
	 * @param clazz      the clazz to analyze
	 * @param methodName the name of the method
	 * @param paramTypes the parameter types of the method (may be {@code null} to
	 *                   indicate any signature)
	 * @return the method (never {@code null})
	 * @throws IllegalStateException if the method has not been found
	 * @see Class#getMethod
	 */
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

	/**
	 * Determine whether the given class has a public method with the given
	 * signature, and return it if available (else return {@code null}).
	 * <p>
	 * In case of any signature specified, only returns the method if there is a
	 * unique candidate, i.e. a single public method with the specified name.
	 * <p>
	 * Essentially translates {@code NoSuchMethodException} to {@code null}.
	 * 
	 * @param clazz      the clazz to analyze
	 * @param methodName the name of the method
	 * @param paramTypes the parameter types of the method (may be {@code null} to
	 *                   indicate any signature)
	 * @return the method, or {@code null} if not found
	 * @see Class#getMethod
	 */

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

	/**
	 * Return the number of methods with a given name (with any argument types), for
	 * the given class and/or its superclasses. Includes non-public methods.
	 * 
	 * @param clazz      the clazz to check
	 * @param methodName the name of the method
	 * @return the number of methods with the given name
	 */
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

	/**
	 * Does the given class or one of its superclasses at least have one or more
	 * methods with the supplied name (with any argument types)? Includes non-public
	 * methods.
	 * 
	 * @param clazz      the clazz to check
	 * @param methodName the name of the method
	 * @return whether there is at least one method with the given name
	 */
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

	/**
	 * Given a method, which may come from an interface, and a target class used in
	 * the current reflective invocation, find the corresponding target method if
	 * there is one. E.g. the method may be {@code IFoo.bar()} and the target class
	 * may be {@code DefaultFoo}. In this case, the method may be
	 * {@code DefaultFoo.bar()}. This enables attributes on that method to be found.
	 * <p>
	 * <b>NOTE:</b> In contrast to
	 * {@link org.springframework.aop.support.AopUtils#getMostSpecificMethod}, this
	 * method does <i>not</i> resolve Java 5 bridge methods automatically. Call
	 * {@link org.springframework.core.BridgeMethodResolver#findBridgedMethod} if
	 * bridge method resolution is desirable (e.g. for obtaining metadata from the
	 * original method definition).
	 * <p>
	 * <b>NOTE:</b> Since Spring 3.1.1, if Java security settings disallow
	 * reflective access (e.g. calls to {@code Class#getDeclaredMethods} etc, this
	 * implementation will fall back to returning the originally provided method.
	 * 
	 * @param method      the method to be invoked, which may come from an interface
	 * @param targetClass the target class for the current invocation (may be
	 *                    {@code null} or may not even implement the method)
	 * @return the specific target method, or the original method if the
	 *         {@code targetClass} does not implement it
	 * @see #getInterfaceMethodIfPossible
	 */
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

	/**
	 * Attempt to find a {@link Method} on the supplied class with the supplied name
	 * and parameter types. Searches all superclasses up to {@code Object}.
	 * <p>
	 * Returns {@code null} if no {@link Method} can be found.
	 * 
	 * @param clazz      the class to introspect
	 * @param name       the name of the method
	 * @param paramTypes the parameter types of the method (may be {@code null} to
	 *                   indicate any signature)
	 * @return the Method object, or {@code null} if none found
	 */
	public static Method findMethod(Class<?> clazz, String name, Class<?>... paramTypes) {
		Class<?> searchType = clazz;
		Method result = null;
		while (searchType != null) {
			Method[] methods = (searchType.isInterface() ? searchType.getMethods() : searchType.getDeclaredMethods());
			for (Method method : methods) {
				if (name.equals(method.getName()) && (paramTypes == null || paramTypes.length == 0
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
					return method;
				}
			}
			searchType = searchType.getSuperclass();
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

	/**
	 * Determine a corresponding interface method for the given method handle, if
	 * possible.
	 * <p>
	 * This is particularly useful for arriving at a public exported type on Jigsaw
	 * which can be reflectively invoked without an illegal access warning.
	 * 
	 * @param method the method to be invoked, potentially from an implementation
	 *               class
	 * @return the corresponding interface method, or the original method if none
	 *         found
	 * @since 5.1
	 * @see #getMostSpecificMethod
	 */
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

	/**
	 * Determine whether the given method is declared by the user or at least
	 * pointing to a user-declared method.
	 * <p>
	 * Checks {@link Method#isSynthetic()} (for implementation methods) as well as
	 * the {@code GroovyObject} interface (for interface methods; on an
	 * implementation class, implementations of the {@code GroovyObject} methods
	 * will be marked as synthetic anyway). Note that, despite being synthetic,
	 * bridge methods ({@link Method#isBridge()}) are considered as user-level
	 * methods since they are eventually pointing to a user-declared generic method.
	 * 
	 * @param method the method to check
	 * @return {@code true} if the method can be considered as user-declared; [@code
	 *         false} otherwise
	 */
	public static boolean isUserLevelMethod(Method method) {
		return (method.isBridge() || (!method.isSynthetic() && !isGroovyObjectMethod(method)));
	}

	private static boolean isGroovyObjectMethod(Method method) {
		return method.getDeclaringClass().getName().equals("groovy.lang.GroovyObject");
	}

	/**
	 * Determine whether the given method is overridable in the given target class.
	 * 
	 * @param method      the method to check
	 * @param targetClass the target class to check against
	 */
	private static boolean isOverridable(Method method, Class<?> targetClass) {
		if (Modifier.isPrivate(method.getModifiers())) {
			return false;
		}
		if (Modifier.isPublic(method.getModifiers()) || Modifier.isProtected(method.getModifiers())) {
			return true;
		}
		return (targetClass == null || getPackageName(method.getDeclaringClass()).equals(getPackageName(targetClass)));
	}

	/**
	 * Return a public static method of a class.
	 * 
	 * @param clazz      the class which defines the method
	 * @param methodName the static method name
	 * @param args       the parameter types to the method
	 * @return the static method, or {@code null} if no static method was found
	 * @throws IllegalArgumentException if the method name is blank or the clazz is
	 *                                  null
	 */

	public static Method getStaticMethod(Class<?> clazz, String methodName, Class<?>... args) {
		try {
			Method method = clazz.getMethod(methodName, args);
			return Modifier.isStatic(method.getModifiers()) ? method : null;
		} catch (NoSuchMethodException ex) {
			return null;
		}
	}

	/**
	 * Determine the name of the package of the given class, e.g. "java.lang" for
	 * the {@code java.lang.String} class.
	 * 
	 * @param clazz the class
	 * @return the package name, or the empty String if the class is defined in the
	 *         default package
	 */
	public static String getPackageName(Class<?> clazz) {
		return getPackageName(clazz.getName());
	}

	/**
	 * Determine the name of the package of the given fully-qualified class name,
	 * e.g. "java.lang" for the {@code java.lang.String} class name.
	 * 
	 * @param fqClassName the fully-qualified class name
	 * @return the package name, or the empty String if the class is defined in the
	 *         default package
	 */
	public static String getPackageName(String fqClassName) {
		int lastDotIndex = fqClassName.lastIndexOf(PACKAGE_SEPARATOR);
		return (lastDotIndex != -1 ? fqClassName.substring(0, lastDotIndex) : "");
	}

	/**
	 * Resolve the given class if it is a primitive class, returning the
	 * corresponding primitive wrapper type instead.
	 * 
	 * @param clazz the class to check
	 * @return the original class, or a primitive wrapper for the original primitive
	 *         type
	 */
	public static Class<?> resolvePrimitiveIfNecessary(Class<?> clazz) {
		return (clazz.isPrimitive() && clazz != void.class ? primitiveTypeToWrapperMap.get(clazz) : clazz);
	}

	/**
	 * Return the user-defined class for the given instance: usually simply the
	 * class of the given instance, but the original class in case of a
	 * CGLIB-generated subclass.
	 * 
	 * @param instance the instance to check
	 * @return the user-defined class
	 */
	public static Class<?> getUserClass(Object instance) {
		return getUserClass(instance.getClass());
	}

	/**
	 * Return the user-defined class for the given class: usually simply the given
	 * class, but the original class in case of a CGLIB-generated subclass.
	 * 
	 * @param clazz the class to check
	 * @return the user-defined class
	 */
	public static Class<?> getUserClass(Class<?> clazz) {
		if (clazz.getName().contains(CGLIB_CLASS_SEPARATOR)) {
			Class<?> superclass = clazz.getSuperclass();
			if (superclass != null && superclass != Object.class) {
				return superclass;
			}
		}
		return clazz;
	}

	public static Method getCurrentMethod(Object... args) {
		StackTraceElement stack = Thread.currentThread().getStackTrace()[2];
		String methodName = stack.getMethodName();
		Class<?> proxy = BeanUtils.forName(stack.getClassName(), null);
		Class<?>[] paramTypes = null;
		if (args != null) {
			paramTypes = Arrays.asList(args).stream().map(Object::getClass).toArray(Class[]::new);
		}
		return BeanUtils.findMethod(proxy, methodName, paramTypes);
	}

	/**
	 * Sort the given factory methods, preferring public methods and "greedy" ones
	 * with a maximum of arguments. The result will contain public methods first,
	 * with decreasing number of arguments, then non-public methods, again with
	 * decreasing number of arguments.
	 * 
	 * @param factoryMethods the factory method array to sort
	 */
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

						com.bc.simple.bean.core.asm.Type[] args = com.bc.simple.bean.core.asm.Type
								.getArgumentTypes(descriptor);
						String[] paras = new String[args.length];
						int[] lvtSlotIndex = computeLvtSlotIndices((access & Opcodes.ACC_STATIC) > 0, args);
						return new MethodVisitor(SpringAsmInfo.ASM_VERSION) {
							private static final String CONSTRUCTOR = "<init>";

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
								cache.put(resolveMember(), paras);
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

	/**
	 * Checks whether the given annotation type is a recognized qualifier type.
	 */
	public static boolean isQualifier(Class<? extends Annotation> annotationType) {
		for (Class<? extends Annotation> qualifierType : qualifierTypes) {
			if (annotationType.equals(qualifierType) || annotationType.isAnnotationPresent(qualifierType)) {
				return true;
			}
		}
		return false;
	}

	public static List<Class<?>> getGeneric(Class<?> source, Class<?> declare) {
		List<Class<?>> list = new ArrayList<Class<?>>();
		walkClass(source, list, declare);
		return list;
	}

	private static void walkClass(Class<?> source, List<Class<?>> list, Class<?> declare) {
		if (source == null || source.equals(Object.class)) {
			return;
		}
		Type[] interfaces = source.getGenericInterfaces();
		Type superClass = source.getGenericSuperclass();
		if (superClass instanceof ParameterizedType) {
			ParameterizedType pt = (ParameterizedType) superClass;
			if (pt.getRawType().getTypeName().equals(declare.getCanonicalName())) {
				for (Type param : pt.getActualTypeArguments()) {
					list.add((Class<?>) param);
				}
			}
		}
		for (Type type : interfaces) {
			if (type instanceof ParameterizedType) {
				ParameterizedType pt = (ParameterizedType) type;
				if (pt.getRawType().getTypeName().equals(declare.getCanonicalName()))
					for (Type param : pt.getActualTypeArguments()) {
						list.add((Class<?>) param);
					}
			}
		}
		if (list.size() == 0) {
			walkClass(source.getSuperclass(), list, declare);
		}
		if (list.size() == 0) {
			for (Class<?> clazz : source.getInterfaces()) {
				walkClass(clazz, list, declare);
			}

		}
	}

	/**
	 * Check if the right-hand side type may be assigned to the left-hand side type,
	 * assuming setting by reflection. Considers primitive wrapper classes as
	 * assignable to the corresponding primitive types.
	 * 
	 * @param lhsType the target type
	 * @param rhsType the value type that should be assigned to the target type
	 * @return if the target type is assignable from the value type
	 * @see TypeUtils#isAssignable
	 */
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

	/**
	 * Determine if the given type is assignable from the given value, assuming
	 * setting by reflection. Considers primitive wrapper classes as assignable to
	 * the corresponding primitive types.
	 * 
	 * @param type  the target type
	 * @param value the value that should be assigned to the type
	 * @return if the type is assignable from the value
	 */
	public static boolean isAssignableValue(Class<?> type, Object value) {
		return (value != null ? isAssignable(type, value.getClass()) : !type.isPrimitive());
	}

	/**
	 * Algorithm that judges the match between the declared parameter types of a
	 * candidate method and a specific list of arguments that this method is
	 * supposed to be invoked with.
	 * <p>
	 * Determines a weight that represents the class hierarchy difference between
	 * types and arguments. A direct match, i.e. type Integer -> arg of class
	 * Integer, does not increase the result - all direct matches means weight 0. A
	 * match between type Object and arg of class Integer would increase the weight
	 * by 2, due to the superclass 2 steps up in the hierarchy (i.e. Object) being
	 * the last one that still matches the required type Object. Type Number and
	 * class Integer would increase the weight by 1 accordingly, due to the
	 * superclass 1 step up the hierarchy (i.e. Number) still matching the required
	 * type Number. Therefore, with an arg of type Integer, a constructor (Integer)
	 * would be preferred to a constructor (Number) which would in turn be preferred
	 * to a constructor (Object). All argument weights get accumulated.
	 * <p>
	 * Note: This is the algorithm used by MethodInvoker itself and also the
	 * algorithm used for constructor and factory method selection in Spring's bean
	 * container (in case of lenient constructor resolution which is the default for
	 * regular bean definitions).
	 * 
	 * @param paramTypes the parameter types to match
	 * @param args       the arguments to match
	 * @return the accumulated weight for all arguments
	 */
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

	/**
	 * count the num of the storeys between two class
	 */
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

	/**
	 * Sort the given constructors, preferring public constructors and "greedy" ones
	 * with a maximum number of arguments. The result will contain public
	 * constructors first, with decreasing number of arguments, then non-public
	 * constructors, again with decreasing number of arguments.
	 * 
	 * @param constructors the constructor array to sort
	 */
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

	/**
	 * Determine the name of the class file, relative to the containing package:
	 * e.g. "String.class"
	 * 
	 * @param clazz the class
	 * @return the file name of the ".class" file
	 */
	public static String getClassFileName(Class<?> clazz) {
		String className = clazz.getName();
		int lastDotIndex = className.lastIndexOf(PACKAGE_SEPARATOR);
		return className.substring(lastDotIndex + 1) + CLASS_FILE_SUFFIX;
	}

	/**
	 * get the paramter indicated by @index as the type indicated by @clazz
	 */
	public static <T> T as(Class<T> clazz, Object obj) {
		if (clazz.isInstance(obj)) {
			return clazz.cast(obj);
		}
		throw new ClassCastException(obj + " can't be cast to type " + clazz);
	}

}
