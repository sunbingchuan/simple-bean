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
package com.chuan.simple.bean.core.config.loader;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import com.chuan.simple.helper.common.StringHelper;
import com.chuan.simple.helper.resource.Resource;
import com.chuan.simple.helper.resource.StreamHelper;
import com.chuan.simple.bean.core.config.node.Node;
import com.chuan.simple.bean.core.config.node.NodeWrapper;

/**
 * A common resource loader.
 */
public class SimpleLoader implements Loader<Resource, NodeWrapper> {

    /**
     * We use a way to show level iteration by '\t' character.
     * @param resource
     *            file
     * @return {@link Node}
     */
    private static final Map<Resource, Node> cache = new HashMap<>();
    public NodeWrapper load(Resource resource) {
        try {
            Node node = cache.get(resource);
            if (node ==null) {
                node = load(StreamHelper.toString(resource.getInputStream()));
                cache.put(resource, node);
            }
            return new NodeWrapper(node, resource);
        } catch (IOException e) {
            throw new IllegalConfigException(e);
        }
    }

    /**
     * @param config
     *            the config {@link String}
     * @return node correspond to the config
     */
    private Node load(String config) {
        config += "\n";
        StringBuffer line = new StringBuffer();
        Node root = new Node("root");
        Status status = new Status(root);
        // Level of each line
        for (int i = 0,level = 0; i < config.length(); i++) {
            char c = config.charAt(i);
            if (c == '\n') {
                String ln = line.toString().trim();
                if (StringHelper.isEmpty(ln) || ln.charAt(0) == '#') {
                    level = 0;
                    line.delete(0, line.length());
                    continue;
                }
                if (level > status.curLevel) {
                    throw new IllegalConfigException(line.toString());
                }
                for (; level < status.curLevel; status.curLevel--) {
                    status.node = status.node.getParent();
                }
                if (ln.indexOf(":") >= 0) {
                    attr(status, ln);
                } else {
                    addChild(status, ln);
                    status.curLevel++;
                }
                line.delete(0, line.length());
                level = 0;
                continue;
            } else if (c == '\t') {
                level++;
            }
            line.append(c);
        }
        return root;
    }

    private void attr(Status status, String line) {
        String[] attr = line.toString().split(":");
        if (attr.length == 0) {
            throw new IllegalConfigException(line.toString());
        }
        String name = attr[0];
        int p;
        if ((p = name.lastIndexOf('.')) >= 0) {
            nestedChild(status, name.substring(0, p));
            name = name.substring(p + 1);
            status.node = status.node.getParent();
        }
        if (attr.length == 1) {
            status.end.attr(name, "");
        } else {
            status.end.attr(name, attr[1]);
        }
    }

    private void nestedChild(Status status, String nestedName) {
        int pos = 0;
        String name;
        int l = status.curLevel;
        status.end = status.node;
        boolean isNew = false;
        for (int i = 0; i <= nestedName.length(); i++) {
            if (i == nestedName.length() || nestedName.charAt(i) == '.') {
                name = nestedName.substring(pos, i);
                if (StringHelper.equals(name, "$")) {
                    isNew = true;
                    name = StringHelper.EMPTY;
                }
                if (name.length() > 0) {
                    status.domainName.put(l, name);
                    status.end = create(status.end, name);
                    status.domainNode.put(l, status.end);
                } else {
                    Node node = null;
                    if (isNew) {
                        name = status.domainName.get(l);
                        if (name != null) {
                            node = create(status.end, name);
                        }
                    } else {
                        node = status.domainNode.get(l);
                    }
                    if (node == null) {
                        throw new IllegalConfigException(nestedName);
                    }
                    status.domainNode.put(l, node);
                    status.end = node;
                }
                if (l == status.curLevel) {
                    status.node = status.end;
                }
                pos = i + 1;
                l++;
            }
        }
    }

    private void addChild(Status status, String line) {
        String name = line.toString().trim();
        nestedChild(status, name);
    }

    private Node create(Node parent, String childName) {
        Node node = new Node(childName);
        node.setParent(parent);
        parent.addChild(node);
        return node;
    }

    /**
     * Class to deal with abbreviate For example:
     * <pre>
     *  a.b.xx
     *  ..yy(=a.b.yy)
     * </pre>
     */
    private static class Status {

    	final Map<Integer, String>  domainName = new HashMap<>();

    	final Map<Integer, Node> domainNode = new HashMap<>();

        /**
         * Current node.
         */
        volatile Node node;

        /**
         * The node in hand.
         */
        volatile Node end;

        /**
         * Level of {@link #node}
         */
        volatile int curLevel=0;

        private Status(Node node) {
            this.node = node;
            this.end = node;
        }

    }
    
    public static class IllegalConfigException extends RuntimeException {
        private static final long serialVersionUID = 1434072254199249298L;

        public IllegalConfigException(String msg) {
            super(msg);
        }

        public IllegalConfigException() {
            super();
        }

        public IllegalConfigException(Throwable cause) {
            super(cause);
        }

        public IllegalConfigException(String msg, Throwable cause) {
            super(msg, cause);
        }

    }
    
}
