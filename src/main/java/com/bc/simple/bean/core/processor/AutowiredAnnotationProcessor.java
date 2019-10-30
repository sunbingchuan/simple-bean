package com.bc.simple.bean.core.processor;

import java.lang.annotation.Annotation;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.bc.simple.bean.BeanDefinition;
import com.bc.simple.bean.common.stereotype.Autowired;
import com.bc.simple.bean.common.stereotype.Value;
import com.bc.simple.bean.common.util.AnnotationUtils;
import com.bc.simple.bean.core.support.InjectedElement;

public class AutowiredAnnotationProcessor implements Processor {

	private final Set<Class<? extends Annotation>> autowiredAnnotationTypes = new LinkedHashSet<>(4);

	public AutowiredAnnotationProcessor() {
		this.autowiredAnnotationTypes.add(Autowired.class);
		this.autowiredAnnotationTypes.add(Value.class);
	}

	@Override
	public void postProcessMergedBeanDefinition(BeanDefinition beanDefinition) {
		Class<?> clazz;
		if (beanDefinition.hasBeanClass()) {
			clazz = beanDefinition.getBeanClass();
		} else {
			clazz = beanDefinition.resolveBeanClass(null);
		}
		for (Field field : clazz.getDeclaredFields()) {
			Map<String, Object> annotationAttributes = findAutowiredAnnotation(field);
			if (annotationAttributes != null && annotationAttributes.size() > 0) {
				InjectedElement element = new InjectedElement();
				element.setAnnotationAttributes(annotationAttributes);
				element.setField(field);
				beanDefinition.addInjectedElement(element);
			}
		}
		for (Method method : clazz.getDeclaredMethods()) {
			Map<String, Object> annotationAttributes = findAutowiredAnnotation(method);
			if (annotationAttributes != null && annotationAttributes.size() > 0) {
				InjectedElement element = new InjectedElement();
				element.setAnnotationAttributes(annotationAttributes);
				element.setMethod(method);
				beanDefinition.addInjectedElement(element);
			}
		}

	}

	private Map<String, Object> findAutowiredAnnotation(AccessibleObject ao) {
		List<Annotation> declaredAnnotations = Arrays.asList(AnnotationUtils.getDeclaredAnnotations(ao));
		for (Annotation annotation : declaredAnnotations) {
			Class<? extends Annotation> currentAnnotationType = annotation.annotationType();
			if (autowiredAnnotationTypes.contains(currentAnnotationType)) {
				return AnnotationUtils.retrieveAnnotationAttributes(ao, annotation, false, false);
			}
		}
		return null;
	}

}
