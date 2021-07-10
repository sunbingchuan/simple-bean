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

import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.chuan.simple.helper.common.StringHelper;
import com.chuan.simple.helper.method.MethodHelper;
import com.chuan.simple.helper.method.ParameterHelper;
import com.chuan.simple.bean.core.build.builder.ConstructorBuilder;
import com.chuan.simple.bean.core.element.entity.Element;
import com.chuan.simple.bean.core.element.entity.MethodParameterElement;

public class MethodParameterInstaller extends
        ParameterInstaller<ConstructorBuilder<?>, MethodParameterElement> {

    @Override
    public void install(ConstructorBuilder<?> builder,
            List<MethodParameterElement> elements, String period) {
        if (!StringHelper.equals(period, PERIOD_AFTER_CREATE)) {
            return;
        }
        ElementBag bag = new ElementBag(builder);
        for (MethodParameterElement methodParameterElement : elements) {
            if (!bag.add(methodParameterElement)) {
                bag.install();
                bag.add(methodParameterElement);
            }
        }
        bag.install();
    }

    private Map<String, List<Executable>> methodsByName(Class<?> clazz) {
        Map<String, List<Executable>> methodsByName = new HashMap<>();
        for (Method method : MethodHelper.getMethods(clazz)) {
            List<Executable> executables = methodsByName.computeIfAbsent(
                    method.getName(), key -> new ArrayList<>());
            executables.add(method);
        }
        for (List<Executable> executables : methodsByName.values()) {
            Collections.sort(executables,
                    (a, b) -> a.getParameterCount() - b.getParameterCount());
        }
        return methodsByName;
    }
    
    private final class ElementBag {
        private String currentMethodName;
        private final Map<String, List<Executable>> methodsByName;
        private List<Executable> currentMethods;
        private Executable currentMethod;
        private final Set<Integer> existIndexes = new HashSet<>();
        private final ConstructorBuilder<?> builder;

        private final List<MethodParameterElement> elements = new ArrayList<>();

        /**
         * The {@link MethodParameterElement} will be grouped and installed in
         * following situation: 
         * <p>1 {@link MethodParameterElement#declaringExecutableName} changed 
         * <p>2 {@link MethodParameterElement#declaringExecutable} changed 
         * <p>3 index repeat
         */
        private ElementBag(ConstructorBuilder<?> builder) {
            this.builder = builder;
            this.methodsByName = methodsByName(builder.getBuilderClass());
        }

        private boolean add(MethodParameterElement element) {
            if (this.currentMethodName == null) {
                this.currentMethodName = element.getDeclaringExecutableName();
                this.currentMethods = this.methodsByName.get(currentMethodName);
            }
            if (this.currentMethod == null
                    && element.getDeclaringExecutable() != null) {
                this.currentMethod = element.getDeclaringExecutable();
            }
            if (!this.currentMethodName
                    .equals(element.getDeclaringExecutableName())
                    || (this.currentMethod != null && !this.currentMethod
                            .equals(element.getDeclaringExecutable()))) {
                return false;
            }
            if (element.getParameterIndex() != null
                    && !this.existIndexes.add(element.getParameterIndex())) {
                return false;
            }
            this.elements.add(element);
            return true;
        }

        private void install() {
            List<Element> params = new ArrayList<>();
            parseParameters(this.elements, params);
            Executable method = this.currentMethod;
            if (method != null) {
                this.builder.setExecutableParameters(method, params);
                return;
            }
            @SuppressWarnings("unchecked")
            List<Object> paramValues =
                    (List<Object>) parseValue(builder, params);
            Class<?>[] currentParamTypes =
                    ParameterHelper.getParameterTypes(paramValues.toArray());
            for (Executable m : currentMethods) {
                if (m.getParameterCount() >= paramValues.size()) {
                    Class<?>[] correspondingParamTypes =
                            new Class<?>[paramValues.size()];
                    System.arraycopy(m.getParameterTypes(), 0,
                            correspondingParamTypes, 0, paramValues.size());
                    if (ParameterHelper.paramsFit(currentParamTypes,
                            correspondingParamTypes)) {
                        if (method == null
                                || m.getParameterCount() < method
                                        .getParameterCount()
                                || (m.getParameterCount() == method
                                        .getParameterCount()
                                        && !ParameterHelper.paramsEqual(
                                                m.getParameterTypes(),
                                                method.getParameterTypes())
                                        && ParameterHelper.paramsFit(
                                                m.getParameterTypes(),
                                                method.getParameterTypes()))) {
                            // Use the method whose parameter count and 
                        	// parameter types nearer the parameter values.
                            method = m;
                        }
                    }
                }
            }
            this.builder.setExecutableParameters(method, params);
            clear();
        }

        private void clear() {
            this.currentMethodName = null;
            this.elements.clear();
            this.existIndexes.clear();
            this.currentMethod = null;
            this.currentMethods = null;
        }

    }
    
}
