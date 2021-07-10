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

import com.chuan.simple.helper.clazz.ClassHelper;
import com.chuan.simple.helper.common.StringHelper;
import com.chuan.simple.bean.core.build.builder.Builder;

/**
 * The bean building entity.
 */
public class Element {

    /**
     * The name of element, may be field name,method name,parameter name.
     */
    protected String name;
    protected Class<?> type;
    protected String typeName;
    protected Builder<?> builder;
    
    /**
     * The name of builder,may be same as {@link #name}.
     */
    protected String builderName;
    
    /**
     * The original value for this element,may be not parsed(multiple type).
     */
    protected Object value;
    
    /**
     * Whether this element is required.
     */
    private boolean required;
    
    /**
     * The cached and parsed value for this element,may be same as
     * {@link #value}.
     */
    protected ThreadLocal<Object> parsedValue=new ThreadLocal<>();

    public Element() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Class<?> getType() {
        return type;
    }

    public void setType(Class<?> type) {
        this.typeName = type.getName();
        this.type = type;
    }

    public Builder<?> getBuilder() {
        return builder;
    }

    public void setBuilder(Builder<?> builder) {
        this.builder = builder;
    }

    public String getBuilderName() {
        return builderName;
    }

    public void setBuilderName(String builderName) {
        this.builderName = builderName;
    }

    public Object getValue() {
        return value;
    }

    public void setValue(Object value) {
        this.value = value;
    }

    public Object getParsedValue() {
        return parsedValue.get();
    }

    public void setParsedValue(Object parsedValue) {
        this.parsedValue.set(parsedValue);
    }

    public String getTypeName() {
        return typeName;
    }

    public void setTypeName(String typeName) {
        this.type = ClassHelper.forName(typeName);
        this.typeName = typeName;
    }

    public boolean isRequired() {
        return required;
    }

    public void setRequired(boolean required) {
        this.required = required;
    }

    @Override
    public String toString() {
        return (this.name != null ? this.name + ":" : "") + super.toString();
    }

}
