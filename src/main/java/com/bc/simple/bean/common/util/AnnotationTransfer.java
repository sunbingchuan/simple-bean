package com.bc.simple.bean.common.util;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.bc.simple.bean.common.annotation.AliasFor;

@SuppressWarnings("unchecked")
public class AnnotationTransfer {

	private  final Log LOG = LogFactory.getLog(AnnotationTransfer.class);

	private  final Map<Class<? extends Annotation>, List<Method>> attributeMethodsCache =
			new ConcurrentHashMap<>(256);

	private  final Map<Method, Map<Class<?>, Set<String>>> aliasDescriptorCache = new ConcurrentHashMap<>(256);

	private  Map<AnnotatedElement, Map<Class<?>, Map<String, Object>>> transferAnnotationCache =
			new ConcurrentHashMap<>(256);

	/**
	 * transfer the annotations of annotatedElement into define map ,consider the {@link AliasFor} annotion
	 * @param annotatedElement
	 * @return  key = annotationType
	 * 			value = 
	 * 					key = attr name
	 * 					value = attr value 
	 */
	public  Map<Class<?>, Map<String, Object>> transferAnnotation(AnnotatedElement annotatedElement) {
		Map<Class<?>, Map<String, Object>> attrs = transferAnnotationCache.get(annotatedElement);
		if (attrs == null) {
			attrs = new HashMap<Class<?>, Map<String, Object>>();
			transferAnnotation(annotatedElement, attrs);
			transferAnnotationCache.put(annotatedElement, attrs);
		}
		return attrs;
	}
	
	private  void transferAnnotation(AnnotatedElement annotatedElement, Map<Class<?>, Map<String, Object>> attrs) {
		for (Annotation anno : AnnotationUtils.getDeclaredAnnotations(annotatedElement)) {
			if (!AnnotationUtils.isInJavaLangAnnotationPackage(anno.annotationType().getCanonicalName())) {
				transferAnnotation(anno, attrs);
			}
		}
	}

	private  void transferAnnotation(Annotation anno, Map<Class<?>, Map<String, Object>> attrs) {
		Class<?> clazz = anno.annotationType();
		transferAnnotationMethod(anno, attrs);
		transferAnnotation(clazz, attrs);
		for (Class<?> ifc : clazz.getInterfaces()) {
			transferAnnotation(ifc, attrs);
		}
		Class<?> superclass = clazz.getSuperclass();
		if (superclass != null) {
			transferAnnotation(superclass, attrs);
		}
	}

	private  void transferAnnotationMethod(Annotation anno, Map<Class<?>, Map<String, Object>> attrs) {
		Class<? extends Annotation> clazz = anno.annotationType();
		Map<String, Object> attr = attrs.get(clazz);
		if (attr == null) {
			attr = new HashMap<String, Object>();
			attrs.put(clazz, attr);
		}
		
		for (Method method : getAttributeMethods(clazz)) {
			try {
				Object value = method.invoke(anno);
				if (value == null || (StringUtils.isEmpty(value.toString()))) {
					value=method.getDefaultValue();
				}
				if (value == null || (StringUtils.isEmpty(value.toString()))) {
					continue;
				}
				Map<Class<? extends Annotation>, Set<String>> alias = fromAliasFor(method);
				for (Entry<Class<? extends Annotation>, Set<String>> entry : alias.entrySet()) {
					Class<?> annotationClass = entry.getKey();
					Set<String> attributes = entry.getValue();
					Map<String, Object> aliasAttr = attrs.get(annotationClass);
					if (aliasAttr == null) {
						aliasAttr = new HashMap<String, Object>();
						attrs.put(annotationClass, aliasAttr);
					}
					for (String attribute : attributes) {
						if (aliasAttr.get(attribute)==null) {
							aliasAttr.put(attribute, value);
						}
					}
				}
			} catch (Exception e) {
				// ignore
			}
		}
	}

	public  Map<Class<? extends Annotation>, Set<String>> fromAliasFor(String annotationType, String attribute) {
		return fromAliasFor(BeanUtils.forName(annotationType), attribute);
	}

	public  Map<Class<? extends Annotation>, Set<String>> fromAliasFor(Class<?> annotationType, String attribute) {
		Method attrMethod;
		try {
			attrMethod = annotationType.getDeclaredMethod(attribute);
		} catch (Exception e) {
			return null;
		}
		return fromAliasFor(attrMethod);
	}



	@SuppressWarnings("rawtypes")
	public  Map<Class<? extends Annotation>, Set<String>> fromAliasFor(Method attribute) {
		Map descriptor = aliasDescriptorCache.get(attribute);
		if (descriptor != null) {
			return descriptor;
		}
		descriptor = new HashMap<Class<? extends Annotation>, Set<String>>();
		fromAliasFor(attribute, descriptor);
		aliasDescriptorCache.put(attribute, descriptor);
		return descriptor;
	}

	private  void fromAliasFor(Method attribute, Map<Class<?>, Set<String>> descriptor) {
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
			fromAliasFor(method, descriptor);
		} catch (Exception e) {
			return;
		}
	}



	private  List<Method> getAttributeMethods(Class<? extends Annotation> annotationType) {
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



}
