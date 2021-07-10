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
package com.chuan.simple.bean.core.info;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import com.chuan.simple.helper.clazz.ClassHelper;

/**
 * A AnnotationVisitor that can save annotation info.
 */
public class AnnotationInfo extends AnnotationVisitor {

    private static final Log LOG = LogFactory.getLog(AnnotationInfo.class);

    private final Map<String, Object> attributes = new HashMap<>();
    
    private String annotationClassName;

    private Class<?> annotationClass;

    public String getAnnotationClassName() {
        return annotationClassName;
    }

    public void setAnnotationClassName(String annotationClassName) {
        this.annotationClassName = annotationClassName;
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }

    public Object getAttribute(String key) {
        return attributes.get(key);
    }

    public void setAttribute(String key, Object value) {
        attributes.put(key, value);
    }

    public Class<?> getAnnotationClass() {
        return annotationClass;
    }

    public void setAnnotationClass(Class<?> annotationClass) {
        this.annotationClass = annotationClass;
    }

    public AnnotationInfo(String desc) {
        super(Opcodes.ASM8);
        this.annotationClassName = Type.getType(desc).getClassName();
        this.annotationClass = ClassHelper.forName(this.annotationClassName);
    }

    @Override
    public void visit(String attributeName, Object attributeValue) {
        this.attributes.put(attributeName, attributeValue);
    }

    @Override
    public AnnotationVisitor visitAnnotation(String attributeName,
            String asmTypeDescriptor) {
        AnnotationInfo annotationInfo = new AnnotationInfo(asmTypeDescriptor);
        this.attributes.put(attributeName, annotationInfo);
        return annotationInfo;
    }

    @Override
    public AnnotationVisitor visitArray(String attributeName) {
        List<Object> list = new ArrayList<>();
        this.attributes.put(attributeName, list);
        return new AnnotationVisitor(Opcodes.ASM8) {
            @Override
            public void visit(String name, Object value) {
                list.add(value);
            }
        };
    }

    @Override
    public void visitEnum(String attributeName, String asmTypeDescriptor,
            String attributeValue) {
        Object newValue = getEnumValue(asmTypeDescriptor, attributeValue);
        visit(attributeName, newValue);
    }

    protected Object getEnumValue(String typeDescriptor,
            String attributeValue) {
        Object valueToUse = attributeValue;
        try {
            Class<?> enumType = ClassHelper
                    .forName(Type.getType(typeDescriptor).getClassName());
            Field enumConstant = enumType.getDeclaredField(attributeValue);
            if (enumConstant != null) {
                enumConstant.setAccessible(true);
                valueToUse = enumConstant.get(null);
            }
        } catch (Exception e) {
            LOG.info(
                    "Failed to load enum type while reading annotation metadata",
                    e);
        }
        return valueToUse;
    }

}
