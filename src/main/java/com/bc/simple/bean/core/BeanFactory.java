package com.bc.simple.bean.core;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.bc.simple.bean.ApplicationContext;
import com.bc.simple.bean.BeanDefinition;
import com.bc.simple.bean.common.annotation.Qualifier;
import com.bc.simple.bean.common.annotation.Value;
import com.bc.simple.bean.common.support.ConvertService;
import com.bc.simple.bean.common.support.ConvertService.Converter;
import com.bc.simple.bean.common.util.BeanUtils;
import com.bc.simple.bean.common.util.StringUtils;
import com.bc.simple.bean.core.assembler.Assembler;
import com.bc.simple.bean.core.processor.Processor;
import com.bc.simple.bean.core.support.BeanMonitor;
import com.bc.simple.bean.core.support.DependencyDescriptor;
import com.bc.simple.bean.core.support.SimpleException;
import com.bc.simple.bean.core.workshop.LargeStoreRoom;
import com.bc.simple.bean.core.workshop.StoreRoom;

public  class BeanFactory extends Factory {

	private Log log = LogFactory.getLog(this.getClass());

	/** ClassLoader to resolve bean class names with, if necessary. */
	protected ClassLoader beanClassLoader = BeanUtils.getDefaultClassLoader();

	/** Security context used when running with a SecurityManager. */
	protected AccessControlContext accessControlContext;

	/** Names of beans that have already been created at least once. */
	protected final Set<String> alreadyCreated = Collections.newSetFromMap(new ConcurrentHashMap<>(256));

	/** Map from alias to canonical name. */
	protected final Map<String, String> aliasMap = new ConcurrentHashMap<>(16);

	protected final Map<String, BeanDefinition> beanDefinitionMap = new ConcurrentHashMap<>(256);

	protected boolean hasInstantiationAwareBeanProcessors = false;

	/** Names of beans that are currently in creation. */
	protected final Set<String> singletonsCurrentlyInCreation = Collections.newSetFromMap(new ConcurrentHashMap<>(16));

	/** Names of beans that are currently in creation. */
	private final ThreadLocal<Object> prototypesCurrentlyInCreation = new ThreadLocal<>();

	protected final Map<String, Set<String>> dependentBeanMap = new ConcurrentHashMap<>(64);

	/** Map from dependency type to corresponding autowired value. */
	public final Map<Class<?>, Object> resolvableDependencies = new ConcurrentHashMap<>(16);

	/** Optional OrderComparator for dependency Lists and arrays. */
	private Comparator<Object> dependencyComparator;

	protected final ConvertService convertService = new ConvertService();

	public static final Object[] EMPTY_ARGS = new Object[0];


	protected Class<? extends Annotation> valueAnnotationType = Value.class;

	/** Whether to cache bean metadata or rather reobtain it for every access. */
	protected boolean cacheBeanMetadata = true;

	/** List of suppressed Exceptions, available for associating related causes. */
	private Set<Exception> suppressedExceptions;

	/** Cache of singleton objects: bean name to bean instance. */
	protected final Map<String, Object> singletonObjects = new ConcurrentHashMap<>(256);

	/** List of bean definition names, in registration order. */
	protected volatile List<String> beanDefinitionNames = new ArrayList<>(256);

	protected boolean allowBeanDefinitionOverriding = true;

	protected final List<Processor> processors = new ArrayList<>();

	protected ApplicationContext context;
	
	public final ThreadLocal<StoreRoom<?, ?, ?>> currentStoreRoom = new ThreadLocal<>();

	private Assembler<BeanFactory> getBeanAssembler;
	private Assembler<BeanFactory> dependencyResolveAssembler;
	private Assembler<BeanFactory> parserValueAssembler;

	

	public BeanFactory() {
	}

	

	
	
	protected Object doCreateBean(final String beanName, final BeanDefinition mbd, final Object[] args) {
		assertAssembler(this.getBeanAssembler);
		StoreRoom<?, ?, ?> previousStoreRoom = currentStoreRoom.get();
		Object bean = null;
		try {
			currentStoreRoom.set(new StoreRoom<BeanDefinition, Object[], Object>(mbd, args, null));
			this.getBeanAssembler.work();
			bean = currentStoreRoom.get().getZ();
		} finally {
			currentStoreRoom.set(previousStoreRoom);
		}
		return bean;
	}

	public boolean isSingletonCurrentlyInCreation(String beanName) {
		return this.singletonsCurrentlyInCreation.contains(beanName);
	}
	

	protected boolean hasDependentBean(String beanName) {
		return this.dependentBeanMap.containsKey(beanName);
	}

	public String[] getDependentBeans(String beanName) {
		Set<String> dependentBeans = this.dependentBeanMap.get(beanName);
		if (dependentBeans == null) {
			return new String[0];
		}
		synchronized (this.dependentBeanMap) {
			return dependentBeans.toArray(new String[0]);
		}
	}

	public void registerMonitorBean(String beanName, Object bean, BeanDefinition mbd) {
		if (bean instanceof BeanMonitor) {
			mbd.registerMonitor((BeanMonitor) bean);
		}
	}

	public ConvertService getConvertService() {
		return convertService;
	}

	public void addConver(Converter<?, ?> converter) {
		this.convertService.addConverter(converter);
	}

	public Constructor<?>[] determineConstructorsFromBeanProcessors(Class<?> beanClass) {
		Constructor<?>[] result = null;
		if (beanClass != null && hasInstantiationAwareBeanProcessors()) {
			for (Processor processor : getProcessors()) {
				result = processor.determineConstructors(beanClass);
			}
		}
		return result;
	}


	public boolean hasInstantiationAwareBeanProcessors() {
		return this.hasInstantiationAwareBeanProcessors;
	}

	public void setHasInstantiationAwareBeanProcessors(boolean hasInstantiationAwareBeanProcessors) {
		this.hasInstantiationAwareBeanProcessors = hasInstantiationAwareBeanProcessors;
	}

	public AccessControlContext getAccessControlContext() {
		return (this.accessControlContext != null ? this.accessControlContext : AccessController.getContext());
	}

	public ClassLoader getBeanClassLoader() {
		return this.beanClassLoader;
	}

	public void registerDependentBean(String beanName, String dependentBeanName) {
		String canonicalName = canonicalName(beanName);
		synchronized (this.dependentBeanMap) {
			Set<String> dependentBeans =
					this.dependentBeanMap.computeIfAbsent(canonicalName, k -> new LinkedHashSet<>(8));
			if (!dependentBeans.add(dependentBeanName)) {
				return;
			}
		}
	}

	public String canonicalName(String name) {
		String canonicalName = name;
		// Handle aliasing...
		String resolvedName;
		do {
			resolvedName = this.aliasMap.get(canonicalName);
			if (resolvedName != null) {
				canonicalName = resolvedName;
			}
		} while (resolvedName != null);
		return canonicalName;
	}


	public boolean isCacheBeanMetadata() {
		return cacheBeanMetadata;
	}

	public void setCacheBeanMetadata(boolean cacheBeanMetadata) {
		this.cacheBeanMetadata = cacheBeanMetadata;
	}

	public Comparator<Object> getDependencyComparator() {
		return dependencyComparator;
	}

	public void setDependencyComparator(Comparator<Object> dependencyComparator) {
		this.dependencyComparator = dependencyComparator;
	}

	

	public boolean isAutowireCandidate(String beanName, DependencyDescriptor descriptor) {
		BeanDefinition bd = null;
		boolean match = (bd = getBeanDefinition(beanName)) != null ? bd.isAutowireCandidate() : false;
		if (match) {
			Qualifier qualifier = descriptor.getAnnotation(Qualifier.class);
			if (qualifier != null) {
				match = qualifier.value().equals(bd.getQualifier(Qualifier.class.getCanonicalName()));
			}
		}
		return match;
	}

	protected boolean isPrototypeCurrentlyInCreation(String beanName) {
		Object curVal = this.prototypesCurrentlyInCreation.get();
		return (curVal != null
				&& (curVal.equals(beanName) || (curVal instanceof Set && ((Set<?>) curVal).contains(beanName))));
	}



	protected boolean isDependent(String beanName, String dependentBeanName) {
		synchronized (this.dependentBeanMap) {
			return isDependent(beanName, dependentBeanName, null);
		}
	}

	private boolean isDependent(String beanName, String dependentBeanName, Set<String> alreadySeen) {
		if (alreadySeen != null && alreadySeen.contains(beanName)) {
			return false;
		}
		String canonicalName = canonicalName(beanName);
		Set<String> dependentBeans = this.dependentBeanMap.get(canonicalName);
		if (dependentBeans == null) {
			return false;
		}
		if (dependentBeans.contains(dependentBeanName)) {
			return true;
		}
		for (String transitiveDependency : dependentBeans) {
			if (alreadySeen == null) {
				alreadySeen = new HashSet<>();
			}
			alreadySeen.add(beanName);
			if (isDependent(transitiveDependency, dependentBeanName, alreadySeen)) {
				return true;
			}
		}
		return false;
	}

	protected void beforeSingletonCreation(String beanName) {
		if (!this.singletonsCurrentlyInCreation.add(beanName)) {
			throw new IllegalStateException(beanName);
		}
	}

	protected void afterSingletonCreation(String beanName) {
		if (!this.singletonsCurrentlyInCreation.remove(beanName)) {
			throw new IllegalStateException("Singleton '" + beanName + "' isn't currently in creation");
		}
	}

	

	@SuppressWarnings("unchecked")
	protected void beforePrototypeCreation(String beanName) {
		Object curVal = this.prototypesCurrentlyInCreation.get();
		if (curVal == null) {
			this.prototypesCurrentlyInCreation.set(beanName);
		} else if (curVal instanceof String) {
			Set<String> beanNameSet = new HashSet<>(2);
			beanNameSet.add((String) curVal);
			beanNameSet.add(beanName);
			this.prototypesCurrentlyInCreation.set(beanNameSet);
		} else {
			Set<String> beanNameSet = (Set<String>) curVal;
			beanNameSet.add(beanName);
		}
	}

	@SuppressWarnings("unchecked")
	protected void afterPrototypeCreation(String beanName) {
		Object curVal = this.prototypesCurrentlyInCreation.get();
		if (curVal instanceof String) {
			this.prototypesCurrentlyInCreation.remove();
		} else if (curVal instanceof Set) {
			Set<String> beanNameSet = (Set<String>) curVal;
			beanNameSet.remove(beanName);
			if (beanNameSet.isEmpty()) {
				this.prototypesCurrentlyInCreation.remove();
			}
		}
	}

	protected void cleanupAfterBeanCreationFailure(String beanName) {
		synchronized (this.beanDefinitionMap) {
			this.alreadyCreated.remove(beanName);
		}
	}

	public boolean indicatesMultipleBeans(Class<?> type) {
		return (type.isArray() || (type.isInterface()
				&& (Collection.class.isAssignableFrom(type) || Map.class.isAssignableFrom(type))));
	}

	public boolean isPrimary(String beanName, Object beanInstance) {
		if (containsBeanDefinition(beanName)) {
			return getBeanDefinition(beanName).isPrimary();
		}
		return false;
	}

	public Collection<String> getAliasesArray(String name) {
		BeanDefinition bd = getBeanDefinition(name);
		if (bd == null) {
			return Collections.emptySet();
		}
		String[] aliases = bd.getAliases();
		if (aliases == null) {
			return Collections.emptySet();
		}
		return Arrays.asList(getBeanDefinition(name).getAliases());
	}

	public void onSuppressedException(Exception ex) {
		if (this.suppressedExceptions != null) {
			this.suppressedExceptions.add(ex);
		}
	}

	public void cacheConstructorAndArgs(BeanDefinition mbd, Executable constructor, Object[] args) {
		synchronized (mbd.buildArgumentLock) {
			mbd.buildMethod = constructor;
			mbd.buildArgumentsResolved = true;
			mbd.resolvedBuildArguments = args;
		}
	}

	@SuppressWarnings("unchecked")
	public Object resolveDependency(DependencyDescriptor dependencyDescriptor, String beanName,
			Set<String> autowiredBeanNames) {
		assertAssembler(this.dependencyResolveAssembler);
		StoreRoom<?, ?, ?> previousStoreRoom = currentStoreRoom.get();
		Object bean=null;
		try {
			currentStoreRoom.set(new LargeStoreRoom<DependencyDescriptor, String, Object, Set<String>, Object>(
					dependencyDescriptor, beanName, autowiredBeanNames, null, null));
			this.dependencyResolveAssembler.work();
			LargeStoreRoom<DependencyDescriptor, String, Set<String>, Object, Object> storeRoom =
					(LargeStoreRoom<DependencyDescriptor, String, Set<String>, Object, Object>) currentStoreRoom.get();
			bean = storeRoom.getM();
		} finally{
			currentStoreRoom.set(previousStoreRoom);
		}
		return bean;
	}

	public Set<BeanMonitor> getBeanMonitor(String beanName) {
		BeanDefinition bd = getBeanDefinition(beanName);
		if (bd == null) {
			return null;
		}
		return bd.getMonitoredBeans();
	}

	
	
	public Object parseValue(String createdBeanName, Map<String, Object> define) {
		assertAssembler(this.parserValueAssembler);
		StoreRoom<?, ?, ?> previousStoreRoom = currentStoreRoom.get();
		Object bean = null;
		try {
			currentStoreRoom.set(new StoreRoom<String, Map<String, Object>, Object>(createdBeanName, define, null));
			this.parserValueAssembler.work();
			bean = currentStoreRoom.get().getZ();
		} finally {
			currentStoreRoom.set(previousStoreRoom);
		}
		return bean;
	}
	public void setAccessControlContext(AccessControlContext accessControlContext) {
		this.accessControlContext = accessControlContext;
	}

	private void assertAssembler(Assembler<?extends Factory> assembler){
		if (assembler==null) {
			throw new SimpleException("assembler isn’t configed！");
		}
	}
	
	public String[] getAliases(String name) {
		if (StringUtils.isEmpty(name)) {
			throw new SimpleException("bean name should not be empty!");
		}
		List<String> aliases = new ArrayList<>();
		for (Entry<String, String> entry : this.aliasMap.entrySet()) {
			if (entry.getValue().equals(name)) {
				aliases.add(entry.getKey());
			}
		}
		return aliases.toArray(new String[aliases.size()]);
	}
	
	protected void markBeanAsCreated(String beanName) {
		if (!this.alreadyCreated.contains(beanName)) {
			synchronized (this.beanDefinitionMap) {
				if (!this.alreadyCreated.contains(beanName)) {
					this.alreadyCreated.add(beanName);
				}
			}
		}
	}
	

	
	public Object getBean(String name) {
		return doGetBean(name, null, null, false);
	}

	
	public <T> T getBean(String name, Class<T> requiredType) {
		return doGetBean(name, requiredType, null, false);
	}

	
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
	
	protected <T> T doGetBean(final String name, final Class<T> requiredType, final Object[] args, boolean typeCheckOnly) {
		String beanName = null;
		if (StringUtils.isNotEmpty(name)) {
			beanName = canonicalName(name);
		}
		Object bean = null;
		// Eagerly check singleton cache for manually registered singletons.
		// no definition?
		if (StringUtils.isEmpty(beanName) && StringUtils.isEmpty(beanName = findBeanNameByType(requiredType))) {
			throw new SimpleException(
					"no bean definition of bean which name is " + beanName + " and type is " + requiredType);
		}
		bean = getSingleton(beanName);
		if (bean == null) {
			if (isPrototypeCurrentlyInCreation(beanName) || isSingletonCurrentlyInCreation(beanName)) {
				throw new SimpleException(beanName + " is in creation!");
			}
			if (!typeCheckOnly && beanName != null) {
				markBeanAsCreated(beanName);
			}
			try {
				final BeanDefinition mbd = getBeanDefinition(beanName);
				// Guarantee initialization of beans that the current bean depends on.
				dealDependsOn(mbd);
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
							throw new SimpleException("bean " + beanName + " 创建失败", e);
						}
					}
				} else if (mbd.isPrototype()) {
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
				throw new SimpleException(name, requiredType, bean.getClass());
			}
		}
		return (T) bean;

	}

	private void dealDependsOn(BeanDefinition mbd) {
		String[] dependsOn = mbd.getDependsOn();
		if (dependsOn != null) {
			for (String dep : dependsOn) {
				if (isDependent(mbd.getBeanName(), dep)) {
					throw new SimpleException(mbd.getResourceDescription(), mbd.getBeanName(),
							"Circular depends-on relationship between '" + mbd.getBeanName() + "' and '" + dep + "'");
				}
				registerDependentBean(dep, mbd.getBeanName());
				try {
					getBean(dep);
				} catch (Exception ex) {
					throw new SimpleException(mbd.getResourceDescription(), mbd.getBeanName(),
							"'" + mbd.getBeanName() + "' depends on missing bean '" + dep + "'", ex);
				}
			}
		}

	}

	
	public String findBeanNameByType(Class<?> requiredType) {
		String beanName = null;
		BeanDefinition resultBeanDefinition = null;
		if (requiredType != null) {
			Set<String> candidates = getBeanNamesForType(requiredType);
			if (candidates != null && candidates.size() > 0) {
				if (candidates.size() > 1) {
					for (String candidate : candidates) {
						BeanDefinition bd = beanDefinitionMap.get(candidate);
						if (resultBeanDefinition == null || (bd.isPrimary() & !resultBeanDefinition.isPrimary())
								|| compareOrder(bd, resultBeanDefinition, requiredType)) {
							resultBeanDefinition = bd;
							beanName = candidate;
						}
					}
				} else {
					beanName = candidates.iterator().next();
				}

			}
		}
		return beanName;
	}

	private boolean compareOrder(BeanDefinition a, BeanDefinition b, Class<?> requiredType) {
		Integer x = (a.getBeanOrder() == null ? Integer.MAX_VALUE : a.getBeanOrder());
		Integer y = (b.getBeanOrder() == null ? Integer.MAX_VALUE : b.getBeanOrder());
		if (x == y) {
			log.info("you have more then one candidate for class " + requiredType
					+ " always ,we will chose the first one finded!");
		}
		return x < y;
	}

	
	public boolean containsBean(String name) {
		return beanDefinitionNames.contains(name);
	}

	
	public boolean containsBean(Class<?> type) {
		for (BeanDefinition bdf : beanDefinitionMap.values()) {
			if (type.isAssignableFrom(bdf.getClass())) {
				return true;
			}
		}
		return false;
	}

	@SuppressWarnings("unchecked")
	
	public <T> Map<String, T> getBeans(Class<T> type) {

		Set<String> beanNames = getBeanNamesForType(type);
		Map<String, T> result = new LinkedHashMap<>(beanNames.size());
		for (String beanName : beanNames) {
			try {
				Object beanInstance = getBean(beanName);
				result.put(beanName, (T) beanInstance);
			} catch (Exception ex) {
				// just ignore the bean in creation
			}
		}
		return result;
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

	
	public BeanDefinition getBeanDefinition(String name) {
		String beanName = canonicalName(name.replaceAll("&", ""));
		BeanDefinition bd = this.beanDefinitionMap.get(beanName);
		if (bd == null)
			throw new SimpleException("no bean " + beanName + " defined!");
		// Set default singleton scope, if not configured before.
		if (!StringUtils.hasLength(bd.getScope())) {
			bd.setScope(BeanDefinition.SCOPE_SINGLETON);
		}
		return bd;
	}

	
	public Collection<BeanDefinition> getBeanDefinitions() {
		return this.beanDefinitionMap.values();
	}

	public void addProcessor(Processor Processor) {
		this.processors.remove(Processor);
		this.processors.add(Processor);
	}

	public void destroyBeans() {
		this.singletonObjects.clear();
		this.resolvableDependencies.clear();
	}

	
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

	public void registerAliases(String name, String[] aliases) {
		for (String alias : aliases) {
			registerAlias(name, alias);
		}
	}

	public void registerBeanDefinition(String beanName, BeanDefinition beanDefinition) {
		BeanDefinition existingDefinition = this.beanDefinitionMap.get(beanName);
		if (existingDefinition != null) {
			if (!beanDefinition.equals(existingDefinition)) {
				if (!isAllowBeanDefinitionOverriding()) {
					throw new SimpleException("beanDefinition of " + beanName + " is exist ");
				}
				this.beanDefinitionMap.put(beanName, beanDefinition);
			}
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
		destroySingleton(beanName);
		for (Processor processor : getProcessors()) {
			processor.resetBeanDefinition(beanName);
		}

	}

	
	public void destroySingleton(String beanName) {
		removeSingleton(beanName);
	}

	protected void removeSingleton(String beanName) {
		synchronized (this.singletonObjects) {
			this.singletonObjects.remove(beanName);
		}
	}
	
	public boolean containsBeanDefinition(String beanName) {
		return this.beanDefinitionMap.containsKey(beanName);
	}

	public int getBeanDefinitionCount() {
		return this.beanDefinitionMap.size();
	}

	
	public String[] getBeanDefinitionNames() {
		return this.beanDefinitionNames.toArray(new String[0]);
	}

	
	public Set<String> getBeanNamesForType(Class<?> type) {
		return getBeanDefinitions(type).keySet();
	}

	
	public Map<String, BeanDefinition> getBeanDefinitions(Class<?> type) {
		Map<String, BeanDefinition> map = new HashMap<String, BeanDefinition>(2);
		for (Entry<String, BeanDefinition> entry : beanDefinitionMap.entrySet()) {
			BeanDefinition bd = entry.getValue();
			String beanName = entry.getKey();
			Class<?> clazz;
			if (bd.hasBeanClass()) {
				clazz = bd.getBeanClass();
			} else {
				clazz = bd.resolveBeanClass();
			}
			if (clazz.equals(type)) {
				map.put(beanName, bd);
			}
		}
		return map;
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

	
	public boolean containsSingleton(String beanName) {
		return this.singletonObjects.containsKey(beanName);
	}

	public boolean isBeanNameInUse(String beanName) {
		return this.aliasMap.containsKey(beanName) && this.beanDefinitionMap.containsKey(beanName);
	}
	
	public Object getSingleton(String beanName) {
		return this.singletonObjects.get(beanName);
	}

	public void addSingleton(String beanName, Object bean) {
		this.singletonObjects.put(beanName, bean);
	}

	protected boolean removeSingletonIfCreatedForTypeCheckOnly(String beanName) {
		if (!this.alreadyCreated.contains(beanName)) {
			removeSingleton(beanName);
			return true;
		} else {
			return false;
		}
	}

	
	public <T> T getBean(String name, Class<T> requiredType, Object... args) {
		return doGetBean(name, requiredType, args, false);
	}

	
	public ApplicationContext getContext() {
		return context;
	}

	public void setContext(ApplicationContext context) {
		this.context = context;
	}

	public boolean isSingleton(String name) {
		return getBeanDefinition(name).isSingleton();
	}

	public boolean isPrototype(String name) {
		return getBeanDefinition(name).isPrototype();
	}

	public Class<?> getType(String name) {
		return getBeanDefinition(name).getBeanClass();
	}
	
	public void setGetBeanAssembler(Assembler<BeanFactory> getBeanAssembler) {
		this.getBeanAssembler = getBeanAssembler;
	}

	public void setDependencyResolveAssembler(Assembler<BeanFactory> dependencyResolveAssembler) {
		this.dependencyResolveAssembler = dependencyResolveAssembler;
	}

	public void setParserValueAssembler(Assembler<BeanFactory> parserValueAssembler) {
		this.parserValueAssembler = parserValueAssembler;
	}


	public void destroy() {
		this.beanDefinitionMap.clear();
		this.beanDefinitionNames.clear();
		this.processors.clear();
		this.dependentBeanMap.clear();
		this.aliasMap.clear();
	}
	
	
}
