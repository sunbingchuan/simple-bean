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
import com.bc.simple.bean.common.stereotype.Autowired;
import com.bc.simple.bean.common.support.ConvertService;
import com.bc.simple.bean.common.support.cglib.CglibProxy;
import com.bc.simple.bean.common.util.AnnotationUtils;
import com.bc.simple.bean.common.util.BeanUtils;
import com.bc.simple.bean.common.util.Constant;
import com.bc.simple.bean.common.util.ObjectUtils;
import com.bc.simple.bean.common.util.StringUtils;
import com.bc.simple.bean.core.AbstractBeanFactory;
import com.bc.simple.bean.core.support.CurrencyException;
import com.bc.simple.bean.core.support.DependencyDescriptor;

public class BeanCreateWorkshop extends Workshop {

	private Log log = LogFactory.getLog(this.getClass());

	public BeanCreateWorkshop(AbstractBeanFactory factory) {
		super(factory);
	}


	public Object createBeanInstance(String beanName, BeanDefinition mbd, Object[] args) {
		// Make sure bean class is actually resolved at this point.
		Class<?> beanClass = mbd.getBeanClass();
		if (beanClass == null) {
			beanClass = mbd.resolveBeanClass(null);
		}
		if (mbd.getFactoryMethodName() != null) {
			return instantiateUsingFactoryMethod(beanName, mbd, args);
		}
		boolean resolved = false;
		boolean autowireNecessary = false;
		if (args == null) {
			synchronized (mbd.constructorArgumentLock) {
				if (mbd.resolvedConstructorOrFactoryMethod != null) {
					resolved = true;
					autowireNecessary = mbd.constructorArgumentsResolved;
				}
			}
		}
		if (resolved) {
			if (autowireNecessary) {
				return autowireConstructor(beanName, mbd, null, null);
			} else {
				return instantiateBean(beanName, mbd);
			}
		}

		// Need to determine the constructor...
		Constructor<?>[] ctors = factory.determineConstructorsFromBeanProcessors(beanClass, beanName);
		if (isAutowiredConstructor(ctors, mbd) || mbd.hasConstructorArgumentValues() || !ObjectUtils.isEmpty(args)) {
			return autowireConstructor(beanName, mbd, ctors, args);
		}

		// No special handling: simply use no-arg constructor.
		return instantiateBean(beanName, mbd);
	}


	private Object instantiateUsingFactoryMethod(String beanName, BeanDefinition mbd, Object[] explicitArgs) {
		Object factoryBean;
		Class<?> factoryClass;
		boolean isStatic;
		String factoryBeanName = mbd.getFactoryBeanName();
		if (factoryBeanName != null) {
			if (factoryBeanName.equals(beanName)) {
				throw new CurrencyException(mbd.getResourceDescription() + beanName
						+ "factory-bean reference points back to the same bean definition");
			}
			factoryBean = factory.getBean(factoryBeanName);
			if (mbd.isSingleton() && factory.containsSingleton(beanName)) {
				return factory.getSingleton(factoryBeanName);
			}
			factoryClass = factoryBean.getClass();
			isStatic = false;
		} else {
			// It's a static factory method on the bean class.
			if (!mbd.hasBeanClass()) {
				throw new CurrencyException(mbd.getResourceDescription() + beanName
						+ "bean definition declares neither a bean class nor a factory-bean reference");
			}
			factoryBean = null;
			factoryClass = BeanUtils.forName(mbd.getFactoryBeanClassName());
			isStatic = true;
		}
		Method factoryMethodToUse = null;
		Object[] argsToUse = null;

		if (explicitArgs != null) {
			argsToUse = explicitArgs;
		} else {
			Object[] argsToResolve = null;
			if (mbd.resolvedConstructorOrFactoryMethod != null
					&& mbd.resolvedConstructorOrFactoryMethod instanceof Method) {
				synchronized (mbd.constructorArgumentLock) {
					factoryMethodToUse = (Method) mbd.resolvedConstructorOrFactoryMethod;
					if (factoryMethodToUse != null && mbd.constructorArgumentsResolved) {
						// Found a cached factory method...
						argsToUse = mbd.resolvedConstructorArguments;
						if (argsToUse == null) {
							argsToResolve = mbd.preparedConstructorArguments;
						}
					}
				}
			}
			if (argsToResolve != null) {
				argsToUse = resolvePreparedArguments(beanName, mbd, factoryMethodToUse, argsToResolve);
			}
		}
		if (factoryMethodToUse == null || argsToUse == null) {
			// Need to determine the factory method...
			// Try all methods with this name to see if they match the given arguments.

			factoryClass = BeanUtils.getUserClass(factoryClass);
			Method[] rawCandidates = factoryClass.getDeclaredMethods();
			List<Method> candidateList = new ArrayList<>();
			for (Method candidate : rawCandidates) {
				if (Modifier.isStatic(candidate.getModifiers()) == isStatic && mbd.isFactoryMethod(candidate)) {
					candidateList.add(candidate);
				}
			}

			if (candidateList.size() == 1 && explicitArgs == null && !mbd.hasConstructorArgumentValues()) {
				Method uniqueCandidate = candidateList.get(0);
				if (uniqueCandidate.getParameterCount() == 0) {
					factory.cacheConstructorAndArgs(mbd, uniqueCandidate, AbstractBeanFactory.EMPTY_ARGS);
					return instantiateFactoryMethod(beanName, mbd, factoryBean, uniqueCandidate,
							AbstractBeanFactory.EMPTY_ARGS);
				}
			}
			Method[] candidates = candidateList.toArray(new Method[0]);
			BeanUtils.sortFactoryMethods(candidates);
			insertAssignedMethod(factoryMethodToUse, candidates);
			boolean autowiring = mbd.isAutowireFactoryMethod();
			int minTypeDiffWeight = Integer.MAX_VALUE;
			Method ambiguousFactoryMethod = null;
			int minNrOfArgs;
			Map<Integer, Object> cargs = mbd.getConstructorArgumentValues();
			if (explicitArgs != null) {
				minNrOfArgs = explicitArgs.length;
			} else {
				// We don't have arguments passed in programmatically, so we need to resolve the
				// arguments specified in the constructor arguments held in the bean definition.
				if (mbd.hasConstructorArgumentValues()) {
					minNrOfArgs = resolveConstructorArguments(beanName, mbd, cargs);
				} else {
					minNrOfArgs = 0;
				}
			}
			LinkedList<Exception> causes = null;
			for (Method candidate : candidates) {
				Class<?>[] paramTypes = candidate.getParameterTypes();
				if (paramTypes.length >= minNrOfArgs) {
					Object[] argsArray = null;
					if (explicitArgs != null) {
						// Explicit arguments given -> arguments length must match exactly.
						if (paramTypes.length != explicitArgs.length) {
							continue;
						}
						argsArray = explicitArgs;
					} else {
						// Resolved constructor arguments: type conversion and/or autowiring
						// necessary.
						try {
							String[] paramNames = BeanUtils.getParameterNames(candidate);
							argsArray = createArgumentArray(beanName, mbd, cargs, paramTypes, paramNames, candidate,
									autowiring);
						} catch (Exception ex) {
							log.info(
									"Ignoring factory method [" + candidate + "] of bean '" + beanName + "': " + ex);
							// Swallow and try next overloaded factory method.
							if (causes == null) {
								causes = new LinkedList<>();
							}
							causes.add(ex);
							continue;
						}
					}
					int typeDiffWeight = 0;
					typeDiffWeight = BeanUtils.getTypeDifferenceWeight(candidate.getParameterTypes(), argsArray);

					// Choose this factory method if it represents the closest match.
					if (typeDiffWeight < minTypeDiffWeight) {
						factoryMethodToUse = candidate;
						argsToUse = argsArray;
						minTypeDiffWeight = typeDiffWeight;
					}
					// Find out about ambiguity: In case of the same type difference weight
					// for methods with the same number of parameters, collect such candidates
					// and eventually raise an ambiguity exception.
					// However, only perform that check in non-lenient constructor resolution mode,
					// and explicitly ignore overridden methods (with the same parameter signature).
					else if (factoryMethodToUse != null && typeDiffWeight == minTypeDiffWeight
							&& paramTypes.length == factoryMethodToUse.getParameterCount()
							&& !Arrays.equals(paramTypes, factoryMethodToUse.getParameterTypes())) {
						if (ambiguousFactoryMethod == null) {
							ambiguousFactoryMethod = candidate;
						}
					}
				}
			}

			if (factoryMethodToUse == null) {
				if (causes != null) {
					Exception ex = causes.removeLast();
					for (Exception cause : causes) {
						factory.onSuppressedException(cause);
					}
					log.info(ex);
				}
				List<String> argTypes = new ArrayList<>(minNrOfArgs);
				for (Object arg : argsToUse) {
					argTypes.add(arg.getClass().getCanonicalName());
				}
				String argDesc = StringUtils.collectionToDelimitedString(argTypes, ",", "", "");
				throw new CurrencyException(mbd.getResourceDescription(), beanName, "No matching factory method found: "
						+ (mbd.getFactoryBeanName() != null ? "factory bean '" + mbd.getFactoryBeanName() + "'; " : "")
						+ "factory method '" + mbd.getFactoryMethodName() + "(" + argDesc + ")'. "
						+ "Check that a method with the specified name " + (minNrOfArgs > 0 ? "and arguments " : "")
						+ "exists and that it is " + (isStatic ? "static" : "non-static") + ".");
			} else if (void.class == factoryMethodToUse.getReturnType()) {
				throw new CurrencyException(mbd.getResourceDescription(), beanName, "Invalid factory method '"
						+ mbd.getFactoryMethodName() + "': needs to have a non-void return type!");
			} else if (ambiguousFactoryMethod != null) {
				throw new CurrencyException(mbd.getResourceDescription(), beanName,
						"Ambiguous factory method matches found in bean '" + beanName + "' "
								+ "(hint: specify index/type/name arguments for simple parameters to avoid type ambiguities): "
								+ ambiguousFactoryMethod);
			}
		}
		factory.cacheConstructorAndArgs(mbd, factoryMethodToUse, argsToUse);
		return instantiateFactoryMethod(beanName, mbd, factoryBean, factoryMethodToUse, argsToUse);
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

	private Object instantiateFactoryMethod(String beanName, BeanDefinition mbd, Object factoryBean,
			Method factoryMethod, Object[] args) {
		try {
			Object result = factoryMethod.invoke(factoryBean, args);
			return result;
		} catch (Throwable ex) {
			throw new CurrencyException("Bean instantiation via factory method failed", ex);
		}
	}


	private Object autowireConstructor(String beanName, BeanDefinition mbd, Constructor<?>[] chosenCtors,
			Object[] explicitArgs) {
		Constructor<?> constructorToUse = null;
		Object[] argsToUse = null;

		if (explicitArgs != null) {
			argsToUse = explicitArgs;
		} else {
			Object[] argsToResolve = null;
			if (mbd.resolvedConstructorOrFactoryMethod != null
					&& mbd.resolvedConstructorOrFactoryMethod instanceof Constructor) {
				constructorToUse = (Constructor<?>) mbd.resolvedConstructorOrFactoryMethod;
				synchronized (mbd.constructorArgumentLock) {
					if (mbd.constructorArgumentsResolved && mbd.resolvedConstructorArguments != null) {
						argsToResolve = mbd.preparedConstructorArguments;
					}
				}
			}
			if (argsToResolve != null) {
				argsToUse = resolvePreparedArguments(beanName, mbd, constructorToUse, argsToResolve);
			}
		}

		if (constructorToUse == null || argsToUse == null) {
			// Take specified constructors, if any.
			Constructor<?>[] candidates = chosenCtors;
			if (candidates == null) {
				Class<?> beanClass = mbd.getBeanClass();
				try {
					candidates = beanClass.getDeclaredConstructors();
				} catch (Throwable ex) {
					throw new CurrencyException(mbd.getResourceDescription(), beanName,
							"Resolution of declared constructors on bean Class [" + beanClass.getName()
									+ "] from ClassLoader [" + beanClass.getClassLoader() + "] failed",
							ex);
				}
			}
			if (candidates.length == 1 && explicitArgs == null && !mbd.hasConstructorArgumentValues()) {
				Constructor<?> uniqueCandidate = candidates[0];
				if (uniqueCandidate.getParameterCount() == 0) {
					factory.cacheConstructorAndArgs(mbd, uniqueCandidate, AbstractBeanFactory.EMPTY_ARGS);
					return instantiate(beanName, mbd, uniqueCandidate, AbstractBeanFactory.EMPTY_ARGS);
				}
			}

			// Need to resolve the constructor.
			boolean autowiring = (chosenCtors != null || mbd.isAutowireConstructor());

			int minNrOfArgs;
			Map<Integer, Object> cargs = mbd.getConstructorArgumentValues();
			if (explicitArgs != null) {
				minNrOfArgs = explicitArgs.length;
			} else {
				minNrOfArgs = resolveConstructorArguments(beanName, mbd, mbd.getConstructorArgumentValues());
			}

			BeanUtils.sortConstructors(candidates);
			int minTypeDiffWeight = Integer.MAX_VALUE;
			Constructor<?> ambiguousConstructor = null;
			LinkedList<Exception> causes = null;

			for (Constructor<?> candidate : candidates) {
				Class<?>[] paramTypes = candidate.getParameterTypes();

				if (constructorToUse != null && argsToUse.length > paramTypes.length) {
					// Already found greedy constructor that can be satisfied ->
					// do not look any further, there are only less greedy constructors left.
					break;
				}
				if (paramTypes.length < minNrOfArgs) {
					continue;
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
					argsToUse = createArgumentArray(beanName, mbd, cargs, paramTypes, paramNames, candidate,
							autowiring);
				} catch (Exception ex) {
					// ex.printStackTrace();
					// Swallow and try next constructor.
					if (causes == null) {
						causes = new LinkedList<>();
					}
					causes.add(ex);
					continue;
				}

				int typeDiffWeight = 0;
				typeDiffWeight = BeanUtils.getTypeDifferenceWeight(candidate.getParameterTypes(), argsToUse);
				if (typeDiffWeight < minTypeDiffWeight) {
					constructorToUse = candidate;
					minTypeDiffWeight = typeDiffWeight;
					ambiguousConstructor = null;
				} else if (constructorToUse != null && typeDiffWeight == minTypeDiffWeight) {
					if (ambiguousConstructor == null) {
						ambiguousConstructor = candidate;
					}
				}
			}

			if (constructorToUse == null) {
				if (causes != null) {
					Exception ex = causes.removeLast();
					for (Exception cause : causes) {
						factory.onSuppressedException(cause);
					}
					log.info(ex);
				}
				throw new CurrencyException(mbd.getResourceDescription(), beanName,
						"Could not resolve matching constructor "
								+ "(hint: specify index/type/name arguments for simple parameters to avoid type ambiguities)");
			} else if (ambiguousConstructor != null) {
				throw new CurrencyException(mbd.getResourceDescription(), beanName,
						"Ambiguous constructor matches found in bean '" + beanName + "' "
								+ "(hint: specify index/type/name arguments for simple parameters to avoid type ambiguities): "
								+ ambiguousConstructor);
			}
		}
		factory.cacheConstructorAndArgs(mbd, constructorToUse, argsToUse);
		return instantiate(beanName, mbd, constructorToUse, argsToUse);
	}


	private Object instantiateBean(final String beanName, final BeanDefinition mbd) {
		try {
			Object beanInstance;
			beanInstance = instantiate(mbd, beanName, factory);
			return beanInstance;
		} catch (Throwable ex) {
			throw new CurrencyException(mbd.getResourceDescription(), beanName, "Instantiation of bean failed", ex);
		}
	}

	private Object instantiate(String beanName, BeanDefinition mbd, Constructor<?> constructorToUse,
			Object[] argsToUse) {
		try {
			Object result = constructorToUse.newInstance(argsToUse);
			return result;
		} catch (Throwable ex) {
			throw new CurrencyException("Bean instantiation via factory method failed", ex);
		}
	}

	private Object instantiate(BeanDefinition bd, String beanName, AbstractBeanFactory factory) {
		// Don't override the class with CGLIB if no overrides.
		if (!bd.hasMethodOverrides()) {
			Constructor<?> constructorToUse;
			synchronized (bd.constructorArgumentLock) {
				constructorToUse = (Constructor<?>) bd.resolvedConstructorOrFactoryMethod;
				if (constructorToUse == null) {
					final Class<?> clazz = bd.getBeanClass();
					if (clazz.isInterface()) {
						throw new CurrencyException(clazz, "Specified class is an interface");
					}
					try {
						if (System.getSecurityManager() != null) {
							constructorToUse = AccessController.doPrivileged(
									(PrivilegedExceptionAction<Constructor<?>>) clazz::getDeclaredConstructor);
						} else {
							constructorToUse = clazz.getDeclaredConstructor();
						}
						bd.resolvedConstructorOrFactoryMethod = constructorToUse;
					} catch (Throwable ex) {
						throw new CurrencyException(clazz, "No default constructor found", ex);
					}
				}
			}
			return BeanUtils.instantiateClass(constructorToUse);
		} else {
			// Must generate CGLIB subclass.
			return instantiateWithMethodInjection(bd, beanName, factory);
		}

	}

	private Object instantiateWithMethodInjection(BeanDefinition bd, String beanName, AbstractBeanFactory owner) {
		Class<?> beanClass = bd.resolveBeanClass(null);
		Map<Method, InvocationHandler> overrideMethods = new HashMap<>();
		Set<Map<String, Object>> overrides = bd.getMethodOverrides();
		for (Map<String, Object> override : overrides) {
			String overrideType = (String) override.get(Constant.ATTR_OVERRIDE_TYPE);
			String name = (String) override.get(Constant.ATTR_NAME);
			if (Constant.OVERRIDE_TYPE_INIT_VALUE.equals(overrideType)) {
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
				@SuppressWarnings("unchecked")
				List<Class<?>> types = (List<Class<?>>) override.get(Constant.DOC_ARG_TYPE);
				Method method = BeanUtils.findMethod(beanClass, name, types.toArray(new Class<?>[0]));
				overrideMethods.put(method, replacer);
			}

		}
		Object instance = CglibProxy.getProxyInstance(new InvocationHandler() {

			@Override
			public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
				return findHandler(method, overrideMethods, beanClass).invoke(proxy, method, args);
			}

		}, beanClass, overrideMethods.keySet().toArray(new Method[0]), new Class[] {});
		return instance;
	}

	private InvocationHandler findHandler(Method method, Map<Method, InvocationHandler> overrideMethods,
			Class<?> parent) {
		for (Method overrideMethod : overrideMethods.keySet()) {
			if (method.getName().equals(overrideMethod.getName())
					&& BeanUtils.paramsFit(method.getParameterTypes(), overrideMethod.getParameterTypes())) {
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
				value = parseConstructArgs((Map<String, Object>) cargs.get(paramIndex));
			}
			if (value != null) {
				value = convertService.convert(value, paramType);
			} else {
				if (!autowiring) {
					throw new CurrencyException(mbd.getResourceDescription(), beanName,
							"Ambiguous argument values for parameter of type [" + paramType.getName()
									+ "] - did you specify the correct bean references as arguments?");
				}
				try {
					Object autowiredArgument = factory.resolveDependency(new DependencyDescriptor(parameter, true),
							beanName, autowiredBeanNames);
					value = autowiredArgument;
				} catch (Exception e) {
					throw new CurrencyException(mbd.getResourceDescription(), beanName, e);
				}
			}
			args[paramIndex] = value;
		}

		for (String autowiredBeanName : autowiredBeanNames) {
			factory.registerDependentBean(autowiredBeanName, beanName);
			log.info("Autowiring by type from bean name '" + beanName + "' via "
					+ (executable instanceof Constructor ? "constructor" : "factory method") + " to bean named '"
					+ autowiredBeanName + "'");
		}

		return args;
	}

	private Object parseConstructArgs(Map<String, Object> carg) {
		@SuppressWarnings("unchecked")
		Map<String, Object> value = (Map<String, Object>) carg.get(Constant.ATTR_VALUE);
		if (Constant.TYPE_STRING_VALUE.equals(value.get(Constant.ATTR_PROPERTY_TYPE))) {
			return value.get(Constant.ATTR_VALUE);
		} else if (Constant.TYPE_REF_VALUE.equals(carg.get(Constant.ATTR_PROPERTY_TYPE))) {
			String beanName = (String) value.get(Constant.ATTR_REF);
			Class<?> classType = BeanUtils.forName((String) value.get(Constant.ATTR_REF_TYPE), null);
			return factory.getBean(beanName, classType);
		}
		return null;
	}


	private Object[] resolvePreparedArguments(String beanName, BeanDefinition mbd, Executable executable,
			Object[] argsToResolve) {
		Class<?>[] paramTypes = executable.getParameterTypes();
		Object[] resolvedArgs = new Object[argsToResolve.length];
		for (int argIndex = 0; argIndex < argsToResolve.length; argIndex++) {
			Object argValue = argsToResolve[argIndex];
			Class<?> paramType = paramTypes[argIndex];
			try {
				resolvedArgs[argIndex] = factory.getConvertService().convert(argValue, paramType);
			} catch (Exception ex) {
				log.info("Could not convert argument value of type [" + ObjectUtils.nullSafeClassName(argValue)
						+ "] to required type [" + paramType.getName() + "]: " + ex.getMessage());
			}
		}
		return resolvedArgs;
	}

	private int resolveConstructorArguments(String beanName, BeanDefinition mbd, Map<Integer, Object> cargs) {
		int minNrOfArgs = cargs.size();
		for (Entry<Integer, Object> entry : cargs.entrySet()) {
			int index = entry.getKey();
			if (index < 0) {
				throw new CurrencyException(
						mbd.getResourceDescription() + beanName + "Invalid constructor argument index: " + index);
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
		for (Constructor<?> constructor : ctors) {
			if (null != constructor.getAnnotation(Autowired.class)) {
				bdf.setAutowireConstructor(true);
				return true;
			}
		}
		return false;
	}

	@SuppressWarnings("unchecked")
	@Override
	public void produceWorkshop() {
		StoreRoom<BeanDefinition, Object[], Object> storeRoom = (StoreRoom<BeanDefinition, Object[], Object>) factory.currentStoreRoom.get();
		BeanDefinition mbd =storeRoom.getX();
		Object[] args =storeRoom.getY();
		String beanName=mbd.getBeanName();
		Object bean = null;
		if (mbd.isSingleton()) {
			bean = factory.getSingleton(beanName);
		}
		if (bean == null) {
			bean = createBeanInstance(beanName, mbd, args);
		}
		// Allow post-processors to modify the merged bean definition.
		synchronized (mbd.postProcessingLock) {
			if (!mbd.postProcessed) {
				try {
					factory.applyMergedBeanDefinitionPostProcessors(mbd, beanName);
				} catch (Throwable ex) {
					log.info(mbd.getResourceDescription() + " " + beanName + " "
							+
							"Post-processing of merged bean definition failed", ex);
				}
				mbd.postProcessed = true;
			}
		}
		try {
			factory.registerMonitorBeanIfNecessary(beanName, bean, mbd);
		} catch (Exception ex) {
			log.info(mbd.getResourceDescription() + " " + beanName + " Invalid destruction signature");
		}
		if (mbd.isSingleton()) {
			factory.addSingleton(beanName, bean);
		}
		storeRoom.setZ(bean);
	}

}
