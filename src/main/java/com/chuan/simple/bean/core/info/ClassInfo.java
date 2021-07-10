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

import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import com.chuan.simple.helper.clazz.BuilderNameHelper;
import com.chuan.simple.helper.common.StringHelper;

/**
 * A ClassVisitor that can save class info.
 */
public class ClassInfo extends ClassVisitor {


    protected final Map<String, AnnotationInfo> annotations = new HashMap<>();

    protected final Map<String, MethodInfo> methods = new HashMap<>();

    protected final Map<String, FieldInfo> fields = new HashMap<>();

    protected int modifier;

    protected String className;

    protected String enclosingClassName;

    protected boolean independentInnerClass;

    protected String superClassName;

    protected String[] interfaces = new String[0];

    protected Set<String> memberClassNames = new LinkedHashSet<>(4);

    public ClassInfo() {
        super(Opcodes.ASM8);
    }
    
    @Override
    public void visit(int version, int access, String name, String signature,
            String supername, String[] interfaces) {
        this.className = BuilderNameHelper.toClassName(name);
        this.modifier = access;
        if (supername != null && !Modifier.isInterface(this.modifier)) {
            this.superClassName = BuilderNameHelper.toClassName(supername);
        }
        this.interfaces = new String[interfaces.length];
        for (int i = 0; i < interfaces.length; i++) {
            this.interfaces[i] = BuilderNameHelper.toClassName(interfaces[i]);
        }
    }

    @Override
    public void visitOuterClass(String owner, String name, String desc) {
        this.enclosingClassName = BuilderNameHelper.toClassName(owner);
    }

    @Override
    public void visitInnerClass(String name, String outerName, String innerName,
            int access) {
        if (outerName != null) {
            String fqName = BuilderNameHelper.toClassName(name);
            String fqOuterName = BuilderNameHelper.toClassName(outerName);
            if (this.className.equals(fqName)) {
                this.enclosingClassName = fqOuterName;
                this.independentInnerClass =
                        ((access & Opcodes.ACC_STATIC) != 0);
            } else if (this.className.equals(fqOuterName)) {
                this.memberClassNames.add(fqName);
            }
        }
    }

    @Override
    public AnnotationVisitor visitAnnotation(final String desc,
            boolean visible) {
        AnnotationInfo annotationInfo = new AnnotationInfo(desc);
        this.annotations.put(Type.getType(desc).getClassName(), annotationInfo);
        return annotationInfo;
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor,
            String signature, String[] exceptions) {
        if ((access & Opcodes.ACC_BRIDGE) != 0) {
            return super.visitMethod(access, name, descriptor, signature,
                    exceptions);
        }
        MethodInfo methodInfo = new MethodInfo(name, access, descriptor, this);
        this.methods.put(name + StringHelper.VERTICAL_LINE + descriptor,
                methodInfo);
        return methodInfo;
    }

    @Override
    public FieldVisitor visitField(int access, String name, String descriptor,
            String signature, Object value) {
        FieldInfo fieldInfo = new FieldInfo(access, name, descriptor, this);
        this.fields.put(name, fieldInfo);
        return fieldInfo;
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public String getEnclosingClassName() {
        return enclosingClassName;
    }

    public void setEnclosingClassName(String enclosingClassName) {
        this.enclosingClassName = enclosingClassName;
    }

    public boolean isIndependentInnerClass() {
        return independentInnerClass;
    }

    public void setIndependentInnerClass(boolean independentInnerClass) {
        this.independentInnerClass = independentInnerClass;
    }

    public String getSuperClassName() {
        return superClassName;
    }

    public void setSuperClassName(String superClassName) {
        this.superClassName = superClassName;
    }

    public String[] getInterfaces() {
        return interfaces;
    }

    public void setInterfaces(String[] interfaces) {
        this.interfaces = interfaces;
    }

    public Set<String> getMemberClassNames() {
        return memberClassNames;
    }

    public void setMemberClassNames(Set<String> memberClassNames) {
        this.memberClassNames = memberClassNames;
    }

    public boolean hasAnnotatedMethods(String annotationClasssName) {
        for (MethodInfo methodInfo : this.methods.values()) {
            if (methodInfo.isAnnotated(annotationClasssName)) {
                return true;
            }
        }
        return false;
    }

    public Set<MethodInfo> getAnnotatedMethods(String annotationClassName) {
        Set<MethodInfo> annotatedMethods = new HashSet<>();
        for (MethodInfo methodInfo : this.methods.values()) {
            if (methodInfo.isAnnotated(annotationClassName)) {
                annotatedMethods.add(methodInfo);
            }
        }
        return annotatedMethods;
    }

    public boolean hasAnnotatedFields(String annotationClasssName) {
        for (FieldInfo fieldInfo : this.fields.values()) {
            if (fieldInfo.isAnnotated(annotationClasssName)) {
                return true;
            }
        }
        return false;
    }

    public Set<FieldInfo> getAnnotatedFields(String annotationClassName) {
        Set<FieldInfo> annotatedFields = new HashSet<>();
        for (FieldInfo fieldInfo : this.fields.values()) {
            if (fieldInfo.isAnnotated(annotationClassName)) {
                annotatedFields.add(fieldInfo);
            }
        }
        return annotatedFields;
    }

    public Map<String, AnnotationInfo> getAnnotations() {
        return annotations;
    }

    public AnnotationInfo getAnnotation(String annotationClassName) {
        return annotations.get(annotationClassName);
    }

    public boolean isAnnotated(String annotationClassName) {
        return annotations.containsKey(annotationClassName);
    }

    public Map<String, MethodInfo> getMethods() {
        return methods;
    }

    public Map<String, FieldInfo> getFields() {
        return fields;
    }

    public int getModifier() {
	return modifier;
    }

    public void setModifier(int modifier) {
	this.modifier = modifier;
    }
    
}
