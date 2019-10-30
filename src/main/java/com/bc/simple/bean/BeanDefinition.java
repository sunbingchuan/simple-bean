package com.bc.simple.bean;

import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import com.bc.simple.bean.common.Resource;
import com.bc.simple.bean.common.config.ConfigLoader.Node;
import com.bc.simple.bean.common.util.BeanUtils;
import com.bc.simple.bean.common.util.StringUtils;
import com.bc.simple.bean.core.support.AnnotationMetaData;
import com.bc.simple.bean.core.support.BeanMonitor;
import com.bc.simple.bean.core.support.InjectedElement;
import com.bc.simple.bean.core.support.LifecycleElement;
import com.bc.simple.bean.core.support.AnnotationMetaData.MethodMetaData;

public class BeanDefinition {


	public static final String INFER_METHOD = "(inferred)";

	public boolean allowCaching = true;

	/** Package-visible field for caching fully resolved constructor arguments. */

	public Object[] resolvedConstructorArguments;

	/** Package-visible field for caching partly prepared constructor arguments. */

	public Object[] preparedConstructorArguments;

	private final Set<String> externallyManagedInitMethods = Collections.synchronizedSet(new HashSet<String>(0));

	private final Set<String> externallyManagedDestroyMethods = Collections.synchronizedSet(new HashSet<String>(0));

	/** Common lock for the four constructor fields below. */
	public final Object constructorArgumentLock = new Object();


	public Executable resolvedConstructorOrFactoryMethod;

	/** Package-visible field that marks the constructor arguments as resolved. */
	public boolean constructorArgumentsResolved = false;

	public final LinkedHashSet<InjectedElement> injectedElements = new LinkedHashSet<>();

	public final LinkedHashSet<LifecycleElement> lifecycleElements = new LinkedHashSet<>();

	/** Common lock for the two post-processing fields below. */
	public final Object postProcessingLock = new Object();


	public boolean postProcessed = false;

	public LinkedHashMap<String, Object> controls = new LinkedHashMap<>();

	public final Map<String, String> qualifiers = new LinkedHashMap<>();

	public final Set<BeanMonitor> monitoredBeans = Collections.synchronizedSet(new HashSet<>(5));


	public static final int AUTOWIRE_NO = 0;


	public static final int AUTOWIRE_BY_NAME = 1;


	public static final int AUTOWIRE_BY_TYPE = 2;

	public static final int AUTOWIRE_CONSTRUCTOR = 4;

	public static final int AUTOWIRE_AUTODETECT = 8;


	public static final String SCOPE_DEFAULT = "";


	public static final String SCOPE_SINGLETON = "singleton";


	public static final String SCOPE_PROTOTYPE = "prototype";


	public static int ROLE_APPLICATION = 0;


	public static int ROLE_SUPPORT = 1;


	public static int ROLE_INFRASTRUCTURE = 2;

	private volatile Object beanClass;

	private volatile String beanClassName;

	private volatile String beanName;

	private String[] aliases;

	private String scope = SCOPE_DEFAULT;

	private boolean abstractFlag = false;

	private boolean lazyInit = false;

	private String[] dependsOn;

	private boolean autowireCandidate = true;

	private int autowireMode = AUTOWIRE_NO;

	private boolean primary = false;

	private String factoryBeanName;

	private String factoryBeanClassName;

	private String factoryMethodName;

	private String initMethodName;

	private String destroyMethodName;

	private boolean enforceInitMethod = true;

	private boolean enforceDestroyMethod = true;

	private boolean synthetic = false;

	private boolean autowireConstructor = false;

	private boolean autowireFactoryMethod = false;

	private int role = BeanDefinition.ROLE_APPLICATION;

	private String description;

	private boolean annotated = false;

	/** Map with String keys and Object values */
	private final Map<String, Object> attributes = new LinkedHashMap<String, Object>(0);

	private Set<Map<String, Object>> methodOverrides = new HashSet<Map<String, Object>>(0);

	private Set<Map<String, Object>> propertyValues;

	private Node root;

	private final Map<Integer, Object> constructorArgumentValues = new LinkedHashMap<>();

	private AnnotationMetaData metadata;

	private Resource resource;


	public BeanDefinition() {
	}

	private MethodMetaData methodMetaData;

	private boolean isConfigClassBeanDefintion;

	public BeanDefinition(Class<?> clazz) {
		this.beanClass = clazz;
	}

	public BeanDefinition(BeanDefinition parent) {
		setAbstract(parent.isAbstract());
		setAliases(parent.getAliases());
		setAnnotated(parent.isAnnotated());
		setAutowireCandidate(parent.isAutowireCandidate());
		this.attributes.putAll(parent.attributes);
		this.allowCaching = parent.allowCaching;
		setBeanClass(parent.getBeanClass());
		setBeanClassName(parent.getBeanClassName());
		setPrimary(this.isPrimary());
		setMetadata(parent.getMetadata());
		setDependsOn(parent.getDependsOn());
		setDescription(parent.getDescription());
		setDestroyMethodName(parent.getDestroyMethodName());
		setInitMethodName(parent.getInitMethodName());
		setFactoryBeanName(parent.getFactoryBeanName());
		setFactoryBeanClassName(getFactoryBeanClassName());
		setFactoryMethodName(parent.getFactoryMethodName());
		setScope(parent.getScope());
		setAutowireMode(parent.getAutowireMode());
		setConfigClassBeanDefintion(parent.isConfigClassBeanDefintion());
		getConstructorArgumentValues().putAll(parent.getConstructorArgumentValues());
		setEnforceDestroyMethod(parent.isEnforceDestroyMethod());
		setBeanName(parent.getBeanName());
		setEnforceInitMethod(parent.isEnforceInitMethod());
		setLazyInit(parent.isLazyInit());
		setMethodMetaData(parent.getMethodMetaData());
		setMethodOverrides(parent.getMethodOverrides());
		setPropertyValues(parent.getPropertyValues());
		setResource(parent.getResource());
		setRoot(parent.getRoot());
		this.resolvedConstructorOrFactoryMethod = parent.resolvedConstructorOrFactoryMethod;
		this.constructorArgumentsResolved = parent.constructorArgumentsResolved;
		this.preparedConstructorArguments = parent.preparedConstructorArguments;
	}


	public void setBeanClassName(String beanClassName) {
		this.beanClassName = beanClassName;
	}



	public String getBeanClassName() {
		if (StringUtils.isEmpty(beanClassName)) {
			Object beanClassObject = this.beanClass;
			if (beanClassObject instanceof Class) {
				beanClassName = ((Class<?>) beanClassObject).getName();
			} else {
				beanClassName = (String) beanClassObject;
			}
		}
		return beanClassName;
	}


	public void setScope(String scope) {
		this.scope = scope;
	}



	public String getScope() {
		return scope;
	}


	public void setLazyInit(boolean lazyInit) {
		this.lazyInit = lazyInit;
	}


	boolean isLazyInit() {
		return lazyInit;
	}


	public void setDependsOn(String... dependsOn) {
		this.dependsOn = dependsOn;
	}



	public String[] getDependsOn() {
		return dependsOn;
	}


	public void setAutowireCandidate(boolean autowireCandidate) {
		this.autowireCandidate = autowireCandidate;
	}


	public boolean isAutowireCandidate() {
		return this.autowireCandidate;
	}


	public void setPrimary(boolean primary) {
		this.primary = primary;
	}


	public boolean isPrimary() {
		return this.primary;
	}


	public void setFactoryBeanName(String factoryBeanName) {
		this.factoryBeanName = factoryBeanName;
	}



	public String getFactoryBeanName() {
		return this.factoryBeanName;
	}


	public void setFactoryMethodName(String factoryMethodName) {
		this.factoryMethodName = factoryMethodName;
	}

	// Read-only attributes


	public boolean isSingleton() {
		return SCOPE_SINGLETON.equals(this.scope) || SCOPE_DEFAULT.equals(this.scope);

	}


	public boolean isPrototype() {
		return SCOPE_PROTOTYPE.equals(this.scope) || SCOPE_DEFAULT.equals(this.scope);
	}


	boolean isAbstract() {
		return this.abstractFlag;
	}


	public int getRole() {
		return role;
	}



	String getDescription() {
		return description;
	}



	public String getResourceDescription() {
		return (this.resource != null ? this.resource.getDescription() : null);

	}



	BeanDefinition getOriginatingBeanDefinition() {
		return null;
	}


	public void setBeanClass(Class<?> beanClass) {
		this.beanClass = beanClass;
	}


	public Class<?> getBeanClass() {
		Object beanClassObject = this.beanClass;
		if (beanClassObject == null) {
			throw new IllegalStateException("No bean class specified on bean definition");
		}
		if (!(beanClassObject instanceof Class)) {
			throw new IllegalStateException(
					"Bean class name [" + beanClassObject + "] has not been resolved into an actual Class");
		}
		return (Class<?>) beanClassObject;
	}


	public void setAbstract(boolean abstractFlag) {
		this.abstractFlag = abstractFlag;
	}


	public void setAutowireMode(int autowireMode) {
		this.autowireMode = autowireMode;
	}


	public int getAutowireMode() {
		return this.autowireMode;
	}

	public boolean isAnnotated() {
		return annotated;
	}

	public void setAnnotated(boolean annotated) {
		this.annotated = annotated;
	}


	public AnnotationMetaData getMetadata() {
		return metadata;

	}

	public void setMetadata(AnnotationMetaData metadata) {
		this.metadata = metadata;
	}

	public String getBeanName() {
		return beanName;
	}

	public void setBeanName(String beanName) {
		this.beanName = beanName;
	}

	public void setBeanClass(Object beanClass) {
		this.beanClass = beanClass;
	}

	public String[] getAliases() {
		return aliases;
	}

	public void setAliases(String[] aliases) {
		this.aliases = aliases;
	}


	public void setDescription(String description) {
		this.description = description;
	}


	public void setInitMethodName(String initMethodName) {
		this.initMethodName = initMethodName;
	}


	public String getInitMethodName() {
		return this.initMethodName;
	}


	public void setEnforceInitMethod(boolean enforceInitMethod) {
		this.enforceInitMethod = enforceInitMethod;
	}


	public boolean isEnforceInitMethod() {
		return this.enforceInitMethod;
	}


	public void setDestroyMethodName(String destroyMethodName) {
		this.destroyMethodName = destroyMethodName;
	}


	public String getDestroyMethodName() {
		return this.destroyMethodName;
	}


	public void setEnforceDestroyMethod(boolean enforceDestroyMethod) {
		this.enforceDestroyMethod = enforceDestroyMethod;
	}


	public boolean isEnforceDestroyMethod() {
		return this.enforceDestroyMethod;
	}


	public void setAttribute(String name, Object value) {
		if (value != null) {
			this.attributes.put(name, value);
		} else {
			removeAttribute(name);
		}
	}


	public Object getAttribute(String name) {
		return this.attributes.get(name);
	}


	public Object removeAttribute(String name) {
		return this.attributes.remove(name);
	}


	public void setMethodOverrides(Set<Map<String, Object>> methodOverrides) {
		this.methodOverrides = methodOverrides;
	}


	public Set<Map<String, Object>> getMethodOverrides() {
		return this.methodOverrides;
	}


	public void setPropertyValues(Set<Map<String, Object>> propertyValues) {
		this.propertyValues = propertyValues;
	}


	public Set<Map<String, Object>> getPropertyValues() {
		if (this.propertyValues == null) {
			this.propertyValues = new HashSet<Map<String, Object>>(8);
		}
		return this.propertyValues;
	}


	public boolean hasPropertyValues() {
		return (this.propertyValues != null && !this.propertyValues.isEmpty());
	}

	public Node getRoot() {
		return root;
	}

	public void setRoot(Node root) {
		this.root = root;
	}

	public Map<Integer, Object> getConstructorArgumentValues() {
		return constructorArgumentValues;
	}

	public Resource getResource() {
		return resource;
	}

	public void setResource(Resource resource) {
		this.resource = resource;
	}


	public MethodMetaData getMethodMetaData() {
		return methodMetaData;
	}

	public void setMethodMetaData(MethodMetaData methodMetaData) {
		this.methodMetaData = methodMetaData;
	}

	public boolean isConfigClassBeanDefintion() {
		return isConfigClassBeanDefintion;
	}

	public void setConfigClassBeanDefintion(boolean isConfigClassBeanDefintion) {
		this.isConfigClassBeanDefintion = isConfigClassBeanDefintion;
	}


	public boolean hasBeanClass() {
		return (this.beanClass instanceof Class);
	}



	public Class<?> resolveBeanClass(ClassLoader classLoader) {
		String className = getBeanClassName();
		if (className == null) {
			return null;
		}
		Class<?> resolvedClass = BeanUtils.forName(className, classLoader);
		this.beanClass = resolvedClass;
		return resolvedClass;
	}

	public void addInjectedElement(InjectedElement injectedElement) {
		this.injectedElements.add(injectedElement);
	}

	public LinkedHashSet<InjectedElement> getInjectedElements() {
		return injectedElements;
	}

	public void addLifecycleedElement(LifecycleElement lifecycleElement) {
		this.lifecycleElements.add(lifecycleElement);
	}

	public LinkedHashSet<LifecycleElement> getLifecycleElements() {
		return lifecycleElements;
	}


	public String getFactoryMethodName() {
		return this.factoryMethodName;
	}


	public boolean hasConstructorArgumentValues() {
		return (this.constructorArgumentValues != null && !this.constructorArgumentValues.isEmpty());
	}


	public boolean isSynthetic() {
		return this.synthetic;
	}

	public void registerExternallyManagedInitMethod(String initMethod) {
		this.externallyManagedInitMethods.add(initMethod);
	}

	public boolean isExternallyManagedInitMethod(String initMethod) {
		return this.externallyManagedInitMethods.contains(initMethod);
	}

	public void registerExternallyManagedDestroyMethod(String destroyMethod) {
		this.externallyManagedDestroyMethods.add(destroyMethod);
	}

	public boolean isExternallyManagedDestroyMethod(String destroyMethod) {
		return this.externallyManagedDestroyMethods.contains(destroyMethod);
	}


	public boolean isFactoryMethod(Method candidate) {
		return candidate.getName().equals(getFactoryMethodName());
	}


	public int getResolvedAutowireMode() {
		return this.autowireMode;
	}

	public BeanDefinition cloneBeanDefinition() {
		return new BeanDefinition(this);
	}


	public void addQualifier(String typeName, String qualifier) {
		this.qualifiers.put(typeName, qualifier);
	}


	public boolean hasQualifier(String typeName) {
		return this.qualifiers.keySet().contains(typeName);
	}



	public String getQualifier(String typeName) {
		return this.qualifiers.get(typeName);
	}


	public Set<String> getQualifiers() {
		return new LinkedHashSet<>(this.qualifiers.values());
	}


	public void copyQualifiersFrom(BeanDefinition source) {
		this.qualifiers.putAll(source.qualifiers);
	}


	public boolean hasMethodOverrides() {
		return (this.methodOverrides != null && !this.methodOverrides.isEmpty());
	}

	public boolean isAutowireConstructor() {
		return autowireConstructor;
	}

	public void setAutowireConstructor(boolean autowireConstructor) {
		this.autowireConstructor = autowireConstructor;
	}

	public boolean isAutowireFactoryMethod() {
		return autowireFactoryMethod;
	}

	public void setAutowireFactoryMethod(boolean autowireFactoryMethod) {
		this.autowireFactoryMethod = autowireFactoryMethod;
	}

	public String getFactoryBeanClassName() {
		return factoryBeanClassName;
	}

	public void setFactoryBeanClassName(String factoryBeanClassName) {
		this.factoryBeanClassName = factoryBeanClassName;
	}

	public void registerMonitor(BeanMonitor monitor) {
		monitoredBeans.add(monitor);
	}

	public Set<BeanMonitor> getMonitoredBeans() {
		return monitoredBeans;
	}

}
