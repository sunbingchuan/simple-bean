/*
 * Copyright 2018-2021 Bingchuan Sun.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.chuan.simple.bean.core.build.procedure;

import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Stream;

import com.chuan.simple.helper.common.ObjectHelper;
import com.chuan.simple.helper.common.StringHelper;
import com.chuan.simple.helper.generic.GenericType;
import com.chuan.simple.helper.generic.GenericTypeHelper;
import com.chuan.simple.helper.method.ParameterHelper;
import com.chuan.simple.bean.core.SimpleContext;
import com.chuan.simple.bean.core.build.builder.Builder;
import com.chuan.simple.bean.exception.SimpleCompoundException;

public class CompoundProcedure extends BuildProcedure {

    public CompoundProcedure(Builder<?> builder) {
        super(builder);
    }

    public Object tryCompound(Class<?> type) {
        return compound(type, false);
    }

    public Object compound(Class<?> type) {
        return compound(type, true);
    }

    public Object compound(Field field) {
        return compound(field, field.getType(), field.getName());
    }

    public Object compound(Parameter parameter) {
        return compound(parameter, parameter.getType(),
                ParameterHelper.getParameterName(parameter));
    }

    protected Object compound(Class<?> type, boolean errorOnFailed) {
        Object bean = null;
        if (isMultipleType(type)) {
            bean = compoundMultipleType(null, type);
        } else {
            bean = builder.getContext().build(type, errorOnFailed);
        }
        return bean;
    }
    
    protected Object compound(Object element, Class<?> type,
            String builderName) {
        Object bean = null;
        if (isMultipleType(type)) {
            bean = compoundMultipleType(element, type);
        } else {
            if (StringHelper.isNotEmpty(builderName)) {
                bean = builder.getContext().build(builderName);
            } else {
                bean = builder.getContext().build(type);
            }
        }
        return bean;
    }
    
    public List<Object> compoundParameters(Executable executable,
            List<Object> paramValues) {
        SimpleContext context = builder.getContext();
        String[] parameterNames = ParameterHelper.getParameterNames(executable);
        Parameter[] parameters = ParameterHelper.getParameters(executable);
        for (int i = 0; i < parameterNames.length; i++) {
            String parameterName = parameterNames[i];
            Object parameterValue = null;
            if (paramValues.size() <= i) {
                paramValues.add(null);
            } else {
                parameterValue = paramValues.get(i);
            }
            if (parameterValue != null) {
                continue;
            }
            if (!builder.isAutowiredExecutable()) {
                continue;
            }
            if (StringHelper.isNotEmpty(parameterName)) {
                parameterValue = context.tryBuild(parameterName);
            }
            if (parameterValue == null) {
                parameterValue = tryCompound(parameters[i].getType());
            }
            if (parameterValue == null) {
                throw new SimpleCompoundException("Compound parameter '"
                        + parameters[i] + "' of executable '" + executable
                        + "' failed");
            }
            paramValues.set(i, parameterValue);
        }
        return paramValues;
    }

    

    protected boolean isMultipleType(Class<?> clazz) {
        boolean isMultipleType = false;
        if (clazz.isArray()) {
            isMultipleType = true;
        }
        if (!isMultipleType)
            for (Class<?> supportType : defaultActualMutipleType.keySet()) {
                if (supportType.isAssignableFrom(clazz)) {
                    isMultipleType = true;
                    break;
                }
            }
        return isMultipleType;
    }
    
    @SuppressWarnings({"unchecked", "rawtypes"})
    protected Object compoundMultipleType(Object element, Class<?> type) {
        Set<GenericType> genericTypes;
        if (element!=null) {
            genericTypes = GenericTypeHelper.getGenericTypes(element);
        }else {
            genericTypes = GenericTypeHelper.getGenericTypes(type);
        }
        if (Stream.class.isAssignableFrom(type)) {
            GenericType genericType = GenericTypeHelper
                    .getGenericType(genericTypes, Stream.class, 0);
            Class<?> actualType = genericType.getActualType();
            List<Object> beans = getSortedBeans(actualType);
            return beans.stream();
        } else if (type.isArray()) {
            Class<?> componentType = type.getComponentType();
            List<Object> beans = getSortedBeans(componentType);
            return ObjectHelper.newArray(componentType, beans.toArray());
        } else if (Collection.class.isAssignableFrom(type)) {
            GenericType genericType = GenericTypeHelper
                    .getGenericType(genericTypes, Collection.class, 0);
            Collection collection = multipleInstance(Collection.class, type);
            List<Object> beans = getSortedBeans(genericType.getActualType());
            collection.addAll(beans);
            return collection;
        } else if (Map.class.isAssignableFrom(type)) {
            GenericType genericType = GenericTypeHelper
                    .getGenericType(genericTypes, Map.class, 1);
            Map map = multipleInstance(Map.class, type);
            Map<String, Object> beans = getBeans(genericType);
            map.putAll(beans);
            return map;
        }
        return null;
    }

    protected List<Object> getSortedBeans(GenericType genericType) {
        if (genericType==null){
            return new ArrayList<>();
        }
        return getSortedBeans(genericType.getClass());
    }
    
    protected List<Builder<?>> getSortedBuilders(Class<?> clazz) {
        Map<String, Builder<?>> builders =
                builder.getContext().getBuilders(clazz);
        List<Builder<?>> sortedBuilders = new ArrayList<>();
        sortedBuilders.addAll(builders.values());
        Collections.sort(sortedBuilders, (a, b) -> {
            return b.getOrder() - a.getOrder();
        });
        return sortedBuilders;
    }



    protected List<Object> getSortedBeans(Class<?> clazz) {
        List<Object> beans = new ArrayList<>();
        if (clazz==null){
            return beans;
        }
        List<Builder<?>> builders = getSortedBuilders(clazz);
        for (Builder<?> builder : builders) {
            beans.add(builder.build());
        }
        return beans;
    }

    protected Map<String,Object> getBeans(GenericType genericType){
        Map<String,Object> beans =new HashMap<>();
        if (genericType==null){
            return beans;
        }
        Map<String, Builder<?>> builders = builder.getContext().getBuilders(genericType.getActualType());
        for (Entry<String, Builder<?>> entry : builders.entrySet()) {
            beans.put(entry.getKey(), entry.getValue().build());
        }
        return beans;
    }
    
    protected static Map<Class<?>, Class<?>> defaultActualMutipleType =
            new HashMap<Class<?>, Class<?>>();

    static {
        defaultActualMutipleType.put(List.class, ArrayList.class);
        defaultActualMutipleType.put(Map.class, HashMap.class);
        defaultActualMutipleType.put(Set.class, HashSet.class);
        defaultActualMutipleType.put(Collection.class, ArrayList.class);
    }

    @SuppressWarnings("unchecked")
    public <T> T multipleInstance(Class<T> supportType, Class<?> actualType) {
        T instance = null;
        try {
            if (supportType.isAssignableFrom(actualType)) {
                if (actualType.isInterface()) {
                    Class<?> instanceType =  defaultActualMutipleType.get(actualType);
                    if (instanceType==null) {
                        instanceType =  defaultActualMutipleType.get(supportType);
                    }
                    if (!actualType.isAssignableFrom(instanceType)){
                        throw new SimpleCompoundException("Couldn't find suitable instance type for multiple type "+actualType);
                    }
                    instance = (T) instanceType.newInstance();
                }else{
                    instance = (T) actualType.newInstance();
                }
            }
        } catch (Exception e) {
            throw new SimpleCompoundException(
                    "Make multiple instance of " + actualType + " failed", e);
        }
        return  instance;
    }
    
    

}
