package com.bc.simple.bean.core.workshop;

import java.beans.ConstructorProperties;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.security.AccessController;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.bc.simple.bean.BeanDefinition;
import com.bc.simple.bean.common.annotation.Autowired;
import com.bc.simple.bean.common.support.ConvertService;
import com.bc.simple.bean.common.support.proxy.CommonInvocationHandler;
import com.bc.simple.bean.common.support.proxy.Proxy;
import com.bc.simple.bean.common.util.AnnotationUtils;
import com.bc.simple.bean.common.util.BeanUtils;
import com.bc.simple.bean.common.util.Constant;
import com.bc.simple.bean.common.util.ObjectUtils;
import com.bc.simple.bean.common.util.StringUtils;
import com.bc.simple.bean.core.AbstractBeanFactory;
import com.bc.simple.bean.core.support.DependencyDescriptor;
import com.bc.simple.bean.core.support.SimpleException;

public class BeanCreateWorkshop extends Workshop {

	private Log log = LogFactory.getLog(this.getClass());

	public BeanCreateWorkshop(AbstractBeanFactory factory) {
		super(factory);
	}

	public Object createBeanInstance(BeanDefinition mbd, Object[] args) {
		// Make sure bean class is actually resolved at this point.
		Class<?> beanClass = mbd.getBeanClass();
		if (beanClass == null) {
			beanClass = mbd.resolveBeanClass();
		}
		if (mbd.getFactoryMethodName() != null) {
			return instantiateUsingFactoryMethod(mbd, args);
		}
		boolean resolved = false;
		boolean autowireNecessary = false;
		if (args == null) {
			synchronized (mbd.buildArgumentLock) {
				if (mbd.buildMethod != null) {
					resolved = true;
					autowireNecessary = mbd.buildArgumentsResolved;
				}
			}
		}
		if (resolved) {
			if (autowireNecessary) {
				return autowireConstructor(mbd, null, null);
			} else {
				return instantiateBean(mbd);
			}
		}

		// Need to determine the constructor...
		Constructor<?>[] ctors = factory.determineConstructorsFromBeanProcessors(beanClass);
		if (isAutowiredConstructor(ctors, mbd) || mbd.hasConstructorArgumentValues() || !ObjectUtils.isEmpty(args)) {
			return autowireConstructor(mbd, ctors, args);
		}
		// No special handling: simply use no-arg constructor.
		return instantiateBean(mbd);
	}


	class FactoryEntry {
		boolean isStatic;
		Class<?> factoryClass;
		Method factoryMethod;
		Method ambiguousFactoryMethod;
		Object[] args;
		Object factoryBean;
	}

	private Object instantiateUsingFactoryMethod(BeanDefinition mbd, Object[] explicitArgs) {
		FactoryEntry fe = new FactoryEntry();
		String beanName = mbd.getBeanName();

		initFactoryEntry(fe, mbd);

		if (explicitArgs != null) {
			fe.args = explicitArgs;
		} else {
			resolveCachedFactoryArgs(mbd, fe);
		}

		if (fe.factoryMethod == null || fe.args == null) {
			// Need to determine the factory method...
			// Try all methods with this name to see if they match the given arguments.
			List<Method> candidateList = searchFactoryMethod(fe, mbd);
			if (candidateList.size() == 1 && explicitArgs == null && !mbd.hasConstructorArgumentValues()) {
				Method uniqueCandidate = candidateList.get(0);
				if (uniqueCandidate.getParameterCount() == 0) {
					factory.cacheConstructorAndArgs(mbd, uniqueCandidate, AbstractBeanFactory.EMPTY_ARGS);
					fe.factoryMethod = uniqueCandidate;
					fe.args = AbstractBeanFactory.EMPTY_ARGS;
					return instantiateFactoryMethod(beanName, mbd, fe);
				}
			}
			Method[] candidates = candidateList.toArray(new Method[0]);
			BeanUtils.sortFactoryMethods(candidates);
			insertAssignedMethod(fe.factoryMethod, candidates);
			int minTypeDiffWeight = Integer.MAX_VALUE;
			int minNrOfArgs;
			Map<Integer, Object> cargs = mbd.getConstructorArgumentValues();
			if (explicitArgs != null) {
				minNrOfArgs = explicitArgs.length;
			} else {
				if (mbd.hasConstructorArgumentValues()) {
					minNrOfArgs = calcArgumentLength(beanName, mbd, cargs);
				} else {
					minNrOfArgs = 0;
				}
			}
			LinkedList<Exception> causes = new LinkedList<Exception>();
			for (Method candidate : candidates) {
				minTypeDiffWeight = vertifyFactoryCandidate(candidate, minNrOfArgs, minTypeDiffWeight, mbd, causes, fe,
						explicitArgs);
			}
			needThrowFactoryException(fe, causes, mbd);
		}
		factory.cacheConstructorAndArgs(mbd, fe.factoryMethod, fe.args);
		return instantiateFactoryMethod(beanName, mbd, fe);
	}

	private int vertifyConstructorCandidate(Constructor<?> candidate, int argCount, int minTypeDiffWeight,
			BeanDefinition mbd, ConstructorEntry ce, LinkedList<Exception> causes, boolean autowiring) {
		Class<?>[] paramTypes = candidate.getParameterTypes();
		if (paramTypes.length < argCount) {
			return minTypeDiffWeight;
		}
		try {
			String[] paramNames = null;
			ConstructorProperties prop = AnnotationUtils.findAnnotation(candidate, ConstructorProperties.class);
			if (prop != null) {
				paramNames = prop.value();
				if (paramNames == null) {
					paramNames = BeanUtils.getParameterNames(candidate);
				}
			}
			ce.argsToUse = createArgumentArray(mbd.getBeanName(), mbd, mbd.getConstructorArgumentValues(), paramTypes,
					paramNames, candidate, autowiring);
		} catch (Exception ex) {
			// ex.printStackTrace();
			if (causes == null) {
				causes = new LinkedList<>();
			}
			causes.add(ex);
			return minTypeDiffWeight;
		}

		int typeDiffWeight = 0;
		typeDiffWeight = BeanUtils.getTypeDifferenceWeight(candidate.getParameterTypes(), ce.argsToUse);
		if (typeDiffWeight < minTypeDiffWeight) {
			ce.constructorToUse = candidate;
			minTypeDiffWeight = typeDiffWeight;
			ce.ambiguousConstructor = null;
		} else if (ce.constructorToUse != null && typeDiffWeight == minTypeDiffWeight) {
			if (ce.ambiguousConstructor == null) {
				ce.ambiguousConstructor = candidate;
			}
		}
		return minTypeDiffWeight;
	}

	private int vertifyFactoryCandidate(Method candidate, int minNrOfArgs, int minTypeDiffWeight, BeanDefinition mbd,
			LinkedList<Exception> causes, FactoryEntry fe, Object[] explicitArgs) {
		Class<?>[] paramTypes = candidate.getParameterTypes();
		String beanName = mbd.getBeanName();
		if (paramTypes.length >= minNrOfArgs) {
			Object[] argsArray = null;
			if (explicitArgs != null) {
				if (paramTypes.length != explicitArgs.length) {
					return minTypeDiffWeight;
				}
				argsArray = explicitArgs;
			} else {
				try {
					String[] paramNames = BeanUtils.getParameterNames(candidate);
					argsArray = createArgumentArray(beanName, mbd, mbd.getConstructorArgumentValues(), paramTypes,
							paramNames, candidate, mbd.isAutowireFactoryMethod());
				} catch (Exception ex) {
					log.debug("Ignoring factory method [" + candidate + "] of bean '" + beanName + "': " + ex);
					// Swallow and try next overloaded factory method.
					causes.add(ex);
					return minTypeDiffWeight;
				}
			}
			int typeDiffWeight = 0;
			typeDiffWeight = BeanUtils.getTypeDifferenceWeight(candidate.getParameterTypes(), argsArray);

			// Choose this factory method if it represents the closest match.
			if (typeDiffWeight < minTypeDiffWeight) {
				fe.factoryMethod = candidate;
				fe.args = argsArray;
				minTypeDiffWeight = typeDiffWeight;
			}
			// Find out about ambiguity: In case of the same type difference weight
			// for methods with the same number of parameters, collect such candidates
			// and eventually raise an ambiguity exception.
			else if (fe.factoryMethod != null && typeDiffWeight == minTypeDiffWeight
					&& paramTypes.length == fe.factoryMethod.getParameterCount()
					&& !Arrays.equals(paramTypes, fe.factoryMethod.getParameterTypes())) {
				if (fe.ambiguousFactoryMethod == null) {
					fe.ambiguousFactoryMethod = candidate;
				}
			}
		}
		return minTypeDiffWeight;
	}


	private void initFactoryEntry(FactoryEntry fe, BeanDefinition mbd) {
		String beanName = mbd.getBeanName();
		String factoryBeanName = mbd.getFactoryBeanName();
		if (factoryBeanName != null) {
			if (factoryBeanName.equals(beanName)) {
				throw new SimpleException("Factory-bean of bean " + beanName + "reference points back to itself!");
			}
			fe.factoryBean = factory.getBean(factoryBeanName);
			fe.factoryClass = fe.factoryBean.getClass();
			fe.isStatic = false;
		} else {
			// It's a static factory method on the bean class.
			if (StringUtils.isEmpty(mbd.getFactoryBeanClassName())) {
				throw new SimpleException("Definition of bean " + beanName
						+ "  declared neither a factory-bean class nor a factory-bean reference!");
			}
			fe.factoryBean = null;
			fe.factoryClass = BeanUtils.forName(mbd.getFactoryBeanClassName());
			fe.isStatic = true;
		}
	}

	private void needThrowConstructorException(ConstructorEntry ce, LinkedList<Exception> causes, BeanDefinition mbd) {
		if (ce.constructorToUse == null) {
			if (causes != null) {
				Exception ex = causes.removeLast();
				for (Exception cause : causes) {
					factory.onSuppressedException(cause);
				}
				log.error("Failed to instance bean!", ex);
			}
			throw new SimpleException(" Could not resolve matching constructor of bean " + mbd.getBeanName()
					+ " defined at " + mbd.getResourceDescription() + "!");
		} else if (ce.ambiguousConstructor != null) {
			throw new SimpleException("Ambiguous constructor matches found for bean '" + mbd.getBeanName() + "' "
					+ ce.ambiguousConstructor + " and " + ce.constructorToUse);
		}
	}

	private void needThrowFactoryException(FactoryEntry fe, LinkedList<Exception> causes, BeanDefinition mbd) {
		String beanName = mbd.getBeanName();
		if (fe.factoryMethod == null) {
			if (causes != null) {
				Exception ex = causes.removeLast();
				for (Exception cause : causes) {
					factory.onSuppressedException(cause);
				}
				log.error("Failed to instance bean!", ex);
			}
			throw new SimpleException("No matching factory method found: "
					+ (mbd.getFactoryBeanName() != null ? "factory bean '" + mbd.getFactoryBeanName() + "'; " : "")
					+ "factory method '" + mbd.getFactoryMethodName() + "'. "
					+ "Check that a method with the specified name " + beanName + " exists and that it is "
					+ (fe.isStatic ? "static" : "non-static") + ".");
		} else if (void.class == fe.factoryMethod.getReturnType()) {
			throw new SimpleException("Invalid factory method '" + mbd.getFactoryMethodName()
					+ "': needs to have a non-void return type!");
		} else if (fe.ambiguousFactoryMethod != null) {
			throw new SimpleException(mbd.getResourceDescription(), beanName,
					"Ambiguous factory method matches found for bean '" + beanName + "': " + fe.factoryMethod + " and "
							+ fe.ambiguousFactoryMethod);
		}
	}

	// TODO
	private void resolveCachedFactoryArgs(BeanDefinition mbd, FactoryEntry fe) {
		Object[] argsToResolve = null;
		if (mbd.buildMethod != null && mbd.buildMethod instanceof Method) {
			synchronized (mbd.buildArgumentLock) {
				fe.factoryMethod = (Method) mbd.buildMethod;
				if (fe.factoryMethod != null && mbd.buildArgumentsResolved && mbd.resolvedBuildArguments != null) {
					argsToResolve = mbd.preparedBuildArguments;
				}
			}
		}
		if (argsToResolve != null) {
			fe.args = resolvePreparedArguments(fe.factoryMethod, argsToResolve);
		}
	}

	private void resolveCachedConstructorArgs(BeanDefinition mbd, ConstructorEntry ce) {
		Object[] argsToResolve = null;
		if (mbd.buildMethod != null && mbd.buildMethod instanceof Constructor) {
			ce.constructorToUse = (Constructor<?>) mbd.buildMethod;
			synchronized (mbd.buildArgumentLock) {
				if (mbd.buildArgumentsResolved && mbd.resolvedBuildArguments != null) {
					argsToResolve = mbd.preparedBuildArguments;
				}
			}
		}
		if (argsToResolve != null) {
			ce.argsToUse = resolvePreparedArguments(ce.constructorToUse, argsToResolve);
		}
	}

	private List<Method> searchFactoryMethod(FactoryEntry fe, BeanDefinition mbd) {
		fe.factoryClass = BeanUtils.getUserClass(fe.factoryClass);
		Method[] rawCandidates = fe.factoryClass.getDeclaredMethods();
		List<Method> candidateList = new ArrayList<>();
		for (Method candidate : rawCandidates) {
			if (Modifier.isStatic(candidate.getModifiers()) == fe.isStatic && mbd.isFactoryMethod(candidate)) {
				candidateList.add(candidate);
			}
		}
		return candidateList;
	}


	private Method[] insertAssignedMethod(Method assignedMethdod, Method[] candidates) {
		// make the assigned method first if have
		if (assignedMethdod != null) {
			Method[] tmp = new Method[candidates.length + 1];
			System.arraycopy(candidates, 0, tmp, 1, candidates.length);
			tmp[0] = assignedMethdod;
			candidates = tmp;
		}
		return candidates;
	}

	private Object instantiateFactoryMethod(String beanName, BeanDefinition mbd, FactoryEntry fe) {
		try {
			Object result = fe.factoryMethod.invoke(fe.factoryBean, fe.args);
			return result;
		} catch (Throwable ex) {
			throw new SimpleException("Bean instantiation via factory method failed!", ex);
		}
	}

	class ConstructorEntry {
		Constructor<?> constructorToUse = null;
		Object[] argsToUse = null;
		Constructor<?> ambiguousConstructor = null;
	}

	// TODO
	private Object autowireConstructor(BeanDefinition mbd, Constructor<?>[] chosenCtors, Object[] explicitArgs) {
		ConstructorEntry ce = new ConstructorEntry();
		String beanName = mbd.getBeanName();
		if (explicitArgs != null) {
			ce.argsToUse = explicitArgs;
		} else {
			resolveCachedConstructorArgs(mbd, ce);
		}
		if (ce.constructorToUse == null || ce.argsToUse == null) {
			// Take specified constructors, if any.
			Constructor<?>[] candidates = retrieveConstructors(mbd, chosenCtors);
			if (candidates.length == 1 && explicitArgs == null && !mbd.hasConstructorArgumentValues()) {
				Constructor<?> uniqueCandidate = candidates[0];
				if (uniqueCandidate.getParameterCount() == 0) {
					factory.cacheConstructorAndArgs(mbd, uniqueCandidate, AbstractBeanFactory.EMPTY_ARGS);
					return instantiate(beanName, mbd, uniqueCandidate, AbstractBeanFactory.EMPTY_ARGS);
				}
			}
			// Need to resolve the constructor.
			boolean autowiring = (chosenCtors != null || mbd.isAutowireConstructor());
			int argCount;
			Map<Integer, Object> cargs = mbd.getConstructorArgumentValues();
			if (explicitArgs != null) {
				argCount = explicitArgs.length;
			} else {
				argCount = calcArgumentLength(beanName, mbd, cargs);
			}

			BeanUtils.sortConstructors(candidates);
			int minTypeDiffWeight = Integer.MAX_VALUE;
			LinkedList<Exception> causes = new LinkedList<>();
			for (Constructor<?> candidate : candidates) {
				Class<?>[] paramTypes = candidate.getParameterTypes();
				if (ce.constructorToUse != null && ce.argsToUse.length > paramTypes.length) {
					// Already found greedy constructor that can be satisfied ->
					// do not look any further, there are only less greedy constructors left.
					break;
				}
				vertifyConstructorCandidate(candidate, argCount, minTypeDiffWeight, mbd, ce, causes, autowiring);
			}
			needThrowConstructorException(ce, causes, mbd);
		}
		factory.cacheConstructorAndArgs(mbd, ce.constructorToUse, ce.argsToUse);
		return instantiate(beanName, mbd, ce.constructorToUse, ce.argsToUse);
	}

	private Constructor<?>[] retrieveConstructors(BeanDefinition mbd, Constructor<?>[] chosenCtors) {
		// Take specified constructors, if any.
		Constructor<?>[] candidates = chosenCtors;
		if (candidates == null) {
			Class<?> beanClass = mbd.getBeanClass();
			try {
				candidates = beanClass.getDeclaredConstructors();
			} catch (Throwable ex) {
				throw new SimpleException("Resolution of declared constructors on bean Class [" + beanClass.getName()
						+ "] whose beanName is" + mbd.getBeanName() + " which is defined at "
						+ mbd.getResourceDescription() + " from ClassLoader [" + beanClass.getClassLoader()
						+ "] failed!", ex);
			}
		}
		return candidates;
	}

	private Object instantiateBean(final BeanDefinition mbd) {
		try {
			Object beanInstance;
			beanInstance = instantiate(mbd, factory);
			return beanInstance;
		} catch (Throwable ex) {
			throw new SimpleException("Instantiation of bean " + mbd.getBeanName() + " failed!", ex);
		}
	}

	private Object instantiate(String beanName, BeanDefinition mbd, Constructor<?> constructorToUse,
			Object[] argsToUse) {
		try {
			Object result = constructorToUse.newInstance(argsToUse);
			return result;
		} catch (Throwable ex) {
			throw new SimpleException("Bean instantiation via factory method failed!", ex);
		}
	}

	private Object instantiate(BeanDefinition bd, AbstractBeanFactory factory) {
		// Don't override the class with CGLIB if no overrides.
		if (!bd.hasMethodOverrides() && !bd.isClassProxy()) {
			Constructor<?> constructorToUse;
			synchronized (bd.buildArgumentLock) {
				constructorToUse = (Constructor<?>) bd.buildMethod;
				if (constructorToUse == null) {
					final Class<?> clazz = bd.getBeanClass();
					if (clazz.isInterface()) {
						throw new SimpleException(clazz, "Specified class is an interface!");
					}
					try {
						if (System.getSecurityManager() != null) {
							constructorToUse = AccessController.doPrivileged(
									(PrivilegedExceptionAction<Constructor<?>>) clazz::getDeclaredConstructor);
						} else {
							constructorToUse = clazz.getDeclaredConstructor();
						}
						bd.buildMethod = constructorToUse;
					} catch (Throwable ex) {
						throw new SimpleException("No default constructor of class " + clazz + " found!", ex);
					}
				}
			}
			return BeanUtils.instantiateClass(constructorToUse);
		} else {
			// Must generate CGLIB subclass.
			return instantiateWithMethodInjection(bd, factory);
		}

	}

	private Object instantiateWithMethodInjection(BeanDefinition bd, AbstractBeanFactory owner) {
		Class<?> beanClass = bd.resolveBeanClass();
		if (!bd.isOverrideMethodParsed()) {
			Map<Method, InvocationHandler> overrideMethods = new HashMap<>();
			Set<Map<String, Object>> overrides = bd.getOverrideMethodDefinitions();
			for (Map<String, Object> override : overrides) {
				parseOveride(override, overrideMethods, beanClass);
			}
			if (bd.isCommonClassProxy()) {
				CommonInvocationHandler.registerAll(overrideMethods);
			} else if (overrideMethods.size() > 0) {
				bd.getOverrideMethods().putAll(overrideMethods);
			}
			bd.setOverrideMethodParsed(true);
		}
		Object instance;
		if (bd.isClassProxy()) {
			instance = Proxy.proxy(beanClass, bd.getHandleClass());
		} else {
			instance = Proxy.instance(new InvocationHandler() {
				@Override
				public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
					return findHandler(method, bd.getOverrideMethods(), beanClass).invoke(proxy, method, args);
				}
			}, beanClass, bd.getOverrideMethods().keySet().toArray(new Method[0]), new Class[] {});
		}
		return instance;

	}

	private Map<Method, InvocationHandler> parseOveride(Map<String, Object> override,
			Map<Method, InvocationHandler> overrideMethods, Class<?> beanClass) {
		String overrideType = (String) override.get(Constant.ATTR_OVERRIDE_TYPE);
		String name = (String) override.get(Constant.ATTR_NAME);
		if (Constant.OVERRIDE_TYPE_LOOKUP_VALUE.equals(overrideType)) {
			String lookUpBeanName = (String) override.get(Constant.ATTR_BEAN);
			InvocationHandler lookUp = new InvocationHandler() {
				@Override
				public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
					return factory.getBean(lookUpBeanName);
				}
			};
			Set<Method> methods = BeanUtils.getMethods(beanClass, name);
			for (Method method : methods) {
				overrideMethods.put(method, lookUp);
			}
		} else if (Constant.OVERRIDE_TYPE_REPLACE_VALUE.equals(overrideType)) {
			String replaceBeanName = (String) override.get(Constant.ATTR_REPLACER);
			InvocationHandler replacer = (InvocationHandler) factory.getBean(replaceBeanName);
			Object argType = override.get(Constant.DOC_ARG_TYPE);
			if (argType instanceof String && Constant.ANY_VALUE.equals(argType)) {
				Set<Method> methods = BeanUtils.getMethods(beanClass, name);
				for (Method method : methods) {
					overrideMethods.put(method, replacer);
				}
			} else {
				@SuppressWarnings("unchecked")
				List<Class<?>> types = (List<Class<?>>) override.get(Constant.DOC_ARG_TYPE);
				Method method = BeanUtils.findMethod(beanClass, name, types.toArray(new Class<?>[0]));
				overrideMethods.put(method, replacer);
			}
		}
		return overrideMethods;
	}

	private InvocationHandler findHandler(Method method, Map<Method, InvocationHandler> overrideMethods,
			Class<?> parent) {
		for (Method overrideMethod : overrideMethods.keySet()) {
			if (method.getName().equals(overrideMethod.getName() + Proxy.PROXY_SUFFIX)
					&& BeanUtils.paramsEqual(method.getParameterTypes(), overrideMethod.getParameterTypes())) {
				return overrideMethods.get(overrideMethod);
			}
		}
		return new InvocationHandler() {
			@Override
			public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
				return null;
			}
		};
	}

	@SuppressWarnings("unchecked")
	private Object[] createArgumentArray(String beanName, BeanDefinition mbd, Map<Integer, Object> cargs,
			Class<?>[] paramTypes, String[] paramNames, Executable executable, boolean autowiring) {
		ConvertService convertService = factory.getConvertService();
		Object[] args = new Object[paramTypes.length];
		Set<String> autowiredBeanNames = new LinkedHashSet<>(4);
		Parameter[] parameters = executable.getParameters();
		for (int paramIndex = 0; paramIndex < paramTypes.length; paramIndex++) {
			Class<?> paramType = paramTypes[paramIndex];
			Parameter parameter = parameters[paramIndex];
			Object value = null;
			if (!ObjectUtils.isEmpty(cargs)) {
				value = parseArguments(beanName, (Map<String, Object>) cargs.get(paramIndex));
			}
			if (value != null) {
				value = convertService.convert(value, paramType);
			} else {
				if (!autowiring) {
					throw new SimpleException("No argument values for parameter of type [" + paramType.getName()
							+ "] when made bean " + beanName + "!");
				}
				try {
					Object autowiredArgument = factory.resolveDependency(new DependencyDescriptor(parameter, true),
							beanName, autowiredBeanNames);
					value = autowiredArgument;
				} catch (Exception e) {
					throw new SimpleException("Autowaire type " + paramType + " faild for bean " + beanName, e);
				}
			}
			args[paramIndex] = value;
		}

		for (String autowiredBeanName : autowiredBeanNames) {
			factory.registerDependentBean(autowiredBeanName, beanName);
			log.debug("Autowiring by type in bean  '" + beanName + "' via "
					+ (executable instanceof Constructor ? "constructor" : "factory method") + " to bean named '"
					+ autowiredBeanName + "'");
		}
		return args;
	}

	private Object parseArguments(String createdBeanName, Map<String, Object> carg) {
		return factory.parseValue(createdBeanName, carg);
	}

	private Object[] resolvePreparedArguments(Executable executable, Object[] argsToResolve) {
		Class<?>[] paramTypes = executable.getParameterTypes();
		Object[] resolvedArgs = new Object[argsToResolve.length];
		for (int argIndex = 0; argIndex < argsToResolve.length; argIndex++) {
			Object argValue = argsToResolve[argIndex];
			Class<?> paramType = paramTypes[argIndex];
			try {
				resolvedArgs[argIndex] = factory.getConvertService().convert(argValue, paramType);
			} catch (Exception ex) {
				log.error("Could not convert argument value of type [" + ObjectUtils.nullSafeClassName(argValue)
						+ "] to required type [" + paramType.getName() + "]: ", ex);
			}
		}
		return resolvedArgs;
	}

	private int calcArgumentLength(String beanName, BeanDefinition mbd, Map<Integer, Object> cargs) {
		int minNrOfArgs = cargs.size();
		for (Entry<Integer, Object> entry : cargs.entrySet()) {
			int index = entry.getKey();
			if (index < 0) {
				throw new SimpleException(
						"Invalid constructor argument index: " + index + ",beanName is " + beanName + ".");
			}
			if (index > minNrOfArgs) {
				minNrOfArgs = index + 1;
			}
		}
		return minNrOfArgs;
	}

	private boolean isAutowiredConstructor(Constructor<?>[] ctors, BeanDefinition bdf) {
		if (bdf.isAutowireConstructor()) {
			return true;
		}
		if (ctors != null) {
			for (Constructor<?> constructor : ctors) {
				if (null != constructor.getAnnotation(Autowired.class)) {
					bdf.setAutowireConstructor(true);
					return true;
				}
			}
		}
		return false;
	}

	@SuppressWarnings("unchecked")
	@Override
	public void produceWorkshop() {
		StoreRoom<BeanDefinition, Object[], Object> storeRoom =
				(StoreRoom<BeanDefinition, Object[], Object>) factory.currentStoreRoom.get();
		BeanDefinition mbd = storeRoom.getX();
		Object[] args = storeRoom.getY();
		String beanName = mbd.getBeanName();
		Object bean = null;
		if (mbd.isSingleton()) {
			bean = factory.getSingleton(beanName);
		}
		if (bean == null) {
			bean = createBeanInstance(mbd, args);
		}
		synchronized (mbd.postProcessingLock) {
			if (!mbd.postProcessed) {
				try {
					factory.applyMergedBeanDefinitionPostProcessors(mbd);
				} catch (Throwable ex) {
					log.info(mbd.getResourceDescription() + " " + beanName + " "
							+ "Post-processing of merged bean definition failed", ex);
				}
				mbd.postProcessed = true;
			}
		}
		factory.registerMonitorBeanIfNecessary(beanName, bean, mbd);
		if (mbd.isSingleton()) {
			factory.addSingleton(beanName, bean);
		}
		storeRoom.setZ(bean);
	}

}
