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

import java.util.HashMap;
import java.util.Map;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import com.chuan.simple.helper.clazz.ClassHelper;

/**
 * A FieldVisitor that can save field info.
 */
public class FieldInfo extends FieldVisitor {

    protected final int modifier;

    protected final ClassInfo parent;

    protected final Map<String, AnnotationInfo> annotations = new HashMap<>();
    
    protected String fieldName;

    protected String fieldClassName;

    protected Class<?> fieldClass;

    public FieldInfo(int access, String name, String desc, ClassInfo parent) {
        super(Opcodes.ASM8);
        this.modifier = access;
        this.fieldName = name;
        this.parent = parent;
        this.fieldClassName = Type.getType(desc).getClassName();
        this.fieldClass = ClassHelper.forName(this.fieldClassName);
    }

    @Override
    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
        AnnotationInfo annotationInfo = new AnnotationInfo(desc);
        this.annotations.put(Type.getType(desc).getClassName(), annotationInfo);
        return annotationInfo;
    }

    public String getFieldName() {
        return fieldName;
    }

    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }

    public String getFieldClassName() {
        return fieldClassName;
    }

    public void setFieldClassName(String fieldClassName) {
        this.fieldClassName = fieldClassName;
    }

    public Class<?> getFieldClass() {
        return fieldClass;
    }

    public void setFieldClass(Class<?> fieldClass) {
        this.fieldClass = fieldClass;
    }

    public int getModifier() {
        return modifier;
    }

    public ClassInfo getParent() {
        return parent;
    }

    public Map<String, AnnotationInfo> getAnnotations() {
        return annotations;
    }

    public boolean isAnnotated(String annotationClassName) {
        return this.annotations.containsKey(annotationClassName);
    }

    public AnnotationInfo getAnnotationInfo(String annotationClassName) {
        return this.annotations.get(annotationClassName);
    }

}
