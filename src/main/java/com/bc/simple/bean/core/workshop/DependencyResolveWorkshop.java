package com.bc.simple.bean.core.workshop;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.bc.simple.bean.common.support.ExpressionUtils;
import com.bc.simple.bean.common.util.AnnotationUtils;
import com.bc.simple.bean.common.util.BeanUtils;
import com.bc.simple.bean.common.util.ObjectUtils;
import com.bc.simple.bean.common.util.StringUtils;
import com.bc.simple.bean.core.AbstractBeanFactory;
import com.bc.simple.bean.core.support.CurrencyException;
import com.bc.simple.bean.core.support.DependencyDescriptor;

public class DependencyResolveWorkshop extends Workshop {

	private Log log = LogFactory.getLog(this.getClass());

	public DependencyResolveWorkshop(AbstractBeanFactory factory) {
		super(factory);
	}

	private Object doResolveDependency(DependencyDescriptor descriptor, String beanName,
			Set<String> autowiredBeanNames) {
		try {
			Class<?> type = descriptor.getDependencyType();
			Object value = AnnotationUtils.findValue(descriptor.getAnnotations());
			if (value != null) {
				if (value instanceof String) {
					String strVal = (String) value;
					try {
						Object val = ExpressionUtils.parseComplexExpression(strVal);
						val = factory.getContext().getPropertyResolverHandler().resolvePlaceholders(val.toString());
						if (val != null && StringUtils.isNotEmpty(val.toString())) {
							strVal = val.toString();
						}
					} catch (Exception e) {
					}
					value = strVal;
				}
				return factory.getConvertService().convert(value, type);
			}

			Object multipleBeans = resolveMultipleBeans(descriptor, beanName, autowiredBeanNames);
			if (multipleBeans != null) {
				return multipleBeans;
			}

			Map<String, Object> matchingBeans = findAutowireCandidates(beanName, type, descriptor);
			if (matchingBeans.isEmpty()) {
				if (descriptor.isRequired()) {
					throw new CurrencyException(type, descriptor.getResolvableType(), descriptor);
				}
				return null;
			}

			String autowiredBeanName;
			Object instanceCandidate;

			if (matchingBeans.size() > 1) {
				autowiredBeanName = determineAutowireCandidate(matchingBeans, descriptor);
				if (autowiredBeanName == null) {
					if (descriptor.isRequired() || !factory.indicatesMultipleBeans(type)) {
						throw new CurrencyException(descriptor.getResolvableType(), matchingBeans);
					} else {
						// In case of an optional Collection/Map, silently ignore a non-unique case:
						// possibly it was meant to be an empty collection of multiple regular beans
						// (before 4.3 in particular when we didn't even look for collection beans).
						return null;
					}
				}
				instanceCandidate = matchingBeans.get(autowiredBeanName);
			} else {
				// We have exactly one match.
				Map.Entry<String, Object> entry = matchingBeans.entrySet().iterator().next();
				autowiredBeanName = entry.getKey();
				instanceCandidate = entry.getValue();
			}
			if (autowiredBeanNames != null) {
				autowiredBeanNames.add(autowiredBeanName);
			}
			if (instanceCandidate instanceof Class) {
				instanceCandidate = factory.getBean(autowiredBeanName);
			}
			Object result = instanceCandidate;
			if (result == null) {
				if (descriptor.isRequired()) {
					throw new CurrencyException(type, descriptor.getResolvableType(), descriptor);
				}
				result = null;
			}
			if (!BeanUtils.isAssignableValue(type, result)) {
				throw new CurrencyException(autowiredBeanName, type, instanceCandidate.getClass());
			}
			return result;
		} catch (Throwable e) {
			e.printStackTrace();
			throw new CurrencyException("resolve depedency of '" + beanName + "'  failed!", e);
		}
	}

	private Object resolveMultipleBeans(DependencyDescriptor descriptor, String beanName,
			Set<String> autowiredBeanNames) {

		Class<?> type = descriptor.getDependencyType();
		DependencyDescriptor multiDescriptor = new DependencyDescriptor(descriptor);
		multiDescriptor.increaseNestingLevel();
		if (descriptor.isStreamAccess()) {
			Map<String, Object> matchingBeans = findAutowireCandidates(beanName, type, descriptor);
			if (autowiredBeanNames != null) {
				autowiredBeanNames.addAll(matchingBeans.keySet());
			}
			return matchingBeans.values().stream();
		} else if (type.isArray()) {
			Class<?> componentType = type.getComponentType();
			Class<?> resolvedArrayType = descriptor.getDependencyType();
			if (resolvedArrayType != null && resolvedArrayType != type) {
				type = resolvedArrayType;
				componentType = resolvedArrayType;
			}
			if (componentType == null) {
				return null;
			}
			Map<String, Object> matchingBeans = findAutowireCandidates(beanName, componentType, multiDescriptor);
			if (matchingBeans.isEmpty()) {
				return null;
			}
			if (autowiredBeanNames != null) {
				autowiredBeanNames.addAll(matchingBeans.keySet());
			}
			Object result = factory.getConvertService().convert(matchingBeans.values(), type);
			if (factory.getDependencyComparator() != null && result instanceof Object[]) {
				Arrays.sort((Object[]) result, factory.getDependencyComparator());
			}
			return result;
		} else if (Collection.class.isAssignableFrom(type) && type.isInterface()) {
			Class<?> elementType = BeanUtils.getGeneric(descriptor.getDependencyType(), Collection.class).get(0);
			if (elementType == null) {
				return null;
			}
			Map<String, Object> matchingBeans = findAutowireCandidates(beanName, elementType, descriptor);
			if (matchingBeans.isEmpty()) {
				return null;
			}
			if (autowiredBeanNames != null) {
				autowiredBeanNames.addAll(matchingBeans.keySet());
			}
			Object result = factory.getConvertService().convert(matchingBeans.values(), type);
			if (factory.getDependencyComparator() != null && result instanceof List) {
				((List<?>) result).sort(factory.getDependencyComparator());
			}
			return result;
		} else if (Map.class == type) {
			List<Class<?>> generics = BeanUtils.getGeneric(descriptor.getDependencyType(), Collection.class);
			if (String.class != generics.get(0)) {
				return null;
			}
			if (null == generics.get(1)) {
				return null;
			}
			Map<String, Object> matchingBeans = findAutowireCandidates(beanName, generics.get(1), multiDescriptor);
			if (matchingBeans.isEmpty()) {
				return null;
			}
			if (autowiredBeanNames != null) {
				autowiredBeanNames.addAll(matchingBeans.keySet());
			}
			return matchingBeans;
		} else {
			return null;
		}
	}


	private Map<String, Object> findAutowireCandidates(String beanName, Class<?> requiredType,
			DependencyDescriptor descriptor) {

		String[] candidateNames = factory.getBeanNamesForType(requiredType, true, descriptor.isEager());
		Map<String, Object> result = new LinkedHashMap<>(candidateNames.length);
		for (Map.Entry<Class<?>, Object> classObjectEntry : factory.resolvableDependencies.entrySet()) {
			Class<?> autowiringType = classObjectEntry.getKey();
			if (autowiringType.isAssignableFrom(requiredType)) {
				Object autowiringValue = classObjectEntry.getValue();
				if (requiredType.isInstance(autowiringValue)) {
					result.put(ObjectUtils.identityToString(autowiringValue), autowiringValue);
					break;
				}
			}
		}
		for (String candidate : candidateNames) {
			if (factory.isAutowireCandidate(candidate, descriptor)) {
				result.put(candidate, factory.getBean(candidate));
			}
		}
		return result;
	}



	private String determineAutowireCandidate(Map<String, Object> candidates, DependencyDescriptor descriptor) {
		String result;
		Class<?> requiredType = descriptor.getDependencyType();
		result = determinePrimaryCandidate(candidates, requiredType);
		if (result != null) {
			return result;
		}
		result = determineHighestOrderCandidate(candidates, requiredType);
		if (result != null) {
			return result;
		}
		// Fallback
		for (Map.Entry<String, Object> entry : candidates.entrySet()) {
			String candidateName = entry.getKey();
			Object beanInstance = entry.getValue();
			if ((beanInstance != null && factory.resolvableDependencies.containsValue(beanInstance))
					|| candidateName.equals(descriptor.getDependencyName())
					|| factory.getAliasesArray(candidateName).contains(candidateName)) {
				result = candidateName;
			}
		}
		if (result == null) {
			log.info("you have more then one candidate for dependency " + descriptor
					+ " , always  , we will chose the first one!");
			result = candidates.keySet().iterator().next();
		}
		return result;
	}


	private String determinePrimaryCandidate(Map<String, Object> candidates, Class<?> requiredType) {
		String primaryBeanName = null;
		for (Map.Entry<String, Object> entry : candidates.entrySet()) {
			String candidateBeanName = entry.getKey();
			Object beanInstance = entry.getValue();
			if (factory.isPrimary(candidateBeanName, beanInstance)) {
				if (primaryBeanName != null) {
					boolean candidateLocal = factory.containsBeanDefinition(candidateBeanName);
					boolean primaryLocal = factory.containsBeanDefinition(primaryBeanName);
					if (candidateLocal && primaryLocal) {
						throw new CurrencyException(requiredType, candidates.size(),
								"more than one 'primary' bean found among candidates: " + candidates.keySet());
					} else if (candidateLocal) {
						primaryBeanName = candidateBeanName;
					}
				} else {
					primaryBeanName = candidateBeanName;
				}
			}
		}
		return primaryBeanName;
	}


	private String determineHighestOrderCandidate(Map<String, Object> candidates, Class<?> requiredType) {
		String highestPriorityBeanName = null;
		Integer highestPriority = null;
		for (Map.Entry<String, Object> entry : candidates.entrySet()) {
			String candidateBeanName = entry.getKey();
			Object beanInstance = entry.getValue();
			if (beanInstance != null) {
				Integer order = AnnotationUtils.getOrder(beanInstance.getClass());
				if (order != null) {
					if (highestPriorityBeanName != null) {
						if (order.equals(highestPriority)) {
							throw new CurrencyException(requiredType, candidates.size(),
									"Multiple beans found with the same priority ('" + highestPriority
											+ "') among candidates: " + candidates.keySet());
						} else if (order < highestPriority) {
							highestPriorityBeanName = candidateBeanName;
							highestPriority = order;
						}
					} else {
						highestPriorityBeanName = candidateBeanName;
						highestPriority = order;
					}
				}
			}
		}
		return highestPriorityBeanName;
	}

	@SuppressWarnings("unchecked")
	@Override
	public void produceWorkshop() {
		LargeStoreRoom<DependencyDescriptor, String, Set<String>, Object, Object> storeRoom = (LargeStoreRoom<DependencyDescriptor, String, Set<String>, Object, Object>) factory.currentStoreRoom
				.get();
		Object bean = doResolveDependency(storeRoom.getX(), storeRoom.getY(), storeRoom.getZ());
		storeRoom.setM(bean);
	}

}
