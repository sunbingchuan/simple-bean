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
package com.chuan.simple.bean.core.config.node;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.chuan.simple.helper.common.StringHelper;

/**
 * A config entity.
 */
public class Node implements Serializable {
    private static final long serialVersionUID = 3441144908360271564L;
    protected static final String DEFAULT_NODE_NAME = "simple";

    protected Node parent;
    protected final List<Node> childs = new ArrayList<>();
    protected final Map<String, Object> attributes = new HashMap<>();

    protected String name;

    public Node(String name) {
        this.name = name;
    }

    public Node() {
        this(DEFAULT_NODE_NAME);
    }

    public Node getParent() {
        return parent;
    }

    public void setParent(Node parent) {
        this.parent = parent;
    }

    public List<Node> getChilds() {
        return childs;
    }

    public void addChild(Node child) {
        this.childs.add(child);
    }

    public Object attr(String key) {
        return attributes.get(key);
    }

    public Map<String, Object> attrs() {
        return attributes;
    }

    public Map<String, String> attrStrings() {
        Map<String, String> attrStrings = new HashMap<>();
        for (Entry<String, Object> entry : this.attributes.entrySet()) {
            String value = StringHelper.toString(entry.getValue());
            if (StringHelper.isNotEmpty(value)) {
                attrStrings.put(entry.getKey(), value);
            }
        }
        return attrStrings;
    }

    public String attrString(String key) {
        Object value = attributes.get(key);
        if (value == null) {
            return StringHelper.EMPTY;
        } 
        return value.toString();
    }

    public boolean hasAttr(String attr) {
        return attributes.containsKey(attr);
    }

    public Object attr(String key, Object value) {
        return attributes.put(key, value);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return toString(StringHelper.EMPTY);
    }

    protected String toString(String identation) {
        StringBuffer sb = new StringBuffer();
        sb.append(identation).append("Node:").append(this.name).append("\n");
        for (Entry<String, Object> attribute : attributes.entrySet()) {
            sb.append(identation).append("\t").append(attribute.getKey())
                    .append(":").append(attribute.getValue()).append("\n");
        }
        for (Node node : childs) {
            sb.append(node.toString("\t" + identation));
        }
        return sb.toString();
    }

}
