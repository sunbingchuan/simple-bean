package com.bc.simple.bean.core.workshop;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import com.bc.simple.bean.common.util.BeanUtils;
import com.bc.simple.bean.common.util.Constant;
import com.bc.simple.bean.common.util.StringUtils;
import com.bc.simple.bean.core.BeanFactory;

public class ParserValueWorkshop extends Workshop {

	
	public ParserValueWorkshop(BeanFactory beanFactory) {
		super(beanFactory);
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
				beanName = factory.findBeanNameByType(classType);
			}
			factory.registerDependentBean(createdBeanName, beanName);
			return factory.getBean(beanName);
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

	@SuppressWarnings("unchecked")
	@Override
	public void produceWorkshop() {
		StoreRoom< String,Map<String,Object>,Object> storeRoom =
				(StoreRoom< String,Map<String,Object>,Object>) factory.currentStoreRoom.get();
		Object bean = parseValue(storeRoom.getX(), storeRoom.getY());
		storeRoom.setZ(bean);
	}
	
	
}
