package com.bc.simple.bean.core.processor;

import java.lang.annotation.Annotation;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import javax.annotation.Resource;

import com.bc.simple.bean.BeanDefinition;
import com.bc.simple.bean.common.annotation.Autowired;
import com.bc.simple.bean.common.annotation.Value;
import com.bc.simple.bean.common.util.AnnotationUtils;
import com.bc.simple.bean.core.support.InjectedElement;

public class AutowiredAnnotationProcessor implements Processor {

	private final Set<Class<? extends Annotation>> autowiredAnnotationTypes = new LinkedHashSet<>(4);

	public AutowiredAnnotationProcessor() {
		this.autowiredAnnotationTypes.add(Autowired.class);
		this.autowiredAnnotationTypes.add(Value.class);
		this.autowiredAnnotationTypes.add(Resource.class);
	}

	@Override
	public void postProcessMergedBeanDefinition(BeanDefinition beanDefinition) {
		Class<?> clazz;
		if (beanDefinition.hasBeanClass()) {
			clazz = beanDefinition.getBeanClass();
		} else {
			clazz = beanDefinition.resolveBeanClass();
		}
		for (Field field : clazz.getDeclaredFields()) {
			Map<Class<?>, Map<String, Object>> annotationAttributes = findAutowiredAnnotation(field);
			if (annotationAttributes != null && annotationAttributes.size() > 0) {
				InjectedElement element = new InjectedElement();
				element.setAnnotationAttributes(annotationAttributes);
				element.setField(field);
				beanDefinition.addInjectedElement(element);
			}
		}
		for (Method method : clazz.getDeclaredMethods()) {
			Map<Class<?>, Map<String, Object>> annotationAttributes = findAutowiredAnnotation(method);
			if (annotationAttributes != null && annotationAttributes.size() > 0) {
				InjectedElement element = new InjectedElement();
				element.setAnnotationAttributes(annotationAttributes);
				element.setMethod(method);
				beanDefinition.addInjectedElement(element);
			}
		}

	}

	private Map<Class<?>, Map<String, Object>> findAutowiredAnnotation(AccessibleObject ao) {
		boolean isAutowired=false;
		for (Class<? extends Annotation> autowiredAnnotationType:autowiredAnnotationTypes) {
			if (AnnotationUtils.findAnnotation(ao, autowiredAnnotationType)!=null) {
				isAutowired=true;
				break;
			}
		}
		if (isAutowired) {
			Map<Class<?>, Map<String, Object>> map=AnnotationUtils.transferAnnotation(ao);
			return map;
		}
		return null;
	}

}
