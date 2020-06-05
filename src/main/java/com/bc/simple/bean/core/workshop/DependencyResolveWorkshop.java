package com.bc.simple.bean.core.workshop;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import javax.annotation.Resource;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.bc.simple.bean.common.support.ExpressionUtils;
import com.bc.simple.bean.common.util.AnnotationUtils;
import com.bc.simple.bean.common.util.BeanUtils;
import com.bc.simple.bean.common.util.BeanUtils.ClassGenericParameter;
import com.bc.simple.bean.common.util.ObjectUtils;
import com.bc.simple.bean.common.util.StringUtils;
import com.bc.simple.bean.core.BeanFactory;
import com.bc.simple.bean.core.support.DependencyDescriptor;
import com.bc.simple.bean.core.support.SimpleException;

public class DependencyResolveWorkshop extends Workshop {

	private Log log = LogFactory.getLog(this.getClass());

	public DependencyResolveWorkshop(BeanFactory factory) {
		super(factory);
	}

	private Object doResolveDependency(DependencyDescriptor descriptor, String beanName,
			Set<String> autowiredBeanNames) {
		try {
			Class<?> type = descriptor.getDependencyType();
			Object value = AnnotationUtils.findValue(descriptor.getAnnotatedElement());
			if (value != null) {
				value = parseValue(value);
				return factory.getConvertService().convert(value, type);
			}
			Object bean = parseResource(descriptor);
			if (bean != null) {
				return bean;
			}
			Object multipleBeans = resolveMultipleBeans(descriptor, beanName, autowiredBeanNames);
			if (multipleBeans != null) {
				return multipleBeans;
			}
			Map<String, Object> matchingBeans = findAutowireCandidates(beanName, type, descriptor);
			if (matchingBeans.isEmpty()) {
				if (descriptor.isRequired()) {
					throw new SimpleException("Could't find matching bean for type " + type + "!");
				}
				return null;
			}

			String autowiredBeanName;
			Object instanceCandidate;

			if (matchingBeans.size() > 1) {
				autowiredBeanName = determineAutowireCandidate(matchingBeans, descriptor);
				if (autowiredBeanName == null) {
					throw new SimpleException(
							"Find more then one mathing bean for type " + type + ":" + matchingBeans.keySet());
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
					throw new SimpleException("Could't find matching bean for type " + type + "!");
				}
			}
			if (!BeanUtils.isAssignableValue(type, result)) {
				throw new SimpleException("Could't find matching bean for type " + type + "!");
			}
			return result;
		} catch (Throwable e) {
			e.printStackTrace();
			throw new SimpleException("Resolve depedency " + descriptor.getMember() + " of '" + beanName + "'  failed!",
					e);
		}
	}

	private Object parseResource(DependencyDescriptor descriptor) {
		AnnotatedElement annotatedElement = descriptor.getAnnotatedElement();
		Resource resource =AnnotationUtils.findAnnotation(annotatedElement, Resource.class);
		if (resource!=null&&StringUtils.isNotEmpty(resource.name())) {
			return factory.getBean(resource.name());
		}
		return null;
	}

	private Object parseValue(Object value) {
		if (value instanceof String) {
			String strVal = (String) value;
			try {
				Object val = ExpressionUtils.parseComplexExpression(strVal);
				val = factory.getContext().getPropertyResolver().resolvePlaceholders(val.toString());
				if (val != null && StringUtils.isNotEmpty(val.toString())) {
					strVal = val.toString();
				}
			} catch (Exception e) {
			}
			value = strVal;
		}
		return value;
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private Object resolveMultipleBeans(DependencyDescriptor descriptor, String beanName,
			Set<String> autowiredBeanNames) {
		Class<?> type = descriptor.getDependencyType();
		Object entity = null;
		if ((entity = descriptor.getField()) == null) {
			entity = descriptor.getMethodParameter();
		}
		if (Stream.class.isAssignableFrom(type)) {
			List<Class<?>> generics = getGenerics(Stream.class, entity);
			Map<String, Object> matchingBeans = findAutowireCandidates(beanName, generics.get(0), descriptor);
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
			Map<String, Object> matchingBeans = findAutowireCandidates(beanName, componentType, descriptor);
			if (matchingBeans.isEmpty()) {
				return null;
			}
			if (autowiredBeanNames != null) {
				autowiredBeanNames.addAll(matchingBeans.keySet());
			}
			Collection beans = matchingBeans.values();
			Object result = beans.toArray((Object[]) Array.newInstance(componentType, 0));
			if (factory.getDependencyComparator() != null && result instanceof Object[]) {
				Arrays.sort((Object[]) result, factory.getDependencyComparator());
			}
			return result;
		} else if (Collection.class.isAssignableFrom(type)) {
			List<Class<?>> list = getGenerics(Collection.class, entity);
			Map<String, Object> matchingBeans = findAutowireCandidates(beanName, list.get(0), descriptor);
			if (matchingBeans.isEmpty()) {
				return null;
			}
			if (autowiredBeanNames != null) {
				autowiredBeanNames.addAll(matchingBeans.keySet());
			}
			Collection result = (Collection) makeMutiTypeInstance(type);
			result.addAll(matchingBeans.values());
			if (factory.getDependencyComparator() != null && result instanceof List) {
				((List<?>) result).sort(factory.getDependencyComparator());
			}
			return result;
		} else if (Map.class.isAssignableFrom(type)) {
			List<Class<?>> generics = getGenerics(Map.class, entity);
			if (!generics.get(0).isAssignableFrom(String.class)) {
				return null;
			}
			Map<String, Object> matchingBeans = findAutowireCandidates(beanName, generics.get(1), descriptor);
			if (matchingBeans.isEmpty()) {
				return null;
			}
			if (autowiredBeanNames != null) {
				autowiredBeanNames.addAll(matchingBeans.keySet());
			}
			Map result = (Map) makeMutiTypeInstance(type);
			result.putAll(matchingBeans);
			return result;
		}
		return null;

	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	private <T> T makeMutiTypeInstance(Class<? extends T> type) {
		try {
			if (!type.isInterface()) {
				return type.newInstance();
			} else if (List.class.isAssignableFrom(type)) {
				return (T) new ArrayList();
			} else if (Map.class.isAssignableFrom(type)) {
				return (T) new HashMap();
			} else if (Set.class.isAssignableFrom(type) || Collection.class.isAssignableFrom(type)) {
				return (T) new HashSet();
			}
			return null;
		} catch (Exception e) {
			throw new SimpleException("MutiType build failed!", e);
		}
	}

	private List<Class<?>> getGenerics(Class<?> declare, Object entity) {
		Map<Set<ClassGenericParameter>, Class<?>> map = buildGenericParameterMap(entity);
		return findGenericParameter(map, declare);
	}

	private final Map<Object, Map<Set<ClassGenericParameter>, Class<?>>> entityGenericParameterMapCache =
			new ConcurrentHashMap<>(256);

	private Map<Set<ClassGenericParameter>, Class<?>> buildGenericParameterMap(Object entity) {
		Map<Set<ClassGenericParameter>, Class<?>> map = entityGenericParameterMapCache.get(entity);
		if (map == null) {
			map = new HashMap<Set<ClassGenericParameter>, Class<?>>();
			if (entity != null) {
				if (entity instanceof Field) {
					Field f = (Field) entity;
					Type genericType = f.getGenericType();
					if (genericType instanceof ParameterizedType) {
						Type[] params = ((ParameterizedType) genericType).getActualTypeArguments();
						for (int j = 0; j < params.length; j++) {
							Type param = params[j];
							if (param instanceof Class<?>) {
								map.put(BeanUtils.findCorrespondParams(f.getType(), j), (Class<?>) param);
							}
						}
					}
					map.putAll(BeanUtils.getGenericMap(f.getType()));
				} else if (entity instanceof Parameter) {
					Parameter p = (Parameter) entity;
					Type genericType = p.getParameterizedType();
					if (genericType instanceof ParameterizedType) {
						Type[] params = ((ParameterizedType) genericType).getActualTypeArguments();
						for (int j = 0; j < params.length; j++) {
							Type param = params[j];
							if (param instanceof Class<?>) {
								map.put(BeanUtils.findCorrespondParams(p.getType(), j), (Class<?>) param);
							}
						}
					}
					map.putAll(BeanUtils.getGenericMap(p.getType()));
				}
			}
		}
		return map;
	}

	private List<Class<?>> findGenericParameter(Map<Set<ClassGenericParameter>, Class<?>> map, Class<?> declare) {
		Type[] types = declare.getTypeParameters();
		List<Class<?>> list = new ArrayList<Class<?>>(types.length);
		for (int i = 0; i < types.length; i++) {
			Class<?> type = null;
			for (Entry<Set<ClassGenericParameter>, Class<?>> entry : map.entrySet()) {
				if (entry.getKey().contains(new ClassGenericParameter(declare, i))) {
					type = entry.getValue();
				}
			}
			if (type == null) {
				type = Object.class;
			}
			list.add(type);
		}
		return list;
	}

	private Map<String, Object> findAutowireCandidates(String beanName, Class<?> requiredType,
			DependencyDescriptor descriptor) {

		Set<String> candidateNames = factory.getBeanNamesForType(requiredType);
		Map<String, Object> result = new LinkedHashMap<>(candidateNames.size());
		for (Map.Entry<Class<?>, Object> classObjectEntry : factory.resolvableDependencies.entrySet()) {
			Class<?> autowiringType = classObjectEntry.getKey();
			if (autowiringType.isAssignableFrom(requiredType)) {
				Object autowiringValue = classObjectEntry.getValue();
				if (requiredType.isInstance(autowiringValue)) {
					result.put(ObjectUtils.identityToString(autowiringValue), autowiringValue);
					return result;
				}
			}
		}

		if (candidateNames.size() == 1 && candidateNames.contains(beanName)) {
			// self refer
			result.put(beanName, factory.getSingleton(beanName));
		} else {
			if (candidateNames.contains(beanName)) {
				candidateNames.remove(beanName);
			}
			for (String candidate : candidateNames) {
				if (factory.isAutowireCandidate(candidate, descriptor)) {
					result.put(candidate, factory.getBean(candidate));
				}
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
					|| factory.getAliasesArray(candidateName).contains(descriptor.getDependencyName())) {
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
						throw new SimpleException(
								"More than one 'primary' bean found among candidates: " + candidates.keySet());
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
				Integer order = null;
				if (factory.containsBean(candidateBeanName)) {
					order = factory.getBeanDefinition(candidateBeanName).getBeanOrder();
				}
				if (order != null) {
					if (highestPriorityBeanName != null) {
						if (order.equals(highestPriority)) {
							throw new SimpleException("Multiple beans found with the same priority ('" + highestPriority
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
		LargeStoreRoom<DependencyDescriptor, String, Set<String>, Object, Object> storeRoom =
				(LargeStoreRoom<DependencyDescriptor, String, Set<String>, Object, Object>) factory.currentStoreRoom
						.get();
		Object bean = doResolveDependency(storeRoom.getX(), storeRoom.getY(), storeRoom.getZ());
		storeRoom.setM(bean);
	}

}
