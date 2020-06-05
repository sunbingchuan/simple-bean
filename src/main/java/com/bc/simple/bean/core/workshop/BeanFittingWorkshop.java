package com.bc.simple.bean.core.workshop;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.bc.simple.bean.BeanDefinition;
import com.bc.simple.bean.common.support.proxy.ProxyHelper;
import com.bc.simple.bean.common.util.AnnotationUtils;
import com.bc.simple.bean.common.util.BeanUtils;
import com.bc.simple.bean.common.util.Constant;
import com.bc.simple.bean.common.util.StringUtils;
import com.bc.simple.bean.core.BeanFactory;
import com.bc.simple.bean.core.processor.Processor;
import com.bc.simple.bean.core.support.BeanMonitor;
import com.bc.simple.bean.core.support.DependencyDescriptor;
import com.bc.simple.bean.core.support.InjectedElement;
import com.bc.simple.bean.core.support.SimpleException;

public class BeanFittingWorkshop extends Workshop {

	private Map<Member, Object> cachedValues = new ConcurrentHashMap<>();
	private Log log = LogFactory.getLog(this.getClass());

	public BeanFittingWorkshop(BeanFactory factory) {
		super(factory);
	}

	public void populateBean(String beanName, BeanDefinition mbd, Object bean) {

		if (!mbd.isSynthetic() && factory.hasInstantiationAwareBeanProcessors()) {
			for (Processor bp : factory.getProcessors()) {
				bp.postProcessAfterInstantiation(bean, beanName);
			}
		}

		Set<Map<String, Object>> pvs = (mbd.hasPropertyValues() ? mbd.getPropertyValues() : null);
		if (pvs != null && pvs.size() > 0) {
			postProperties(beanName, mbd, pvs, bean);
		}
		boolean hasInstAwareBpps = factory.hasInstantiationAwareBeanProcessors();
		if (hasInstAwareBpps) {
			postProcessProperties(bean, beanName, mbd);
		}
	}

	private void postProcessProperties(Object bean, String beanName, BeanDefinition bd) {
		LinkedHashSet<InjectedElement> injectedElements = bd.getInjectedElements();
		for (InjectedElement injectedElement : injectedElements) {
			Field field = injectedElement.getField();
			if (field != null) {
				Object value = cachedValues.get(field);
				if (value == null) {
					DependencyDescriptor desc = new DependencyDescriptor(field, true);
					desc.setContainingClass(bean.getClass());
					Set<String> autowiredBeanNames = new LinkedHashSet<>(1);
					value = resolveDependency(desc, beanName, autowiredBeanNames);
				}
				if (value == null) {
					throw new SimpleException(
							"Injection of autowired dependency field  " + field.getName() + " failed");
				}
				field.setAccessible(true);
				try {
					field.set(bean, value);
				} catch (Throwable e) {
					throw new SimpleException("Injection of autowired dependency field" + field.getName() + " failed!",
							e);
				}
				cachedValues.put(field, value);
			}
		}

	}

	private void postProperties(String beanName, BeanDefinition mbd, Set<Map<String, Object>> pvs, Object bean) {
		Class<?> type = mbd.getBeanClass();
		for (Map<String, Object> map : pvs) {
			try {
				String name = (String) map.get(Constant.ATTR_NAME);
				Object value = factory.parseValue(beanName, map);
				Field field = type.getDeclaredField(name);
				value = factory.getConvertService().convert(value, field.getType());
				field.setAccessible(true);
				field.set(bean, value);
				log.debug("post propertiy " + name + " of " + beanName);
			} catch (Exception e) {
				log.error("postProperties failed!", e);
			}
		}
	}

	private Object resolveDependency(DependencyDescriptor descriptor, String requestingBeanName,
			Set<String> autowiredBeanNames) {
		if (AnnotationUtils.isLazy(descriptor)) {
			return buildLazyResolutionProxy(descriptor, requestingBeanName);
		} else {
			return factory.resolveDependency(descriptor, requestingBeanName, autowiredBeanNames);
		}
	}

	private Object buildLazyResolutionProxy(final DependencyDescriptor descriptor, final String beanName) {
		Supplier<?> target = new Supplier<Object>() {
			@Override
			public Object get() {
				Object target = factory.resolveDependency(descriptor, beanName, null);
				if (target == null) {
					Class<?> type = descriptor.getDependencyType();
					if (Map.class == type) {
						return Collections.emptyMap();
					} else if (List.class == type) {
						return Collections.emptyList();
					} else if (Set.class == type || Collection.class == type) {
						return Collections.emptySet();
					}
					throw new SimpleException(
							"Optional dependency not present for lazy injection point for bean " + beanName + "!");
				}
				return target;
			}
		};

		Class<?> dependencyType = descriptor.getDependencyType();
		return ProxyHelper.instance(new InvocationHandler() {
			@Override
			public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
				return target.get();
			}
		}, dependencyType, new Class<?>[0]);

	}


	@SuppressWarnings("unchecked")
	@Override
	public void produceWorkshop() {
		StoreRoom<BeanDefinition, Object[], Object> storeRoom =
				(StoreRoom<BeanDefinition, Object[], Object>) factory.currentStoreRoom.get();
		BeanDefinition mbd = storeRoom.getX();
		Object bean = storeRoom.getZ();
		String beanName = mbd.getBeanName();
		try {
			populateBean(beanName, mbd, bean);
			initializeBean(beanName, bean, mbd);
		} catch (Throwable ex) {
			log.info("Initialization of bean " + beanName + " failed");
		}
	}
	public Object initializeBean(final String beanName, final Object bean, BeanDefinition mbd) {
		Object wrappedBean = bean;
		if (mbd == null || !mbd.isSynthetic()) {
			wrappedBean = applyBeanProcessorsBeforeInitialization(wrappedBean, beanName);
		}
		try {
			invokeInitMethods(beanName, wrappedBean, mbd);
		} catch (Throwable ex) {
			throw new SimpleException("bean " + beanName + " defined at " + mbd.getResourceDescription()
					+ " invocate  init method failed!", ex);
		}
		if (mbd == null || !mbd.isSynthetic()) {
			wrappedBean = applyBeanProcessorsAfterInitialization(wrappedBean, beanName);
		}
		return wrappedBean;
	}
	private Object applyBeanProcessorsBeforeInitialization(Object existingBean, String beanName) {
		Object result = existingBean;
		for (Processor processor : factory.getProcessors()) {
			processor.postProcessBeforeInitialization(result, beanName);
		}
		return result;
	}
	
	private Object applyBeanProcessorsAfterInitialization(Object existingBean, String beanName) {
		Object result = existingBean;
		for (Processor processor : factory.getProcessors()) {
			processor.postProcessAfterInitialization(result, beanName);
		}
		return result;
	}
	
	private void invokeInitMethods(String beanName, final Object bean, BeanDefinition mbd) throws Throwable {

		boolean isMonitorBean = (bean instanceof BeanMonitor);
		if (isMonitorBean && (mbd == null || !mbd.isExternallyManagedInitMethod("afterPropertiesSet"))) {
			log.debug("Invoking afterPropertiesSet() on bean with name '" + beanName + "'");
			((BeanMonitor) bean).afterPropertiesSet();
		}
		if (mbd != null && bean != null) {
			String initMethodName = mbd.getInitMethodName();
			if (StringUtils.hasLength(initMethodName) && !mbd.isExternallyManagedInitMethod(initMethodName)) {
				invokeCustomInitMethod(beanName, bean, mbd);
			}
		}
	}
	
	private void invokeCustomInitMethod(String beanName, final Object bean, BeanDefinition mbd) throws Throwable {
		String initMethodName = mbd.getInitMethodName();
		final Method initMethod = BeanUtils.findMethod(bean.getClass(), initMethodName);
		if (initMethod == null) {
			if (mbd.isEnforceInitMethod()) {
				throw new SimpleException(
						"Could not find an init method named '" + initMethodName + "' on bean '" + beanName + "'");
			} else {
				log.info("No default init method named '" + initMethodName + "' found on bean  '" + beanName + "'");
				// Ignore non-existent default lifecycle methods.
				return;
			}
		}

		log.info("Invoking init method  '" + initMethodName + "' on bean with name '" + beanName + "'");

		if (System.getSecurityManager() != null) {
			AccessController.doPrivileged((PrivilegedAction<Object>) () -> {
				initMethod.setAccessible(true);
				return null;
			});
			try {
				AccessController.doPrivileged((PrivilegedExceptionAction<Object>) () -> initMethod.invoke(bean),
						factory.getAccessControlContext());
			} catch (PrivilegedActionException pae) {
				InvocationTargetException ex = (InvocationTargetException) pae.getException();
				throw ex.getTargetException();
			}
		} else {
			try {
				initMethod.setAccessible(true);
				initMethod.invoke(bean);
			} catch (InvocationTargetException ex) {
				throw ex.getTargetException();
			}
		}
	}
}
