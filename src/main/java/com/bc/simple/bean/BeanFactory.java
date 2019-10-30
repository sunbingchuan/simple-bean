package com.bc.simple.bean;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.bc.simple.bean.common.util.StringUtils;
import com.bc.simple.bean.core.AbstractBeanFactory;
import com.bc.simple.bean.core.processor.Processor;
import com.bc.simple.bean.core.support.CurrencyException;

public class BeanFactory extends AbstractBeanFactory {

	private Log log = LogFactory.getLog(this.getClass());

	public BeanFactory() {
		super();
	}

	/** List of bean definition names, in registration order. */
	private volatile List<String> beanDefinitionNames = new ArrayList<>(256);


	private boolean allowBeanDefinitionOverriding = true;


	private final Set<Class<?>> ignoredDependencyInterfaces = new HashSet<>();

	private final List<Processor> processors = new ArrayList<>();

	private ApplicationContext context;


	@Override
	public Object getBean(String name) {
		return doGetBean(name, null, null, false);
	}


	@Override
	public <T> T getBean(String name, Class<T> requiredType) {
		return doGetBean(name, requiredType, null, false);
	}


	@Override
	public Object getBean(String name, Object... args) {
		return doGetBean(name, null, args, false);
	}


	public <T> T getBean(Class<T> requiredType) {
		return doGetBean(null, requiredType, null, false);
	}


	public <T> T getBean(Class<T> requiredType, Object... args) {
		return doGetBean(null, requiredType, args, false);
	}


	@SuppressWarnings("unchecked")
	@Override
	public <T> T doGetBean(final String name, final Class<T> requiredType, final Object[] args, boolean typeCheckOnly) {

		String beanName = name;
		Object bean = null;
		// Eagerly check singleton cache for manually registered singletons.
		// no definition?
		if (StringUtils.isEmpty(beanName) && StringUtils.isEmpty(beanName = findBeanNameByType(requiredType))) {
			throw new CurrencyException(
					"no bean definetion of bean which name is " + name + " and type is " + requiredType);
		}
		bean = getSingleton(beanName);
		if (bean == null) {
			if (isPrototypeCurrentlyInCreation(beanName) || isSingletonCurrentlyInCreation(beanName)) {
				throw new CurrencyException(beanName + "is in creation!");
			}
			if (!typeCheckOnly && beanName != null) {
				markBeanAsCreated(beanName);
			}

			try {
				final BeanDefinition mbd = getBeanDefinition(beanName);

				// Guarantee initialization of beans that the current bean depends on.
				String[] dependsOn = mbd.getDependsOn();
				if (dependsOn != null) {
					for (String dep : dependsOn) {
						if (isDependent(beanName, dep)) {
							throw new CurrencyException(mbd.getResourceDescription(), beanName,
									"Circular depends-on relationship between '" + beanName + "' and '" + dep + "'");
						}
						registerDependentBean(dep, beanName);
						try {
							getBean(dep);
						} catch (Exception ex) {
							throw new CurrencyException(mbd.getResourceDescription(), beanName,
									"'" + beanName + "' depends on missing bean '" + dep + "'", ex);
						}
					}
				}

				// Create bean instance.
				if (mbd.isSingleton()) {
					bean = getSingleton(beanName);
					if (bean == null) {
						try {
							beforeSingletonCreation(beanName);
							bean = doCreateBean(beanName, mbd, args);
							afterSingletonCreation(beanName);
						} catch (Exception e) {
							destroySingleton(beanName);
						}
					}
				}

				else if (mbd.isPrototype()) {
					try {
						beforePrototypeCreation(beanName);
						bean = doCreateBean(beanName, mbd, args);
					} finally {
						afterPrototypeCreation(beanName);
					}
				} else {
					// ignore
				}
			} catch (Exception ex) {
				cleanupAfterBeanCreationFailure(beanName);
				throw ex;
			}
		}

		// Check if required type matches the type of the actual bean instance.
		if (requiredType != null && !requiredType.isInstance(bean)) {
			try {
				T convertedBean = (T) this.convertService.convert(bean, requiredType);
				return convertedBean;
			} catch (Exception ex) {
				log.info("Failed to convert bean '" + name + "' to required type '" + requiredType + "'", ex);
				throw new CurrencyException(name, requiredType, bean.getClass());
			}
		}
		return (T) bean;

	}

	public String findBeanNameByType(Class<?> requiredType) {
		if (requiredType != null) {
			List<String> candidates = doGetBeanNamesForType(requiredType);
			if (candidates != null && candidates.size() > 0) {
				return candidates.get(0);
			}
		}
		return null;
	}


	@Override
	public boolean containsBean(String name) {
		return beanDefinitionNames.contains(name);
	}


	@Override
	public boolean containsBean(Class<?> type) {
		for (BeanDefinition bdf : beanDefinitionMap.values()) {
			if (type.isAssignableFrom(bdf.getClass())) {
				return true;
			}
		}
		return false;
	}


	boolean isSingleton(String name) {
		return false;
	}


	boolean isPrototype(String name) {
		return false;
	}


	boolean isTypeMatch(String name, Class<?> typeToMatch) {
		BeanDefinition mbd = beanDefinitionMap.get(name);
		if (mbd != null) {
			if (mbd.getBeanClass().equals(typeToMatch)) {
				return true;
			}
		}
		return false;
	}


	Class<?> getType(String name) {
		return null;
	}


	String[] getAliases(String name) {
		return null;
	}

	@Override
	public <T> Map<String, T> getBeansOfType(Class<T> type) {
		return getBeansOfType(type, false, false);
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T> Map<String, T> getBeansOfType(Class<T> type, boolean includeNonSingletons, boolean allowEagerInit) {

		List<String> beanNames = doGetBeanNamesForType(type);
		Map<String, T> result = new LinkedHashMap<>(beanNames.size());
		for (String beanName : beanNames) {
			try {
				Object beanInstance = getBean(beanName);
				result.put(beanName, (T) beanInstance);
			} catch (Exception ex) {
				// just ignore
			}
		}
		return result;
	}

	@Override
	public List<String> doGetBeanNamesForType(Class<?> type) {
		List<String> result = new ArrayList<>();

		// Check all bean definitions.
		for (String beanName : this.beanDefinitionNames) {
			boolean matchFound = isTypeMatch(beanName, type);
			if (matchFound) {
				result.add(beanName);
			}

		}
		return result;
	}


	void ignoreDependencyType(Class<?> type) {
	}


	void ignoreDependencyInterface(Class<?> ifc) {
		this.ignoredDependencyInterfaces.add(ifc);
	}



	public void registerResolvableDependency(Class<?> dependencyType, Object autowiredValue) {
		if (autowiredValue != null) {
			if (!dependencyType.isInstance(autowiredValue)) {
				throw new IllegalArgumentException("Value [" + autowiredValue
						+ "] does not implement specified dependency type [" + dependencyType.getName() + "]");
			}
			this.resolvableDependencies.put(dependencyType, autowiredValue);
		}
	}


	boolean isAutowireCandidate(String beanName, BeanDefinition bd) {
		return false;
	}


	@Override
	public BeanDefinition getBeanDefinition(String name) {
		String beanName = canonicalName(name.replaceAll("&", ""));
		BeanDefinition bd = this.beanDefinitionMap.get(beanName);
		if (bd == null)
			throw new CurrencyException("no bean " + beanName + " defined!");
		// Set default singleton scope, if not configured before.
		if (!StringUtils.hasLength(bd.getScope())) {
			bd.setScope(BeanDefinition.SCOPE_SINGLETON);
		}
		return bd;
	}


	Iterator<String> getBeanNamesIterator() {
		return null;
	}


	public void clearMetadataCache() {

	}


	void freezeConfiguration() {

	}


	boolean isConfigurationFrozen() {
		return false;
	}


	void preInstantiateSingletons() {
	}

	public void addProcessor(Processor Processor) {
		this.processors.remove(Processor);
		this.processors.add(Processor);
	}

	public void destroySingletons() {
		this.beanDefinitionMap.clear();
		this.beanDefinitionNames.clear();
		this.singletonObjects.clear();
		this.resolvableDependencies.clear();
		this.ignoredDependencyInterfaces.clear();
		this.processors.clear();
	}

	@Override
	public List<Processor> getProcessors() {
		return processors;
	}


	public void registerAlias(String name, String alias) {
		synchronized (this.aliasMap) {
			if (alias.equals(name)) {
				this.aliasMap.remove(alias);
			} else {
				String registeredName = this.aliasMap.get(alias);
				if (registeredName != null) {
					if (registeredName.equals(name)) {
						// An existing alias - no need to re-register
						return;
					}
				}
				this.aliasMap.put(alias, name);
			}
		}
	}

	public void registerBeanDefinition(String beanName, BeanDefinition beanDefinition) {

		BeanDefinition existingDefinition = this.beanDefinitionMap.get(beanName);
		if (existingDefinition != null) {
			if (!isAllowBeanDefinitionOverriding()) {
				throw new CurrencyException(beanName + "beanDefinition exist current = " + beanDefinition
						+ ",to be added " + existingDefinition);
			} else if (existingDefinition.getRole() < beanDefinition.getRole()) {
				// e.g. was ROLE_APPLICATION, now overriding with ROLE_SUPPORT or
				// ROLE_INFRASTRUCTURE
				// ig
			} else if (!beanDefinition.equals(existingDefinition)) {
				// ig
			} else {
				//
			}
			this.beanDefinitionMap.put(beanName, beanDefinition);
		} else {
			if (getAlreadyCreated().contains(beanName)) {
				// Cannot modify startup-time collection elements anymore (for stable iteration)
				synchronized (this.beanDefinitionMap) {
					this.beanDefinitionMap.put(beanName, beanDefinition);
					List<String> updatedDefinitions = new ArrayList<>(this.beanDefinitionNames.size() + 1);
					updatedDefinitions.addAll(this.beanDefinitionNames);
					updatedDefinitions.add(beanName);
					this.beanDefinitionNames = updatedDefinitions;
				}
			} else {
				// Still in startup registration phase
				this.beanDefinitionMap.put(beanName, beanDefinition);
				this.beanDefinitionNames.add(beanName);
			}
		}

		if (existingDefinition != null || containsSingleton(beanName)) {
			resetBeanDefinition(beanName);
		}
	}


	protected void resetBeanDefinition(String beanName) {

		// Remove corresponding bean from singleton cache, if any. Shouldn't usually
		// be necessary, rather just meant for overriding a context's default beans
		// (e.g. the default StaticMessageSource in a StaticApplicationContext).
		destroySingleton(beanName);

		// Notify all post-processors that the specified bean definition has been reset.
		for (Processor processor : getProcessors()) {
			processor.resetBeanDefinition(beanName);
		}

	}


	@Override
	public void destroySingleton(String beanName) {
		// Remove a registered singleton of the given name, if any.
		removeSingleton(beanName);
	}


	protected void removeSingleton(String beanName) {
		synchronized (this.singletonObjects) {
			this.singletonObjects.remove(beanName);
		}
	}


	@Override
	public boolean containsBeanDefinition(String beanName) {
		return this.beanDefinitionMap.containsKey(beanName);
	}

	public int getBeanDefinitionCount() {
		return this.beanDefinitionMap.size();
	}

	@Override
	public String[] getBeanDefinitionNames() {
		return this.beanDefinitionNames.toArray(new String[0]);
	}

	@Override
	public String[] getBeanNamesForType(Class<?> type) {
		return (String[]) getBeansOfType(type).keySet().toArray();
	}

	@Override
	public String[] getBeanNamesForType(Class<?> type, boolean includeNonSingletons, boolean allowEagerInit) {
		return getBeansOfType(type, includeNonSingletons, allowEagerInit).keySet().toArray(new String[0]);
	}

	public boolean isAllowBeanDefinitionOverriding() {
		return allowBeanDefinitionOverriding;
	}

	public void setAllowBeanDefinitionOverriding(boolean allowBeanDefinitionOverriding) {
		this.allowBeanDefinitionOverriding = allowBeanDefinitionOverriding;
	}

	public Set<String> getAlreadyCreated() {
		return alreadyCreated;
	}

	@Override
	public boolean containsSingleton(String beanName) {
		return this.singletonObjects.containsKey(beanName);
	}

	public boolean isBeanNameInUse(String beanClassName) {
		return this.aliasMap.containsKey(beanClassName) && this.beanDefinitionMap.containsKey(beanClassName);
	}


	@Override
	public Object getSingleton(String beanName) {
		return this.singletonObjects.get(beanName);
	}

	@Override
	public void addSingleton(String beanName,Object bean) {
		 this.singletonObjects.put(beanName,bean);
	}


	

	@Override
	protected boolean removeSingletonIfCreatedForTypeCheckOnly(String beanName) {
		if (!this.alreadyCreated.contains(beanName)) {
			removeSingleton(beanName);
			return true;
		} else {
			return false;
		}
	}

	@Override
	public <T> T getBean(String name, Class<T> requiredType, Object... args) {
		return doGetBean(name, requiredType, args, false);
	}


	protected void markBeanAsCreated(String beanName) {
		if (!this.alreadyCreated.contains(beanName)) {
			synchronized (this.beanDefinitionMap) {
				if (!this.alreadyCreated.contains(beanName)) {
					// Let the bean definition get re-merged now that we're actually creating
					// the bean... just in case some of its metadata changed in the meantime.
					this.alreadyCreated.add(beanName);
				}
			}
		}
	}

	@Override
	public ApplicationContext getContext() {
		return context;
	}

	public void setContext(ApplicationContext context) {
		this.context = context;
	}

	public void destroy() {

	}

}
