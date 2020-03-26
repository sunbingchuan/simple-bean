package com.bc.simple.bean.core;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.bc.simple.bean.ApplicationContext;
import com.bc.simple.bean.BeanDefinition;
import com.bc.simple.bean.common.annotation.Qualifier;
import com.bc.simple.bean.common.annotation.Value;
import com.bc.simple.bean.common.support.ConvertService;
import com.bc.simple.bean.common.support.ConvertService.Converter;
import com.bc.simple.bean.common.util.BeanUtils;
import com.bc.simple.bean.common.util.Constant;
import com.bc.simple.bean.common.util.StringUtils;
import com.bc.simple.bean.core.processor.Processor;
import com.bc.simple.bean.core.support.BeanMonitor;
import com.bc.simple.bean.core.support.DependencyDescriptor;
import com.bc.simple.bean.core.support.SimpleException;
import com.bc.simple.bean.core.workshop.BeanCreateWorkshop;
import com.bc.simple.bean.core.workshop.BeanFittingWorkshop;
import com.bc.simple.bean.core.workshop.DependencyResolveWorkshop;
import com.bc.simple.bean.core.workshop.LargeStoreRoom;
import com.bc.simple.bean.core.workshop.StoreRoom;
import com.bc.simple.bean.core.workshop.Workshop;

public abstract class AbstractBeanFactory extends Factory {

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

	protected boolean allowRawInjectionDespiteWrapping = false;

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

	protected final Map<String, Set<String>> dependenciesForBeanMap = new ConcurrentHashMap<>(64);

	protected Class<? extends Annotation> valueAnnotationType = Value.class;

	/** Whether to cache bean metadata or rather reobtain it for every access. */
	protected boolean cacheBeanMetadata = true;

	/** List of suppressed Exceptions, available for associating related causes. */
	private Set<Exception> suppressedExceptions;

	public Map<Member, Object> cachedValues = new ConcurrentHashMap<>();

	/** Cache of singleton objects: bean name to bean instance. */
	protected final Map<String, Object> singletonObjects = new ConcurrentHashMap<>(256);

	public final ThreadLocal<StoreRoom<?, ?, ?>> currentStoreRoom = new ThreadLocal<>();

	public final String getBeanKey;
	public final String resolveDependencyKey;

	public AbstractBeanFactory() {
		Workshop getBean = new BeanCreateWorkshop(this);
		getBean.next(new BeanFittingWorkshop(this));
		this.getBeanKey = addWorkshop(getBean);
		this.resolveDependencyKey = addWorkshop(new DependencyResolveWorkshop(this));
	}

	protected Object doCreateBean(final String beanName, final BeanDefinition mbd, final Object[] args) {
		StoreRoom<?, ?, ?> previousStoreRoom = currentStoreRoom.get();
		currentStoreRoom.set(new StoreRoom<BeanDefinition, Object[], Object>(mbd, args, null));
		produce(getBeanKey);
		Object bean = currentStoreRoom.get().getZ();
		currentStoreRoom.set(previousStoreRoom);
		return bean;
	}

	public void applyMergedBeanDefinitionPostProcessors(BeanDefinition mbd) {
		for (Processor bp : getProcessors()) {
			bp.postProcessMergedBeanDefinition(mbd);
		}
	}

	public boolean isSingletonCurrentlyInCreation(String beanName) {
		return this.singletonsCurrentlyInCreation.contains(beanName);
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

	public abstract Object getSingleton(String beanName);

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

	protected abstract boolean removeSingletonIfCreatedForTypeCheckOnly(String beanName);

	public void registerMonitorBeanIfNecessary(String beanName, Object bean, BeanDefinition mbd) {
		if (bean instanceof BeanMonitor) {
			mbd.registerMonitor((BeanMonitor) bean);
		}
	}

	public Object obtainFromSupplier(Supplier<?> instanceSupplier, String beanName) {
		Object instance = null;
		try {
			instance = instanceSupplier.get();
		} catch (Exception e) {

		}
		return instance;
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

	public abstract List<Processor> getProcessors();

	public boolean hasInstantiationAwareBeanProcessors() {
		return this.hasInstantiationAwareBeanProcessors;
	}

	public void setHasInstantiationAwareBeanProcessors(boolean hasInstantiationAwareBeanProcessors) {
		this.hasInstantiationAwareBeanProcessors = hasInstantiationAwareBeanProcessors;
	}

	public AccessControlContext getAccessControlContext() {
		return (this.accessControlContext != null ? this.accessControlContext : AccessController.getContext());
	}

	public Object applyBeanProcessorsBeforeInitialization(Object existingBean, String beanName) {
		Object result = existingBean;
		for (Processor processor : getProcessors()) {
			processor.postProcessBeforeInitialization(result, beanName);
		}
		return result;
	}

	public Object applyBeanProcessorsAfterInitialization(Object existingBean, String beanName) {
		Object result = existingBean;
		for (Processor processor : getProcessors()) {
			processor.postProcessAfterInitialization(result, beanName);
		}
		return result;
	}

	protected void invokeInitMethods(String beanName, final Object bean, BeanDefinition mbd) throws Throwable {

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

	public ClassLoader getBeanClassLoader() {
		return this.beanClassLoader;
	}

	public abstract Object getBean(String name);

	public abstract <T> T getBean(String name, Class<T> requiredType);

	public abstract Object getBean(String name, Object... args);

	public abstract <T> T getBean(String name, Class<T> requiredType, Object... args);

	protected void invokeCustomInitMethod(String beanName, final Object bean, BeanDefinition mbd) throws Throwable {

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
						getAccessControlContext());
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

	public abstract boolean containsBean(String name);

	public abstract boolean containsBean(Class<?> type);

	public abstract boolean containsSingleton(String beanName);

	public void registerDependentBean(String beanName, String dependentBeanName) {
		String canonicalName = canonicalName(beanName);
		synchronized (this.dependentBeanMap) {
			Set<String> dependentBeans =
					this.dependentBeanMap.computeIfAbsent(canonicalName, k -> new LinkedHashSet<>(8));
			if (!dependentBeans.add(dependentBeanName)) {
				return;
			}
		}
		synchronized (this.dependenciesForBeanMap) {
			Set<String> dependenciesForBean =
					this.dependenciesForBeanMap.computeIfAbsent(dependentBeanName, k -> new LinkedHashSet<>(8));
			dependenciesForBean.add(canonicalName);
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

	public abstract boolean containsBeanDefinition(String beanName);

	public abstract BeanDefinition getBeanDefinition(String beanName);

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

	public abstract <T> Map<String, T> getBeans(Class<T> type);

	public abstract String[] getBeanDefinitionNames();

	public abstract Set<String> getBeanNamesForType(Class<?> type);

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

	public abstract <T> T doGetBean(final String name, final Class<T> requiredType, final Object[] args,
			boolean typeCheckOnly);

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

	public abstract void destroySingleton(String beanName);

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
		StoreRoom<?, ?, ?> previousStoreRoom = currentStoreRoom.get();
		currentStoreRoom.set(new LargeStoreRoom<DependencyDescriptor, String, Object, Set<String>, Object>(
				dependencyDescriptor, beanName, autowiredBeanNames, null, null));
		produce(resolveDependencyKey);
		LargeStoreRoom<DependencyDescriptor, String, Set<String>, Object, Object> storeRoom =
				(LargeStoreRoom<DependencyDescriptor, String, Set<String>, Object, Object>) currentStoreRoom.get();
		Object bean = storeRoom.getM();
		currentStoreRoom.set(previousStoreRoom);
		return bean;
	}

	public Set<BeanMonitor> getBeanMonitor(String beanName) {
		BeanDefinition bd = getBeanDefinition(beanName);
		if (bd == null) {
			return null;
		}
		return bd.getMonitoredBeans();
	}

	@SuppressWarnings("unchecked")
	public Object parseValue(String createdBeanName, Map<String, Object> define) {
		String type = StringUtils.toString(define.get(Constant.ATTR_TYPE));
		if (StringUtils.isEmpty(type) || Constant.TYPE_STRING_VALUE.equals(type)) {
			return define.get(Constant.ATTR_VALUE);
		} else if (Constant.TYPE_REF_VALUE.equals(type)) {
			String beanName = (String) define.get(Constant.ATTR_REF);
			Class<?> classType = null;
			String refType = StringUtils.toString(define.get(Constant.ATTR_REF_TYPE));
			if (StringUtils.hasLength(refType)) {
				classType = BeanUtils.forName(refType, null);
			}
			if (StringUtils.isEmpty(beanName)) {
				beanName = findBeanNameByType(classType);
			}
			registerDependentBean(createdBeanName, beanName);
			return getBean(beanName);
		} else if (Constant.TYPE_MAP_VALUE.equals(type)) {
			List<Map<String, Object>> list = (List<Map<String, Object>>) define.get(Constant.ATTR_VALUE);
			return parseMap(createdBeanName, list);
		} else if (Constant.TYPE_SET_VALUE.equals(type)) {
			List<Map<String, Object>> list = (List<Map<String, Object>>) define.get(Constant.ATTR_VALUE);
			return parseSet(createdBeanName, list);
		} else if (Constant.TYPE_LIST_VALUE.equals(type)) {
			List<Map<String, Object>> list = (List<Map<String, Object>>) define.get(Constant.ATTR_VALUE);
			return parseList(createdBeanName, list);
		} else if (Constant.TYPE_ARRAY_VALUE.equals(type)) {
			List<Map<String, Object>> list = (List<Map<String, Object>>) define.get(Constant.ATTR_VALUE);
			return parseArray(createdBeanName, list);
		} else if (Constant.TYPE_PROPS_VALUE.equals(type)) {
			List<Map<String, Object>> list = (List<Map<String, Object>>) define.get(Constant.ATTR_VALUE);
			return parseProps(createdBeanName, list);
		}
		return null;
	}

	@SuppressWarnings({"unchecked"})
	private Properties parseProps(String createdBeanName, List<Map<String, Object>> list) {
		Properties result = new Properties();
		for (Map<String, Object> map : list) {
			Object key = parseValue(createdBeanName, (Map<String, Object>) map.get(Constant.ATTR_KEY));
			Object value = parseValue(createdBeanName, (Map<String, Object>) map.get(Constant.ATTR_VALUE));
			if (key != null) {
				result.put(key, value);
			}
		}
		return result;
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private Map parseMap(String createdBeanName, List<Map<String, Object>> list) {
		Map result = new HashMap<>();
		for (Map<String, Object> map : list) {
			Object key = parseValue(createdBeanName, (Map<String, Object>) map.get(Constant.ATTR_KEY));
			Object value = parseValue(createdBeanName, (Map<String, Object>) map.get(Constant.ATTR_VALUE));
			if (key != null) {
				result.put(key, value);
			}
		}
		return result;
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private List parseList(String createdBeanName, List<Map<String, Object>> list) {
		List result = new ArrayList<>();
		for (Map<String, Object> map : list) {
			Object val = parseValue(createdBeanName, map);
			if (val != null) {
				result.add(val);
			}
		}
		return result;
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private Set parseSet(String createdBeanName, List<Map<String, Object>> list) {
		Set result = new HashSet<>();
		for (Map<String, Object> map : list) {
			Object val = parseValue(createdBeanName, map);
			if (val != null) {
				result.add(val);
			}
		}
		return result;
	}

	private Object[] parseArray(String createdBeanName, List<Map<String, Object>> list) {
		Object[] result = new Object[list.size()];
		for (int i = 0; i < list.size(); i++) {
			Map<String, Object> map = list.get(i);
			Object val = parseValue(createdBeanName, map);
			result[i] = val;
		}
		return result;
	}

	public abstract void addSingleton(String beanName, Object bean);

	public abstract ApplicationContext getContext();

	public abstract Map<String, BeanDefinition> getBeanDefinitions(Class<?> type);

	public abstract String findBeanNameByType(Class<?> requiredType);

	public abstract Collection<BeanDefinition> getBeanDefinitions();
}
