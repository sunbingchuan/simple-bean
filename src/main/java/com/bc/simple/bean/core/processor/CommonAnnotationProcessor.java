package com.bc.simple.bean.core.processor;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.bc.simple.bean.BeanDefinition;
import com.bc.simple.bean.common.util.Constant;
import com.bc.simple.bean.core.support.LifecycleElement;

public class CommonAnnotationProcessor implements Processor {

	private final transient Map<Class<?>, LinkedHashSet<LifecycleElement>> lifecycleMetadataCache = new ConcurrentHashMap<>(
			256);

	private Class<? extends Annotation> initAnnotationType;

	private Class<? extends Annotation> destroyAnnotationType;

	@Override
	public void postProcessMergedBeanDefinition(BeanDefinition beanDefinition) {
		setLifecycleMetadata(beanDefinition);
	}

	private void setLifecycleMetadata(BeanDefinition beanDefinition) {
		if (this.lifecycleMetadataCache == null) {
			buildLifecycleMetadata(beanDefinition);
		}
		Class<?> clazz = beanDefinition.getBeanClass();
		// Quick check on the concurrent map first, with minimal locking.
		LinkedHashSet<LifecycleElement> lifecycleElements = this.lifecycleMetadataCache.get(clazz);
		if (lifecycleElements == null) {
			synchronized (this.lifecycleMetadataCache) {
				lifecycleElements = this.lifecycleMetadataCache.get(clazz);
				if (lifecycleElements == null) {
					lifecycleElements = buildLifecycleMetadata(beanDefinition);
					this.lifecycleMetadataCache.put(clazz, lifecycleElements);
				}
			}
		}
	}

	private LinkedHashSet<LifecycleElement> buildLifecycleMetadata(BeanDefinition beanDefinition) {
		final LinkedHashSet<LifecycleElement> linkedHashSet = new LinkedHashSet<>();
		Class<?> targetClass = beanDefinition.getBeanClass();
		do {
			for (Method method : targetClass.getDeclaredMethods()) {
				if (this.initAnnotationType != null && method.isAnnotationPresent(this.initAnnotationType)) {
					LifecycleElement element = new LifecycleElement(method, Constant.METHOD_INIT_VALUE);
					linkedHashSet.add(element);
				}
				if (this.destroyAnnotationType != null && method.isAnnotationPresent(this.destroyAnnotationType)) {
					linkedHashSet.add(new LifecycleElement(method, Constant.METHOD_DESTROY_VALUE));
				}
			}
			targetClass = targetClass.getSuperclass();
		} while (targetClass != null && targetClass != Object.class);
		beanDefinition.getLifecycleElements().addAll(linkedHashSet);
		return linkedHashSet;
	}

}
