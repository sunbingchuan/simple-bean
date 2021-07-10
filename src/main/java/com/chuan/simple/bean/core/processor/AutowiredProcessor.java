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
package com.chuan.simple.bean.core.processor;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;

import com.chuan.simple.helper.common.ObjectHelper;
import com.chuan.simple.helper.field.FieldHelper;
import com.chuan.simple.helper.method.MethodHelper;
import com.chuan.simple.bean.core.SimpleContext;
import com.chuan.simple.bean.core.build.builder.Builder;
import com.chuan.simple.bean.core.element.entity.Element;

public class AutowiredProcessor extends AnnotationProcessor
        implements Processor {

    public AutowiredProcessor(SimpleContext context) {
        super(context);
    }

    @Override
    public void processBuilders(Collection<Builder<?>> builders) {
        for (Builder<?> builder : builders) {
            processBuilder(builder);
        }
    }

    private void processBuilder(Builder<?> builder) {
        Class<?> builderClass = builder.getBuilderClass();
        for (Field field : FieldHelper.getFields(builderClass)) {
            Element element = parseAutowiredField(field);
            if (element != null) {
                builder.addElement(element);
            }
        }
        for (Method method : MethodHelper.getMethods(builderClass, false)) {
            List<Element> elements = parseAutowiredExecutable(method, false);
            if (ObjectHelper.isNotEmpty(elements)) {
                builder.getElements().addAll(elements);
            }
        }
        // Only one constructor with annotation {@link Autowired}
        // will be in effect.
        for (Constructor<?> ctor : builderClass.getDeclaredConstructors()) {
            List<Element> elements = parseAutowiredExecutable(ctor, true);
            if (ObjectHelper.isNotEmpty(elements)) {
                builder.getElements().addAll(elements);
                break;
            }
        }
    }

}
