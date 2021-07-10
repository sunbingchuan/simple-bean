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

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

import com.chuan.simple.bean.annotation.Bean;
import com.chuan.simple.bean.annotation.Configuration;
import com.chuan.simple.bean.core.SimpleContext;
import com.chuan.simple.bean.core.build.builder.Builder;
import com.chuan.simple.bean.core.info.ClassInfo;
import com.chuan.simple.bean.core.info.MethodInfo;

public class ConfigurationProcessor extends AnnotationProcessor
        implements Processor {

    private final Set<MethodInfo> beanMethods = new LinkedHashSet<>();

    public ConfigurationProcessor(SimpleContext context) {
        super(context);
    }

    @Override
    public void processBuilders(Collection<Builder<?>> builders) {
        for (Builder<?> builder : builders) {
            ClassInfo classInfo = builder.getClassInfo();
            if (classInfo != null && classInfo
                    .isAnnotated(Configuration.class.getName())) {
                for (MethodInfo method : classInfo
                        .getAnnotatedMethods(Bean.class.getName())) {
                    parseBeanMethod(method, builder);
                }
            }
        }
    }

    public Set<MethodInfo> getBeanMethods() {
        return beanMethods;
    }

    public void addBeanMethods(MethodInfo metaData) {
        beanMethods.add(metaData);
    }

}
