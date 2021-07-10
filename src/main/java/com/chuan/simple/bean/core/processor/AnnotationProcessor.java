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
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.chuan.simple.helper.annotation.AnnotationAttribute;
import com.chuan.simple.helper.annotation.AnnotationAttributeHelper;
import com.chuan.simple.helper.clazz.BuilderNameHelper;
import com.chuan.simple.helper.clazz.ClassHelper;
import com.chuan.simple.helper.common.StringHelper;
import com.chuan.simple.helper.method.MethodHelper;
import com.chuan.simple.helper.method.ParameterHelper;
import com.chuan.simple.bean.annotation.Alias;
import com.chuan.simple.bean.annotation.Automatic;
import com.chuan.simple.bean.annotation.Autowired;
import com.chuan.simple.bean.annotation.Bean;
import com.chuan.simple.bean.annotation.DependsOn;
import com.chuan.simple.bean.annotation.Order;
import com.chuan.simple.bean.annotation.Scope;
import com.chuan.simple.bean.core.SimpleContext;
import com.chuan.simple.bean.core.build.builder.Builder;
import com.chuan.simple.bean.core.build.builder.MethodBuilder;
import com.chuan.simple.bean.core.element.entity.BuildParameterElement;
import com.chuan.simple.bean.core.element.entity.Element;
import com.chuan.simple.bean.core.element.entity.FieldElement;
import com.chuan.simple.bean.core.element.entity.MethodParameterElement;
import com.chuan.simple.bean.core.element.entity.ParameterElement;
import com.chuan.simple.bean.core.info.MethodInfo;
import com.chuan.simple.constant.Constant;

public class AnnotationProcessor implements Processor {

    private static final Log log = LogFactory.getLog(AnnotationProcessor.class);

    protected SimpleContext context;

    public AnnotationProcessor(SimpleContext context) {
        this.context = context;
    }

    protected Element parseAutowiredField(Field field) {
        Map<Class<? extends Annotation>, AnnotationAttribute> attrs =
                AnnotationAttributeHelper.from(field);
        AnnotationAttribute annotationAttribute = attrs.get(Autowired.class);
        FieldElement fieldElement = null;
        if (annotationAttribute != null) {
            fieldElement = new FieldElement(field);
            parseAutowired(fieldElement, annotationAttribute);
        }
        return fieldElement;
    }

    protected List<Element> parseAutowiredExecutable(Executable executable,
            boolean isBuildExecutable) {
        List<Element> result = new ArrayList<>();
        Map<Class<? extends Annotation>, AnnotationAttribute> attrs =
                AnnotationAttributeHelper.from(executable);
        if (attrs.get(Bean.class) != null && !isBuildExecutable) {
            return result;
        }
        AnnotationAttribute autowiredAnnotationAttribute =
                attrs.get(Autowired.class);
        boolean methodAutowired = false;
        String value = null;
        boolean required = true;
        Class<?> type = null;
        String name = null;
        if (autowiredAnnotationAttribute != null) {
            methodAutowired = true;
            value = (String) autowiredAnnotationAttribute
                    .getAttribute(Constant.ATTR_VALUE);
            name = (String) autowiredAnnotationAttribute
                    .getAttribute(Constant.ATTR_NAME);
            type = (Class<?>) autowiredAnnotationAttribute
                    .getAttribute(Constant.ATTR_TYPE);
            required = (boolean) autowiredAnnotationAttribute
                    .getAttribute(Constant.ATTR_REQUIRED);
        }
        for (Parameter parameter : ParameterHelper.getParameters(executable)) {
            Map<Class<? extends Annotation>, AnnotationAttribute> ats =
                    AnnotationAttributeHelper.from(parameter);
            AnnotationAttribute attribute = ats.get(Autowired.class);
            if (methodAutowired || attribute != null) {
                ParameterElement parameterElement = null;
                if (isBuildExecutable) {
                    parameterElement = new BuildParameterElement(parameter);
                } else {
                    parameterElement = new MethodParameterElement(parameter);
                }
                result.add(parameterElement);
                if (type != null && !type.equals(Object.class)) {
                    parameterElement.setType(type);
                } else {
                    parameterElement.setType(parameter.getType());
                }
                if (attribute != null) {
                    parseAutowired(parameterElement, attribute);
                    continue;
                }
                if (StringHelper.isNotEmpty(value)) {
                    parameterElement.setValue(value);
                }
                if (StringHelper.isNotEmpty(name)) {
                    parameterElement.setBuilderName(name);
                }
                parameterElement.setRequired(required);
            }
        }
        return result;
    }

    protected void parseAutowired(Element element,
            AnnotationAttribute annotationAttribute) {
        String value =
                (String) annotationAttribute.getAttribute(Constant.ATTR_VALUE);
        if (StringHelper.isNotEmpty(value)) {
            element.setValue(value);
        }
        String builerName =
                (String) annotationAttribute.getAttribute(Constant.ATTR_NAME);
        if (StringHelper.isNotEmpty(builerName)) {
            element.setBuilderName(builerName);
        } else {
            element.setBuilderName(element.getName());
        }
        Class<?> type =
                (Class<?>) annotationAttribute.getAttribute(Constant.ATTR_TYPE);
        if (type != null && !type.equals(Object.class)) {
            element.setType(type);
        }
        boolean required = (boolean) annotationAttribute
                .getAttribute(Constant.ATTR_REQUIRED);
        element.setRequired(required);
    }

    protected void parseBeanMethod(MethodInfo method, Builder<?> builder) {
        try {
            Method executable = builder.getBuilderClass().getDeclaredMethod(
                    method.getMethodName(),
                    ClassHelper.forName(method.getParameterTypeNames()));
            MethodBuilder<?> methodBuilder = new MethodBuilder<>(context);
            methodBuilder.setClassName(method.getReturnTypeName());
            methodBuilder.setBuilderClass(
                    ClassHelper.forName(method.getReturnTypeName()));
            methodBuilder.setBuildExecutable(executable);
            methodBuilder.setOwnerClass(builder.getBuilderClass());
            methodBuilder.setMethodName(method.getMethodName());
            methodBuilder.setOwnerClassName(builder.getClassName());
            methodBuilder.setOwnerName(builder.getBuilderName());
            Map<Class<? extends Annotation>, AnnotationAttribute> attrs =
                    AnnotationAttributeHelper.from(executable);
            AnnotationAttribute beanAttr = attrs.get(Bean.class);
            List<Element> builderElements =
                    parseAutowiredExecutable(executable, true);
            methodBuilder.getElements().addAll(builderElements);
            String builderName = StringHelper
                    .toString(beanAttr.getAttribute(Constant.ATTR_VALUE));
            if (StringHelper.isEmpty(builderName)) {
                builderName = method.getMethodName();
            }
            builderName=BuilderNameHelper.satisfiedName(builderName, context::checkAndUseName);
            methodBuilder.setBuilderName(builderName);
            processBuilderAnnotation(methodBuilder, attrs);
            this.context.addBuilder(builderName, methodBuilder);
        } catch (Exception e) {
            log.debug("Parse bean method " + method + " failed", e);
        }
    }

    protected void processBuilderAnnotation(Builder<?> builder,
            Map<Class<? extends Annotation>, AnnotationAttribute> attrs) {
        parseDefaultAutowiredPattern(builder);
        parseScope(attrs, builder);
        parseAutomatic(attrs, builder);
        parseOrder(attrs, builder);
        parseDependsOn(attrs, builder);
        parseAlias(attrs, builder);
    }

    protected void parseDefaultAutowiredPattern(Builder<?> builder) {
        String executablePattern = this.context
                .getAttribute(Constant.ATTR_DEFAULT_AUTOWIRED_EXECUTABLES);
        if (StringHelper.isNotEmpty(executablePattern)) {
            String[] patterns = StringHelper.splitByDelimiter(executablePattern,
                    StringHelper.COMMA);
            builder.setAutowiredExecutable(
                    StringHelper.match(patterns, builder.getBuilderName()));
        }
        String fieldPattern = this.context
                .getAttribute(Constant.ATTR_DEFAULT_AUTOWIRED_FIELDS);
        if (StringHelper.isNotEmpty(fieldPattern)) {
            String[] patterns = StringHelper.splitByDelimiter(fieldPattern,
                    StringHelper.COMMA);
            builder.setAutowiredExecutable(
                    StringHelper.match(patterns, builder.getBuilderName()));
        }
    }

    protected void parseScope(
            Map<Class<? extends Annotation>, AnnotationAttribute> attrs,
            Builder<?> builder) {
        AnnotationAttribute annotationAttribute = attrs.get(Scope.class);
        if (annotationAttribute != null) {
            builder.setScope(StringHelper.toString(
                    annotationAttribute.getAttribute(Constant.ATTR_VALUE)));
        }
    }

    protected void parseAutomatic(
            Map<Class<? extends Annotation>, AnnotationAttribute> attrs,
            Builder<?> builder) {
        AnnotationAttribute annotationAttribute = attrs.get(Automatic.class);
        if (annotationAttribute != null) {
            boolean autoInit = (boolean) annotationAttribute
                    .getAttribute(Constant.ATTR_AUTO_INIT);
            builder.setAutoInit(autoInit);
            boolean autowiredField = (boolean) annotationAttribute
                    .getAttribute(Constant.ATTR_NAME_AUTOWIRED_FIELD);
            builder.setAutowiredField(autowiredField);
            boolean autowiredExecutable = (boolean) annotationAttribute
                    .getAttribute(Constant.ATTR_NAME_AUTOWIRED_EXECUTABLE);
            builder.setAutowiredExecutable(autowiredExecutable);
        }
    }

    protected void parseOrder(
            Map<Class<? extends Annotation>, AnnotationAttribute> attrs,
            Builder<?> builder) {
        AnnotationAttribute annotationAttribute = attrs.get(Order.class);
        if (annotationAttribute != null) {
            int order =
                    (int) annotationAttribute.getAttribute(Constant.ATTR_VALUE);
            builder.setOrder(order);
        }
    }

    protected void parseDependsOn(
            Map<Class<? extends Annotation>, AnnotationAttribute> attrs,
            Builder<?> builder) {
        AnnotationAttribute annotationAttribute = attrs.get(DependsOn.class);
        if (annotationAttribute != null) {
            String[] dependsOn = (String[]) annotationAttribute
                    .getAttribute(Constant.ATTR_VALUE);
            builder.setDependsOn(dependsOn);
        }
    }

    protected void parseAlias(
            Map<Class<? extends Annotation>, AnnotationAttribute> attrs,
            Builder<?> builder) {
        AnnotationAttribute annotationAttribute = attrs.get(Alias.class);
        if (annotationAttribute != null) {
            String aliasStr = StringHelper.toString(
                    annotationAttribute.getAttribute(Constant.ATTR_VALUE));
            if (!StringHelper.isEmpty(aliasStr)) {
                List<String> aliases =
                        Arrays.asList(StringHelper.splitByDelimiters(aliasStr,
                                Constant.MULTI_VALUE_ATTRIBUTE_DELIMITERS));
                builder.addAliases(aliases);
                this.context.registerAliases(builder.getBuilderName(), aliases);
            }
        }
    }
}
