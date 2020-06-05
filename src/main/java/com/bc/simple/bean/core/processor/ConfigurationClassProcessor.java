package com.bc.simple.bean.core.processor;

import java.lang.reflect.Executable;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import com.bc.simple.bean.BeanDefinition;
import com.bc.simple.bean.common.annotation.Autowired;
import com.bc.simple.bean.common.annotation.Bean;
import com.bc.simple.bean.common.annotation.Order;
import com.bc.simple.bean.common.annotation.Primary;
import com.bc.simple.bean.common.annotation.Scope;
import com.bc.simple.bean.common.util.AnnotationUtils;
import com.bc.simple.bean.common.util.BeanUtils;
import com.bc.simple.bean.common.util.Constant;
import com.bc.simple.bean.common.util.StringUtils;
import com.bc.simple.bean.core.BeanFactory;
import com.bc.simple.bean.core.support.AnnotationMetaData.MethodMetaData;

public class ConfigurationClassProcessor implements Processor {

	private final Set<MethodMetaData> beanMethods = new LinkedHashSet<>();

	@Override
	public void processBeanFactory(BeanFactory beanFactory) {
		processConfigBean(beanFactory);
	}

	private void processConfigBean(BeanFactory beanFactory) {
		String[] candidateNames = beanFactory.getBeanDefinitionNames();
		for (String beanName : candidateNames) {
			BeanDefinition beanDefinition = beanFactory.getBeanDefinition(beanName);
			if (beanDefinition.getMetadata() != null
					&& AnnotationUtils.isFullConfigurationCandidate(beanDefinition.getMetadata())) {
				Set<MethodMetaData> methods = beanDefinition.getMetadata().getAnnotatedMethods(Bean.class.getName());
				for (MethodMetaData method : methods) {
					method.setFactaryBeanName(beanName);
				}
				beanMethods.addAll(methods);
			}
		}

		loadBeanDefinitionsForBeanMethod(beanFactory);

	}

	@SuppressWarnings("unchecked")
	private void loadBeanDefinitionsForBeanMethod(BeanFactory beanFactory) {
		for (MethodMetaData methodMetaData : beanMethods) {
			BeanDefinition beanDefinition = new BeanDefinition();
			beanDefinition.setBeanClassName(methodMetaData.getReturnTypeName());
			beanDefinition.setBeanClass(BeanUtils.forName(methodMetaData.getReturnTypeName(), null));
			beanDefinition.setMethodMetaData(methodMetaData);
			beanDefinition.setConfigClassBeanDefintion(true);
			beanDefinition.setFactoryBeanName(methodMetaData.getFactaryBeanName());
			beanDefinition.setFactoryBeanClassName(methodMetaData.getDeclaringClassName());
			beanDefinition.setFactoryMethodName(methodMetaData.getMethodName());
			Class<?>[] paramTypes = Arrays.asList(methodMetaData.getParameterTypeName()).stream().map((className) -> {
				return BeanUtils.forName(className, null);
			}).toArray(Class<?>[]::new);
			Executable factoryMethod = BeanUtils.findMethod(
					BeanUtils.forName(methodMetaData.getDeclaringClassName(), null), methodMetaData.getMethodName(),
					paramTypes);
			beanDefinition.buildMethod = factoryMethod;
			Map<String, Object> beanAttributes = (Map<String, Object>) methodMetaData
					.getAnnotationAttributes(Bean.class.getName());
			String name = (String) beanAttributes.get(Constant.ATTR_NAME);
			if (StringUtils.isEmpty(name)) {
				name = methodMetaData.getMethodName();
			}
			beanDefinition.setBeanName(name);
			Object tmpVal = null;
			Map<String, Object> autowiredAttributes = (Map<String, Object>) methodMetaData
					.getAnnotationAttributes(Autowired.class.getName());
			if (autowiredAttributes != null) {
				beanDefinition.setAutowireFactoryMethod(true);
			}
			boolean autowireCandidate = ((tmpVal = beanAttributes.get(Constant.ATTR_AUTOWIRECANDIDATE)) == null) ? true
					: (boolean) tmpVal;
			beanDefinition.setAutowireCandidate(autowireCandidate);
			String destroyMethod = (String) beanAttributes.get(Constant.ATTR_DESTROYMETHOD);
			beanDefinition.setDestroyMethodName(destroyMethod);
			String initMethod = (String) beanAttributes.get(Constant.ATTR_INITMETHOD);
			beanDefinition.setInitMethodName(initMethod);
			beanDefinition.setResource(methodMetaData.getParent().getResource());
			Map<String, Object> scopeAttributes = (Map<String, Object>) methodMetaData
					.getAnnotationAttributes(Scope.class.getName());
			if (scopeAttributes != null) {
				String scope = (String) scopeAttributes.get(Constant.ATTR_VALUE);
				if (StringUtils.isNotEmpty(scope)) {
					beanDefinition.setScope(scope);
				}
			}
			Map<String, Object> orderAttributes = (Map<String, Object>) methodMetaData
					.getAnnotationAttributes(Order.class.getName());
			if (orderAttributes != null) {
				Integer order = StringUtils.switchInteger(orderAttributes.get(Constant.ATTR_VALUE));
				if (order ==null) {
					order=AnnotationUtils.getOrder(beanDefinition.getBeanClass());
				}
				if (order !=null) {
					beanDefinition.setBeanOrder(order);;
				}
			}
			if (methodMetaData.isAnnotated(Primary.class.getName())) {
					beanDefinition.setPrimary(Boolean.TRUE);
			}
			beanFactory.registerBeanDefinition(name, beanDefinition);
		}
	}

	public Set<MethodMetaData> getBeanMethods() {
		return beanMethods;
	}

	public void addBeanMethods(MethodMetaData metaData) {
		beanMethods.add(metaData);
	}

}
