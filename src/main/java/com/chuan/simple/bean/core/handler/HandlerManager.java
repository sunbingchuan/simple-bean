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

import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.chuan.simple.helper.clazz.ClassHelper;
import com.chuan.simple.helper.common.StringHelper;
import com.chuan.simple.helper.resource.ResourceHelper;
import com.chuan.simple.bean.core.SimpleContext;
import com.chuan.simple.bean.core.config.node.Node;
import com.chuan.simple.bean.core.config.parser.SimpleParser;
import com.chuan.simple.bean.exception.SimpleParseException;

/**
 * <p>
 * This class will Parse handlers that provided by META-INF/simple.handlers and
 * map a CustomNode to its right handler.
 * <p>
 * The format of file META-INF/simple.handlers is:
 * <pre>
 * key=value
 * ...
 * key=value
 * </pre>
 * <p>
 * key: the config node name of CustomNode
 * {@link SimpleParser#parseCustomNode(Node)}
 * <p>
 * value: the class name of handler who implements {@link Handler}
 */
public class HandlerManager {

    private static final Log log = LogFactory.getLog(HandlerManager.class);

    private static final String handlerConfigLocation =
            "META-INF/simple.handlers";

    private final Map<String, Handler> handlers =
            new ConcurrentHashMap<String, Handler>();

    private void init(SimpleContext context) {
        Properties props = ResourceHelper.loadProperties(handlerConfigLocation);
        for (Entry<Object, Object> entry : props.entrySet()) {
            String key = StringHelper.toString(entry.getKey());
            String value = StringHelper.toString(entry.getValue());
            try {
                Class<?> handlerClass = ClassHelper.forName(value);
                if (handlerClass != null
                        && Handler.class.isAssignableFrom(handlerClass)) {
                    Handler handler = (Handler) handlerClass.newInstance();
                    handler.setContext(context);
                    handlers.put(key, handler);
                } else {
                    log.error("Skip simple handler " + entry + " as the value "
                            + value
                            + " couldn't be treated as a correct handler");
                }
            } catch (Exception e) {
                throw new SimpleParseException(
                        "Key '" + key + "' of simple.handlers parsed error", e);
            }
        }

    }

    public HandlerManager(SimpleContext context) {
        init(context);
    }

    public void parse(Node cfg) {
        try {
            Handler handler = handlers.get(cfg.getName());
            if (handler != null) {
                handler.handle(cfg);
            } else {
                log.debug("Skip simple bean config " + cfg
                        + " because the handler for it is not found");
            }
        } catch (Exception e) {
            throw new SimpleParseException("Node '" + cfg + "' parsed error",
                    e);
        }
    }

}
