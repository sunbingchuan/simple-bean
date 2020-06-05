package com.bc.simple.bean.core.handler;

import java.util.Map.Entry;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import com.bc.simple.bean.BeanDefinition;
import com.bc.simple.bean.common.config.ConfigLoader.Node;
import com.bc.simple.bean.common.util.BeanUtils;
import com.bc.simple.bean.common.util.ResourceUtils;
import com.bc.simple.bean.common.util.StringUtils;
import com.bc.simple.bean.core.BeanFactory;
import com.bc.simple.bean.core.parser.BeanDefinitionParser;
import com.bc.simple.bean.core.support.SimpleException;

public class HandlerProxy {

	private static final String configLocation = "META-INF/simple.handlers";

	private final ConcurrentHashMap<String, Handler> handlers = new ConcurrentHashMap<String, Handler>();

	@SuppressWarnings("rawtypes")
	private void init(BeanFactory beanFactory) {
		Properties props = ResourceUtils.loadAllProperties(configLocation, beanFactory.getBeanClassLoader());
		for (Entry<Object, Object> entry : props.entrySet()) {
			String key = StringUtils.toString(entry.getKey());
			String value = StringUtils.toString(entry.getValue());
			try {
				Class handlerClass = BeanUtils.forName(value, beanFactory.getBeanClassLoader());
				if (handlerClass != null && Handler.class.isAssignableFrom(handlerClass)) {
					Handler handler = (Handler) handlerClass.newInstance();
					handler.setBeanFactory(beanFactory);
					handlers.put(key, handler);
				}
			} catch (Exception e) {
				throw new SimpleException("key '" + key + "' parsed error of simple.handlers!", e);
			}
		}

	}

	public HandlerProxy(BeanFactory beanFactory) {
		init(beanFactory);
	}


	/**
	 * Parses the supplied {@link Element} by delegating to the {@link BeanDefinitionParser} that is
	 * registered for that {@link Element}.
	 */
	public Object parse(Node element, BeanDefinition containingBd, Node root) {
		try {
			String domain = StringUtils.getLetters(element.getName());
			Handler handler = handlers.get(domain);
			if (handler != null) {
				return handler.handle(element, containingBd, root);
			}
			return containingBd;
		} catch (Exception e) {
			throw new SimpleException("resolve config of " + element + " error!", e);
		}
	}

}
