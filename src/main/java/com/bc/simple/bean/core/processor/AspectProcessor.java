package com.bc.simple.bean.core.processor;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.bc.simple.bean.BeanDefinition;
import com.bc.simple.bean.common.annotation.Around;
import com.bc.simple.bean.common.annotation.Aspect;
import com.bc.simple.bean.common.support.proxy.SimpleInvocationHandler;
import com.bc.simple.bean.common.util.PatternUtils;
import com.bc.simple.bean.common.util.StringUtils;
import com.bc.simple.bean.core.BeanFactory;
import com.bc.simple.bean.core.support.AnnotationMetaData;

public class AspectProcessor implements Processor {
	private static final String CONSTRUCTOR_PATTERN = "new";
	private static final String ARG_TYPES_ANY_PATTERN = "..";
	private List<PointCut> pointCuts = new ArrayList<AspectProcessor.PointCut>();
	
	
	private BeanFactory beanFactory;

	public AspectProcessor(BeanFactory beanFactory) {
		this.beanFactory = beanFactory;
	}

	@Override
	public void processBeanDefinitions(Collection<BeanDefinition> beanDefinitions) {
		addPoints(beanDefinitions);
		applyPoints(beanDefinitions);
	}

	private void applyPoints(Collection<BeanDefinition> beanDefinitions) {
		for (BeanDefinition beanDefinition : beanDefinitions) {
			walkPointCuts(beanDefinition);
		}
	}

	private void walkPointCuts(BeanDefinition beanDefinition) {
		Class<?> beanClass = beanDefinition.resolveBeanClass();
		for (PointCut pointCut : pointCuts) {
			if (pointCut.matchClass(beanClass.getCanonicalName())) {
				walkPointCut(pointCut, beanDefinition);
			}
		}

	}

	private void walkPointCut(PointCut pointCut, BeanDefinition beanDefinition) {
		Class<?> beanClass = beanDefinition.resolveBeanClass();
		if (pointCut.matchClass(beanClass.getCanonicalName())) {
			if (pointCut.isClassProxy()) {
				beanDefinition.setHandleClass(pointCut.getHandlerClass());
			} else {
				for (Method m : beanClass.getDeclaredMethods()) {
					if (pointCut.matchMethod(m)) {
						InvocationHandler handler=getHandler(pointCut);
						if (beanDefinition.getHandleClass()!=null
								&&SimpleInvocationHandler.class.isAssignableFrom(beanDefinition.getHandleClass())) {
							SimpleInvocationHandler.register(m, handler);
						}else {
							beanDefinition.getOverrideMethods().put(m, handler);
						}
					}
				}
				for (Constructor<?> constructor:beanClass.getDeclaredConstructors()) {
					if (pointCut.matchConstructor(constructor)) {
						beanDefinition.setHandleClass(SimpleInvocationHandler.class);
						InvocationHandler handler=getHandler(pointCut);
						SimpleInvocationHandler.register(constructor, handler);
						if (beanDefinition.getOverrideMethods().size()>0) {
							SimpleInvocationHandler.registerAll(beanDefinition.getOverrideMethods());
							beanDefinition.getOverrideMethods().clear();
						}
					}
				}
			}
		}
	}

	private InvocationHandler getHandler(PointCut pointCut) {
		InvocationHandler handler=pointCut.getHandler();
		if ( handler== null&&StringUtils.isNotEmpty(pointCut.getHandlerRef())){
			handler = new InvocationHandler() {
				@Override
				public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
					InvocationHandler handler = (InvocationHandler) beanFactory.getBean(pointCut.getHandlerRef());
					if (handler!=null) {
						return handler.invoke(proxy, method, args);
					}
					return method.invoke(proxy, args);
				}
			};
			pointCut.setHandler(handler);
		}
		return  handler;
	}
	
	private void addPoints(Collection<BeanDefinition> beanDefinitions) {
		for (BeanDefinition beanDefinition : beanDefinitions) {
			AnnotationMetaData metaData = beanDefinition.getMetadata();
			if (metaData != null && metaData.hasAnnotation(Aspect.class.getCanonicalName())) {
				if (beanDefinition.getMetadata().hasAnnotatedMethods(Around.class.getCanonicalName())) {
					resolveAround(beanDefinition);
				}
			}
		}
	}

	@SuppressWarnings("unchecked")
	private void resolveAround(BeanDefinition beanDefinition) {
		Class<?> beanClass = beanDefinition.resolveBeanClass();
		for (Method m : beanClass.getDeclaredMethods()) {
			Around around = m.getDeclaredAnnotation(Around.class);
			if (around != null) {
				Object aspect = beanFactory.getBean(beanDefinition.getBeanName());
				String pointcut = around.value();
				if (StringUtils.isNotEmpty(pointcut)) {
					if (m.getParameterCount() == 0) {
						Object o = null;
						try {
							o = m.invoke(aspect);
						} catch (Exception e) {
							// ignore
						}
						if (o instanceof String) {
							addPoint(pointcut, (String) o);
						} else if (o instanceof Class) {
							addPoint(pointcut, (Class<InvocationHandler>) o);
						}
					} else {
						addPoint(pointcut, new InvocationHandler() {
							@Override
							public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
								System.out.println("addPoint invoke doaround");
								return m.invoke(aspect, proxy, method, args);
							}
						});
					}
				}
			}
		}
	}

	public void addPoint(String pointcut, Class<? extends InvocationHandler> handlerClass) {
		pointCuts.add(new PointCut(pointcut, handlerClass));
	}

	public void addPoint(String pointcut, String handlerRef) {
		pointCuts.add(new PointCut(pointcut, handlerRef));
	}

	public void addPoint(String pointcut, InvocationHandler handler) {
		pointCuts.add(new PointCut(pointcut, handler));
	}

	public class PointCut {

		private String pointcut;
		private Class<? extends InvocationHandler> handlerClass;
		private String handlerRef;

		private String returnTypePattern;
		private String classPattern;
		private String methodPattern;
		private String[] argTypePatterns;
		private int modifiers;

		private boolean classProxy = false;

		private void init() {
			String[] tokens = pointcut.split("[ \\(\\),]+");
			if (tokens.length == 1) {
				classPattern = tokens[0];
				classProxy = true;
				return;
			}
			int i = 0;
			for (; i < tokens.length; i++) {
				Integer modifier = PatternUtils.getModifier(tokens[i]);
				if (modifier == null) {
					break;
				}
				modifiers += modifier;
			}
			returnTypePattern = tokens[i++];
			String token = tokens[i++];
			int separate = token.lastIndexOf('.');
			classPattern = token.substring(0, separate);
			if ("*".equals(classPattern)) {
				classPattern = "*..*";
			}
			methodPattern = token.substring(separate + 1);
			argTypePatterns = new String[tokens.length - i];
			System.arraycopy(tokens, i, argTypePatterns, 0, tokens.length - i);
		}


		private InvocationHandler handler;

		public PointCut(String pointcut, Class<? extends InvocationHandler> handlerClass) {
			this(pointcut, handlerClass, null, null);
			this.classProxy=true;
		}

		public PointCut(String pointcut, String handlerRef) {
			this(pointcut, null, handlerRef, null);
		}

		public PointCut(String pointcut, InvocationHandler handler) {
			this(pointcut, null, null, handler);
		}


		private PointCut(String pointcut, Class<? extends InvocationHandler> handlerClass, String handlerRef, InvocationHandler handler) {
			this.handlerClass = handlerClass;
			this.pointcut = pointcut;
			this.handlerRef = handlerRef;
			this.handler = handler;
			init();
		}

		public String getPointcut() {
			return pointcut;
		}

		public void setPointcut(String pointcut) {
			this.pointcut = pointcut;
		}

		public String getHandlerRef() {
			return handlerRef;
		}

		public void setHandlerRef(String handlerRef) {
			this.handlerRef = handlerRef;
		}

		public Class<? extends InvocationHandler> getHandlerClass() {
			return handlerClass;
		}

		public void setHandlerClass(Class<? extends InvocationHandler> handlerClass) {
			this.handlerClass = handlerClass;
		}

		public InvocationHandler getHandler() {
			if (handler == null && handlerRef != null) {
				handler = (InvocationHandler) beanFactory.getBean(handlerRef);
			}
			return handler;
		}

		public void setHandler(InvocationHandler handler) {
			this.handler = handler;
		}

		public boolean matchClass(String className) {
			return PatternUtils.matchQualifiedName(classPattern, className);
		}

		public boolean matchConstructor(Constructor<?> constructor) {
			if (!CONSTRUCTOR_PATTERN.equals(methodPattern)) {
				return false;
			}
			if (!argsTypeMatch(constructor)) {
				return false;
			}
			return true;
		}
		
		public boolean matchMethod(Method method) {
			if (!PatternUtils.matchQualifiedName(methodPattern, method.getName())) {
				return false;
			}
			if ((modifiers & method.getModifiers()) == 0) {
				return false;
			}
			if (!PatternUtils.matchQualifiedName(returnTypePattern, method.getReturnType().getCanonicalName())) {
				return false;
			}
			if (!argsTypeMatch(method)) {
				return false;
			}
			return true;
		}

		private boolean argsTypeMatch(Executable exec) {
			if (ARG_TYPES_ANY_PATTERN.equals(argTypePatterns[0])) {
				return true;
			}
			if (argTypePatterns.length != exec.getParameterCount()) {
				return false;
			}
			Class<?>[] types = exec.getParameterTypes();
			for (int i = 0; i < types.length; i++) {
				Class<?> type = types[i];
				if (!PatternUtils.matchQualifiedName(argTypePatterns[i], type.getCanonicalName())) {
					return false;
				}
			}
			return true;
		}
		
		public boolean isClassProxy() {
			return classProxy;
		}

		public void setClassProxy(boolean classProxy) {
			this.classProxy = classProxy;
		}
		
		@Override
		public String toString() {
			return this.pointcut;
		}
	}

}
