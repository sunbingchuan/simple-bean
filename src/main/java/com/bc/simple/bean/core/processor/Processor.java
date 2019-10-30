package com.bc.simple.bean.core.processor;

import com.bc.simple.bean.BeanDefinition;
import com.bc.simple.bean.BeanFactory;

public interface Processor {

	default void processBeanFactory(BeanFactory beanFactory) {
	}

	default void resetBeanDefinition(String beanName) {
	}

	default void processBeanDefinition(BeanDefinition beanDefinitions) {
	}

	default void postProcessMergedBeanDefinition(BeanDefinition beanDefinition) {
	}

	default Object postProcessBeforeInitialization(Object bean, String beanName) {
		return bean;
	}

	default Object postProcessAfterInitialization(Object bean, String beanName) {
		return bean;
	}

	default void postProcessAfterInstantiation(Object bean, String beanName) {
	}

	default void processInjection(Object bean) {
	}

}
