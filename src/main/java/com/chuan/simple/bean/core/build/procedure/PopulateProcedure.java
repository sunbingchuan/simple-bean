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

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.Map.Entry;

import com.chuan.simple.helper.field.FieldHelper;
import com.chuan.simple.bean.core.build.builder.Builder;
import com.chuan.simple.bean.core.element.entity.Element;
import com.chuan.simple.bean.core.element.entity.FieldElement;
import com.chuan.simple.bean.core.element.installer.ElementInstaller;
import com.chuan.simple.bean.exception.SimplePopulateException;

public class PopulateProcedure extends BuildProcedure
        implements ProcedureDependant {

    protected CompoundProcedure compoundProcedure;

    protected ProxyProcedure proxyProcedure;

    public PopulateProcedure(Builder<?> builder) {
        super(builder);
    }

    public Object populate(Object object) {
        initializeProcedure();
        doPopulate(object);
        object = proxyProcedure.optionalWrapBean(object);
        return object;
    }

    protected void doPopulate(Object object) {
        Class<?> builderClass = builder.getBuilderClass();
        Map<String, FieldElement> fields = builder.getFields();
        for (Entry<String, FieldElement> entry : builder.getFields()
                .entrySet()) {
            String fieldName = entry.getKey();
            FieldElement fieldElement = entry.getValue();
            Object value = getElementParsedValue(fieldElement);
            try {
                Field field = FieldHelper.getField(builderClass, fieldName);
                value = arrayCast(field.getType(), value);
                FieldHelper.setFieldValue(object, field, value);
            } catch (Exception e) {
                throw new SimplePopulateException("Set field '" + fieldName
                        + "' of bean '" + builder.getBuilderName() + "' failed",
                        e);
            }
        }
        if (builder.isAutowiredField()) {
            for (Field field : FieldHelper.getFields(builderClass)) {
                if (!fields.containsKey(field.getName())) {
                    Object fieldValue =
                            FieldHelper.getFieldValue(field, object);
                    if (fieldValue == null) {
                        Object fileValue = compoundProcedure.compound(field);
                        FieldHelper.setFieldValue(object, field, fileValue);
                    }
                }
            }
        }
    }

    @Override
    public void initializeProcedure() {
        if (this.compoundProcedure == null) {
            synchronized (this) {
                if (this.compoundProcedure == null) {
                    this.compoundProcedure =
                            builder.getProcedure(CompoundProcedure.class);
                }
            }
        }
        if (this.proxyProcedure == null) {
            synchronized (this) {
                if (this.proxyProcedure == null) {
                    this.proxyProcedure =
                            builder.getProcedure(ProxyProcedure.class);
                }
            }
        }
    }
    
    protected Object arrayCast(Class<?> type, Object value) {
        if (type.isArray()) {
            Class<?> componentType = type.getComponentType();
            int length = Array.getLength(value);
            Object array = Array.newInstance(componentType, length);
            for (int i = 0; i < length; i++) {
                Array.set(array, i,
                        arrayCast(componentType, Array.get(value, i)));
            }
            value = array;
        }
        return value;
    }

    protected Object getElementParsedValue(Element element) {
        @SuppressWarnings("unchecked")
        ElementInstaller<Builder<?>, Element> installer =
                (ElementInstaller<Builder<?>, Element>) builder.getContext()
                        .getElementInstaller(element.getClass());
        installer.parse(builder, element);
        return element.getParsedValue();
    }
    
    
}
