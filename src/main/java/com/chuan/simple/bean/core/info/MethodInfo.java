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
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import com.chuan.simple.helper.method.ParameterHelper;

/**
 * A MethodVisitor that can save method info.
 */
public class MethodInfo extends MethodVisitor {

    protected final String methodName;

    protected final int modifier;

    protected final String returnTypeName;

    protected final String[] parameterTypeNames;

    protected final ClassInfo parent;

    protected final Map<String, AnnotationInfo> annotations = new HashMap<>();

    public MethodInfo(String methodName, int access, String desc,
            ClassInfo parent) {
        super(Opcodes.ASM8);
        this.methodName = methodName;
        this.modifier = access;
        this.returnTypeName = Type.getReturnType(desc).getClassName();
        this.parameterTypeNames = ParameterHelper.getParameterTypeNames(desc);
        this.parent = parent;
    }

    @Override
    public AnnotationVisitor visitAnnotation(final String desc,
            boolean visible) {
        AnnotationInfo annotationInfo = new AnnotationInfo(desc);
        this.annotations.put(Type.getType(desc).getClassName(), annotationInfo);
        return annotationInfo;
    }

    public String getMethodName() {
        return this.methodName;
    }

    public boolean isAnnotated(String annotationClassName) {
        return this.annotations.containsKey(annotationClassName);
    }

    public AnnotationInfo getAnnotationInfo(String annotationClassName) {
        return this.annotations.get(annotationClassName);
    }

    public String getReturnTypeName() {
        return this.returnTypeName;
    }

    public String[] getParameterTypeNames() {
        return parameterTypeNames;
    }

    public ClassInfo getParent() {
        return parent;
    }

}