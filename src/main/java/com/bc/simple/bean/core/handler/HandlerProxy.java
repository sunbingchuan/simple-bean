package com.bc.simple.bean.core.handler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.bc.simple.bean.BeanDefinition;
import com.bc.simple.bean.BeanFactory;
import com.bc.simple.bean.common.config.ConfigLoader.Node;
import com.bc.simple.bean.common.util.StringUtils;
import com.bc.simple.bean.core.parser.BeanDefinitionParser;

public class HandlerProxy {
	private BeanFactory beanFactory;
	private final ConcurrentHashMap<String, Handler> handlers = new ConcurrentHashMap<String, Handler>();

	private void init(BeanFactory beanFactory) {
		this.beanFactory = beanFactory;
		registerHandler(new ScanHandler(beanFactory));
	}

	public HandlerProxy(BeanFactory beanFactory) {
		init(beanFactory);
	}

	public void registerHandler(Handler handler) {
		handlers.put(handler.getDomain(), handler);
	}


	/**
	 * Parses the supplied {@link Element} by delegating to the
	 * {@link BeanDefinitionParser} that is registered for that {@link Element}.
	 */
	public BeanDefinition parse(Node element, BeanDefinition containingBd, Node root) {
		try {
			String domain = StringUtils.getLetters(element.getName());
			Handler handler = handlers.get(domain);
			if (handler != null) {
				return handler.handle(element, containingBd, root);
			}
			return containingBd;
		} catch (Exception e) {
			// ignore
		}
		return null;
	}



	public static BeanDefinition decorate(Node node, BeanDefinition originalDef, Node root) {
		return null;
	}

	public static BeanDefinition decorate(Map<String, Object> attr, BeanDefinition originalDef, Node root) {
		return null;
	}

}
