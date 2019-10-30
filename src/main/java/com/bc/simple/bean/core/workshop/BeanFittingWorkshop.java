package com.bc.simple.bean.core.workshop;

import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
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
import com.bc.simple.bean.common.support.cglib.CglibProxy;
import com.bc.simple.bean.common.util.AnnotationUtils;
import com.bc.simple.bean.common.util.BeanUtils;
import com.bc.simple.bean.common.util.Constant;
import com.bc.simple.bean.core.AbstractBeanFactory;
import com.bc.simple.bean.core.processor.Processor;
import com.bc.simple.bean.core.support.CurrencyException;
import com.bc.simple.bean.core.support.DependencyDescriptor;
import com.bc.simple.bean.core.support.InjectedElement;

public class BeanFittingWorkshop extends Workshop {

	private Log log = LogFactory.getLog(this.getClass());

	public BeanFittingWorkshop(AbstractBeanFactory factory) {
		super(factory);
	}


	@SuppressWarnings({ "unchecked" }) // for postProcessPropertyValues
	public void populateBean(String beanName, BeanDefinition mbd, Object bean) {

		if (!mbd.isSynthetic() && factory.hasInstantiationAwareBeanProcessors()) {
			for (Processor bp : factory.getProcessors()) {
				bp.postProcessAfterInstantiation(bean, beanName);
			}
		}

		Set<Map<String, Object>> pvs = (mbd.hasPropertyValues() ? mbd.getPropertyValues() : null);

		if (mbd.getAutowireMode() == BeanDefinition.AUTOWIRE_BY_NAME) {
			autowireByName(beanName, mbd, pvs, bean);
		}
		// Add property values based on autowire by type if applicable.
		if (mbd.getAutowireMode() == BeanDefinition.AUTOWIRE_BY_TYPE) {
			autowireByType(beanName, mbd, pvs, bean);
		}

		boolean hasInstAwareBpps = factory.hasInstantiationAwareBeanProcessors();
		PropertyDescriptor[] filteredPds = null;
		if (hasInstAwareBpps) {
			if (pvs == null) {
				pvs = mbd.getPropertyValues();
			}
			Set<Map<String, Object>> pvsToUse = (Set<Map<String, Object>>) postProcessProperties(pvs, bean, beanName,
					mbd);
			if (filteredPds == null) {
				filteredPds = BeanUtils.skipException(() -> {
					return Introspector.getBeanInfo(bean.getClass()).getPropertyDescriptors();
				});
			}
			pvs = pvsToUse;
		}
	}

	private Object postProcessProperties(Object pvs, Object bean, String beanName, BeanDefinition bd) {
		LinkedHashSet<InjectedElement> injectedElements = bd.getInjectedElements();
		for (InjectedElement injectedElement : injectedElements) {
			Field field = injectedElement.getField();
			if (field != null) {
				Object value = factory.cachedValues.get(field);
				if (value == null) {
					DependencyDescriptor desc = new DependencyDescriptor(field, true);
					desc.setContainingClass(bean.getClass());
					Set<String> autowiredBeanNames = new LinkedHashSet<>(1);
					value = factory.resolveDependency(desc, beanName, autowiredBeanNames);
				}
				if (value == null) {
					throw new CurrencyException(
							"Injection of autowired dependency field  " + field.getName() + " failed");
				}
				field.setAccessible(true);
				try {
					field.set(bean, value);
				} catch (Throwable e) {
					throw new CurrencyException("Injection of autowired dependency field" + field.getName() + " failed",
							e);
				}
				factory.cachedValues.put(field, value);
			}
		}
		return pvs;
	}


	private void autowireByName(String beanName, BeanDefinition mbd, Set<Map<String, Object>> pvs, Object bean) {
		Set<String> propertyNames = getRefProperties(mbd, bean);
		Class<?> type = mbd.getBeanClass();
		for (String propertyName : propertyNames) {
			if (factory.containsBean(propertyName)) {
				try {
					Object prop = factory.getBean(propertyName);
					PropertyDescriptor pd = new PropertyDescriptor(propertyName, type);
					prop = factory.getConvertService().convert(prop, pd.getPropertyType());
					pd.createPropertyEditor(bean).setValue(prop);
					factory.registerDependentBean(propertyName, beanName);
					log.info("Added autowiring by name from bean name '" + beanName + "' via property '"
							+ propertyName + "' to bean named '" + propertyName + "'");
				} catch (IntrospectionException e) {
					log.info("autowireByName failed!", e);
				}
			} else {
				log.info("Not autowiring property '" + propertyName + "' of bean '" + beanName
						+ "' by name: no matching bean found");
			}
		}
	}


	private void autowireByType(String beanName, BeanDefinition mbd, Set<Map<String, Object>> pvs, Object bean) {

		Set<String> autowiredBeanNames = new LinkedHashSet<>(4);
		Set<String> propertyNames = getRefProperties(mbd, bean);
		Class<?> type = mbd.getBeanClass();
		for (String propertyName : propertyNames) {
			try {
				PropertyDescriptor pd = new PropertyDescriptor(propertyName, type);
				Method writeMethod = pd.getWriteMethod();
				// Do not allow eager init for type matching in case of a prioritized
				// post-processor.
				Parameter[] params = writeMethod.getParameters();
				Object[] args = new Object[params.length];
				for (int i = 0; i < args.length; i++) {
					args[i] = resolveDependency(new DependencyDescriptor(params[i], true), beanName,
							autowiredBeanNames);
				}
				writeMethod.invoke(bean, args);
				for (String autowiredBeanName : autowiredBeanNames) {
					factory.registerDependentBean(autowiredBeanName, beanName);
					log.info("Autowiring by type from bean name '" + beanName + "' via property '"
							+ propertyName + "' to bean named '" + autowiredBeanName + "'");
				}
				autowiredBeanNames.clear();
			} catch (Exception ex) {
				throw new CurrencyException(mbd.getResourceDescription(), beanName, propertyName, ex);
			}
		}

	}


	public Object resolveDependency(DependencyDescriptor descriptor, String requestingBeanName,
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
					throw new CurrencyException(descriptor.getResolvableType(),
							"Optional dependency not present for lazy injection point");
				}
				return target;
			}
		};

		Class<?> dependencyType = descriptor.getDependencyType();
		return CglibProxy.getProxyInstance(new InvocationHandler() {
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
			if (value instanceof Map) {
				@SuppressWarnings("unchecked")
				Map<String, Object> v = (Map<String, Object>) value;
				String propertyType = (String) v.get(Constant.ATTR_PROPERTY_TYPE);
				if (propertyType.equals(Constant.TYPE_REF_VALUE)) {
					result.add(name);
				}
			}
		}
		return result;
	}

	@SuppressWarnings("unchecked")
	@Override
	public void produceWorkshop() {
		StoreRoom<BeanDefinition, Object[], Object> storeRoom = (StoreRoom<BeanDefinition, Object[], Object>) factory.currentStoreRoom.get();
		BeanDefinition mbd = storeRoom.getX();
		Object bean =storeRoom.getZ();
		String beanName = mbd.getBeanName();
		try {
			populateBean(beanName, mbd, bean);
			factory.initializeBean(beanName, bean, mbd);
		} catch (Throwable ex) {
			ex.printStackTrace();
			log.info(mbd.getResourceDescription() + " " + beanName + " " + "Initialization of bean failed");
		}
	}
}
