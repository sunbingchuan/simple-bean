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
package com.chuan.simple.bean.core.element.entity;

import java.lang.reflect.Field;

public class FieldElement extends Element {

    private Field field;

    private Class<?> declaringClass;

    private String declaringClassName;

    public FieldElement(String declaringClassName, String name) {
        this.declaringClassName = declaringClassName;
        this.name = name;
    }

    public FieldElement(Field field) {
        this.field = field;
        this.name = field.getName();
        this.type = field.getType();
        this.typeName = this.type.getName();
        this.declaringClass = field.getDeclaringClass();
        this.declaringClassName = this.declaringClass.getName();
    }

    public Field getField() {
        return field;
    }

    public void setField(Field field) {
        this.field = field;
    }

    public Class<?> getDeclaringClass() {
        return declaringClass;
    }

    public void setDeclaringClass(Class<?> declaringClass) {
        this.declaringClass = declaringClass;
    }

    public String getDeclaringClassName() {
        return declaringClassName;
    }

    public void setDeclaringClassName(String declaringClassName) {
        this.declaringClassName = declaringClassName;
    }

}
