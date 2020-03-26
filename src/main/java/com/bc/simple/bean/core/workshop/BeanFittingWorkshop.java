package com.bc.simple.bean.core.workshop;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.bc.simple.bean.BeanDefinition;
import com.bc.simple.bean.common.support.proxy.Proxy;
import com.bc.simple.bean.common.util.AnnotationUtils;
import com.bc.simple.bean.common.util.Constant;
import com.bc.simple.bean.core.AbstractBeanFactory;
import com.bc.simple.bean.core.processor.Processor;
import com.bc.simple.bean.core.support.DependencyDescriptor;
import com.bc.simple.bean.core.support.InjectedElement;
import com.bc.simple.bean.core.support.SimpleException;

public class BeanFittingWorkshop extends Workshop {

	private Log log = LogFactory.getLog(this.getClass());

	public BeanFittingWorkshop(AbstractBeanFactory factory) {
		super(factory);
	}

	@SuppressWarnings({"unchecked"}) // for postProcessPropertyValues
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
				Object value = factory.cachedValues.get(field);
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
				factory.cachedValues.put(field, value);
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
		return Proxy.instance(new InvocationHandler() {
			@Override
			public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
				return target.get();
			}
		}, dependencyType, new Class<?>[0]);

	}

	private Set<String> getRefProperties(BeanDefinition mbd, Object bean) {
		Set<String> result = new TreeSet<>();
		Set<Map<String, Object>> pvs = mbd.getPropertyValues();
		for (Map<String, Object> map : pvs) {
			String name = (String) map.get(Constant.ATTR_NAME);
			Object value = map.get(Constant.ATTR_VALUE);
			String propertyType = (String) map.get(Constant.ATTR_TYPE);
			if (propertyType.equals(Constant.TYPE_REF_VALUE)) {
				result.add(name);
			}
		}
		return result;
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
			factory.initializeBean(beanName, bean, mbd);
		} catch (Throwable ex) {
			log.info("Initialization of bean " + beanName + " failed");
		}
	}
}
