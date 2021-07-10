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
package com.chuan.simple.bean.core.element.installer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.chuan.simple.helper.common.StringHelper;
import com.chuan.simple.bean.core.SimpleContext;
import com.chuan.simple.bean.core.build.builder.Builder;
import com.chuan.simple.bean.core.build.procedure.CompoundProcedure;
import com.chuan.simple.bean.core.element.entity.Element;
import com.chuan.simple.bean.exception.SimpleBuildException;

/**
 * The installer of {@link Element}.
 * @param <B>
 *            The specific Builder type.
 * @param <T>
 *            The specific Element type.
 */
public abstract class ElementInstaller<B extends Builder<?>, T extends Element> {

    /**
     * Define the period of installation. Before the bean instance create.
     */
    public static final String PERIOD_BEFORE_CREATE = "before-create";
    /**
     * Define the period of installation. After the bean instance create.
     */
    public static final String PERIOD_AFTER_CREATE = "after-create";

    public abstract void install(B builder, List<T> elements, String period);

    public void parse(B owner, Element element) {
        if (element.getParsedValue() != null) {
            return;
        }
        SimpleContext context = owner.getContext();
        Object value = element.getValue();
        if (value != null) {
            value = parseValue(owner, value);
        }
        if (value!=null) {
            owner.privates.getParsedElements().add(element);
            element.setParsedValue(value);
            return;
        }
        Builder<?> elementBuilder = element.getBuilder();
        if (elementBuilder == null
                && StringHelper.isNotEmpty(element.getBuilderName())) {
            elementBuilder = context.getBuilder(element.getBuilderName());
        }
        if (elementBuilder == null && element.getType() != null) {
            elementBuilder = context.getBuilder(element.getType());
        }
        if (elementBuilder != null) {
            element.setBuilder(elementBuilder);
            value = elementBuilder.build();
        } else if (element.getType() != null) {
            CompoundProcedure compoundProcedure =
                    owner.getProcedure(CompoundProcedure.class);
            value = compoundProcedure.tryCompound(element.getType());
        }
        if (element.isRequired() && value == null) {
            throw new SimpleBuildException(
                    "Install required element " + element + " failed");
        }
        owner.privates.getParsedElements().add(element);
        element.setParsedValue(value);
    }

    @SuppressWarnings("unchecked")
    public Object parseValue(B owner, Object value) {
        if (value != null) {
            if (value instanceof Map) {
                Map<Object, Object> parsedValue = new HashMap<>();
                Map<Object, Object> map = (Map<Object, Object>) value;
                for (Entry<Object, Object> entry : map.entrySet()) {
                    Object v = parseValue(owner, entry.getValue());
                    Object k = parseValue(owner, entry.getKey());
                    parsedValue.put(k, v);
                }
                value = parsedValue;
            } else if (value instanceof List) {
                List<Object> parsedValue = new ArrayList<>();
                List<Object> list = (List<Object>) value;
                for (Object o : list) {
                    parsedValue.add(parseValue(owner, o));
                }
                value = parsedValue;
            } else if (value instanceof Set) {
                Set<Object> parsedValue = new HashSet<>();
                Set<Object> list = (Set<Object>) value;
                for (Object o : list) {
                    parsedValue.add(parseValue(owner, o));
                }
                value = parsedValue;
            } else if (value instanceof Element[]) {
                Element[] elements = (Element[]) value;
                Object[] parsedValue = new Object[elements.length];
                for (int i = 0; i < elements.length; i++) {
                    Element element = elements[i];
                    parse(owner, element);
                    parsedValue[i] = element.getParsedValue();
                }
                value = parsedValue;
            } else if (value instanceof Element) {
                Element element = (Element) value;
                parse(owner, element);
                value = element.getParsedValue();
            }
        }
        return value;
    }

    
    @Override
    public int hashCode() {
        return this.getClass().hashCode();
    }
    
}
