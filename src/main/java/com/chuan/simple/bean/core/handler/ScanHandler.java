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
package com.chuan.simple.bean.core.handler;

import java.io.File;
import java.io.FileInputStream;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.objectweb.asm.ClassReader;

import com.chuan.simple.helper.annotation.AnnotationAttribute;
import com.chuan.simple.helper.annotation.AnnotationAttributeHelper;
import com.chuan.simple.helper.clazz.BuilderNameHelper;
import com.chuan.simple.helper.clazz.ClassHelper;
import com.chuan.simple.helper.common.PatternHelper;
import com.chuan.simple.helper.common.StringHelper;
import com.chuan.simple.helper.resource.PathHelper;
import com.chuan.simple.helper.resource.ResourceHelper;
import com.chuan.simple.bean.annotation.Component;
import com.chuan.simple.bean.core.SimpleContext;
import com.chuan.simple.bean.core.build.builder.Builder;
import com.chuan.simple.bean.core.build.builder.ConstructorBuilder;
import com.chuan.simple.bean.core.config.node.Node;
import com.chuan.simple.bean.core.info.AnnotationInfo;
import com.chuan.simple.bean.core.info.ClassInfo;
import com.chuan.simple.bean.core.processor.AutowiredProcessor;
import com.chuan.simple.bean.core.processor.ConfigurationProcessor;
import com.chuan.simple.bean.core.processor.ScanProcessor;
import com.chuan.simple.bean.exception.SimpleParseException;
import com.chuan.simple.constant.Constant;

/**
 * Handler to deal with {@link Node} 'scan'.
 * <p>
 * The function of {@link Node} 'scan' is scanning and parsing the the class
 * with right annotation automatically.
 */
public class ScanHandler implements Handler {

    private SimpleContext context;

    private ConfigurationProcessor configurationProcessor;

    private AutowiredProcessor autowiredProcessor;

    private ScanProcessor scanProcessor;

    public ScanHandler() {
    }

    @Override
    public void handle(Node cfg) {
        initProcessor();
        List<Builder<?>> builders = this.doScan(cfg);
        this.scanProcessor.getBuilders().addAll(builders);
        this.scanProcessor.processScanBuilders();
    }

    protected List<Builder<?>> doScan(Node element) {
        String basePackage = getBasePackage(element);
        String packageSearchPath = basePackage + PathHelper.FOLDER_SEPARATOR
                + ResourceHelper.DEFAULT_CLASS_RESOURCE_PATTERN;
        Set<File> resources = ResourceHelper.matchResources(packageSearchPath);
        List<Builder<?>> builders = new ArrayList<>();
        for (File file : resources) {
            try (FileInputStream inputStream = new FileInputStream(file)) {
                ClassReader classReader = new ClassReader(inputStream);
                ClassInfo classInfo = new ClassInfo();
                classReader.accept(classInfo, ClassReader.SKIP_DEBUG);
                if (!isComponent(classInfo)) {
                    continue;
                }
                ConstructorBuilder<?> builder =
                        new ConstructorBuilder<>(context);
                builder.setClassInfo(classInfo);
                builder.setClassName(classInfo.getClassName());
                Class<?> builderClass =
                        ClassHelper.forName(classInfo.getClassName());
                builder.setBuilderClass(builderClass);
                Map<Class<? extends Annotation>, AnnotationAttribute> annotationAttributes =
                        AnnotationAttributeHelper.from(builderClass);
                AnnotationAttribute componentAttribute =
                        annotationAttributes.get(Component.class);
                String builderName = null;
                if (componentAttribute != null) {
                    builderName = (String) componentAttribute
                            .getAttribute(Constant.ATTR_VALUE);
                }
                if (StringHelper.isEmpty(builderName)) {
                    builderName =
                            BuilderNameHelper.generateAnnotatedBuilderName(
                                    builder.getBuilderClass());
                    builderName = BuilderNameHelper.satisfiedName(builderName,
                            context::checkAndUseName);
                }
                builder.setBuilderName(builderName);
                builders.add(builder);
                this.context.addBuilder(builderName, builder);
            } catch (Exception e) {
                throw new SimpleParseException(
                        "Scan file  '" + file + "'  error", e);
            }
        }
        return builders;
    }

    private String getBasePackage(Node element) {
        String basePackage = element.attrString(Constant.ATTR_BASE_PACKAGE);
        if (StringHelper.isEmpty(basePackage)) {
            return StringHelper.EMPTY;
        }
        basePackage =
                basePackage.replaceAll(Pattern.quote(PatternHelper.DOUBLE_DOT),
                        PatternHelper.DOT_DOUBLE_ASTERISK);
        basePackage = StringHelper.classNameToResourcePath(basePackage);
        basePackage = PathHelper.cleanPath(basePackage);
        return basePackage;
    }

    private static final Class<?> COMPONENT_TYPE = Component.class;
    private static final Map<Class<?>, Boolean> componentTypeCache =
            new HashMap<>();

    private boolean isComponent(ClassInfo classInfo) {
        for (AnnotationInfo desc : classInfo.getAnnotations().values()) {
            Class<?> annotationClass = desc.getAnnotationClass();
            Boolean isComponent = componentTypeCache.get(annotationClass);
            if (isComponent == null) {
                if (annotationClass.equals(COMPONENT_TYPE)) {
                    isComponent = true;
                } else {
                    Map<Class<? extends Annotation>, AnnotationAttribute> annotationAttributes =
                            AnnotationAttributeHelper.from(annotationClass);
                    isComponent =
                            annotationAttributes.containsKey(Component.class);
                }
                componentTypeCache.put(annotationClass, isComponent);
            }
            if (isComponent) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void setContext(SimpleContext context) {
        this.context = context;
    }

    private void initProcessor() {
        if (this.autowiredProcessor == null) {
            synchronized (this) {
                if (this.autowiredProcessor == null) {
                    this.autowiredProcessor = new AutowiredProcessor(context);
                    context.addProcessor(this.autowiredProcessor);
                } 
            }
        }
        if (this.configurationProcessor == null) {
            synchronized (this) {
                if (this.configurationProcessor == null) {
                    this.configurationProcessor = new ConfigurationProcessor(context);
                    context.addProcessor(this.configurationProcessor);
                } 
            }
        }
        if (this.scanProcessor == null) {
            synchronized (this) {
                if (this.scanProcessor == null) {
                    this.scanProcessor = new ScanProcessor(context);
                } 
            }
        }
    }
}
