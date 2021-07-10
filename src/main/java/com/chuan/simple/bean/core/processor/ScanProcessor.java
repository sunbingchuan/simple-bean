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

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.chuan.simple.helper.annotation.AnnotationAttribute;
import com.chuan.simple.helper.annotation.AnnotationAttributeHelper;
import com.chuan.simple.bean.core.SimpleContext;
import com.chuan.simple.bean.core.build.builder.Builder;

public class ScanProcessor extends AnnotationProcessor implements Processor {

    protected final List<Builder<?>> builders = new ArrayList<>();

    public ScanProcessor(SimpleContext context) {
        super(context);
    }

    public void processScanBuilders() {
        for (Builder<?> builder : builders) {
            processScanBuilder(builder);
        }
    }

    public void processScanBuilder(Builder<?> builder) {
        Map<Class<? extends Annotation>, AnnotationAttribute> attrs =
                AnnotationAttributeHelper.from(builder.getBuilderClass());
        processBuilderAnnotation(builder, attrs);
    }

    public List<Builder<?>> getBuilders() {
        return builders;
    }

}
