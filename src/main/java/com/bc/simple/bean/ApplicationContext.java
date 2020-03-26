package com.bc.simple.bean;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import com.bc.simple.bean.core.parser.BeanDefinitionReader;
import com.bc.simple.bean.core.processor.CommonProcessor;
import com.bc.simple.bean.core.processor.Processor;
import com.bc.simple.bean.core.support.Environment;
import com.bc.simple.bean.core.support.PropertyResolverHandler;

public class ApplicationContext {

	private final PropertyResolverHandler propertyResolverHandler = new PropertyResolverHandler();

	private Environment environment;

	/** Flag that indicates whether this context is currently active. */
	private final AtomicBoolean active = new AtomicBoolean();

	/** Flag that indicates whether this context has been closed already. */
	private final AtomicBoolean closed = new AtomicBoolean();

	/** Synchronization monitor for the "refresh" and "destroy". */
	private final Object applicationMonitor = new Object();

	/** Synchronization monitor for the internal BeanFactory. */
	private final Object beanFactoryMonitor = new Object();

	private String[] configLocations;

	private BeanFactory beanFactory;

	private Thread shutdownHook;

	private Processor lifecycleProcessor;

	public ApplicationContext() {
	}

	public ApplicationContext(String... configLocations) {
		this.configLocations = configLocations;
	}

	public void refresh() {
		synchronized (this.applicationMonitor) {
			// Prepare this context for refreshing.
			prepareRefresh();

			// Tell the subclass to refresh the internal bean factory.
			BeanFactory beanFactory = obtainFreshBeanFactory();

			// Prepare the bean factory for use in this context.
			prepareBeanFactory(beanFactory);

			try {
				// Allows post-processing of the bean factory in context subclasses.
				postProcessBeanFactory(beanFactory);

				postProcessBeanDefinitions(beanFactory);
				
				registerBeanPostProcessors(beanFactory);

				// Initialize other special beans in specific context subclasses.
				onRefresh();

				// Instantiate all remaining (non-lazy-init) singletons.
				finishBeanFactoryInitialization(beanFactory);

				// Last step: publish corresponding event.
				finishRefresh();
			} catch (Throwable e) {
				e.printStackTrace();
			} finally {
				// Reset common introspection caches in Spring's core, since we
				// might not ever need metadata for singleton beans anymore...
				resetCommonCaches();
			}
		}
	}

	protected void prepareRefresh() {
		if (!active.get()) {
			active.compareAndSet(false, true);
		}
		if (closed.get()) {
			closed.compareAndSet(true, false);
		}
	}

	protected BeanFactory obtainFreshBeanFactory() {
		refreshBeanFactory();
		return getBeanFactory();
	}

	protected void prepareBeanFactory(BeanFactory beanFactory) {
		// Configure the bean factory with context callbacks.
		beanFactory.addProcessor(new CommonProcessor());
		// MessageSource registered (and found for autowiring) as a bean.
		beanFactory.registerResolvableDependency(BeanFactory.class, beanFactory);
		beanFactory.registerResolvableDependency(ApplicationContext.class, this);
	}

	protected void postProcessBeanFactory(BeanFactory beanFactory) {
		List<Processor> processors = getProcessors();
		for (Processor processor : processors) {
			processor.processBeanFactory(beanFactory);
		}
	}
	
	protected void postProcessBeanDefinitions(BeanFactory beanFactory) {
		List<Processor> processors = getProcessors();
		for (Processor processor : processors) {
			processor.processBeanDefinitions(beanFactory.getBeanDefinitions());
		}
	}


	protected void registerBeanPostProcessors(BeanFactory beanFactory) {
		Map<String, Processor> processors = beanFactory.getBeans(Processor.class);
		for (String beanName : processors.keySet()) {
			getBeanFactory().addProcessor(processors.get(beanName));
		}
	}

	protected void initLifecycleProcessor() {
		BeanFactory beanFactory = getBeanFactory();
		this.lifecycleProcessor = beanFactory.getBean(Processor.class);
	}

	protected void onRefresh() {
		// For subclasses: do nothing by default.
	}

	protected void finishBeanFactoryInitialization(BeanFactory beanFactory) {
		// Allow for caching all bean definition metadata, not expecting further
		// changes.
		beanFactory.freezeConfiguration();

		// Instantiate all remaining (non-lazy-init) singletons.
		beanFactory.preInstantiateSingletons();
	}

	protected void finishRefresh() {

	}

	protected void resetCommonCaches() {
	}

	@Deprecated
	public void destroy() {
		close();
	}

	public void close() {
		synchronized (this.applicationMonitor) {
			doClose();
			// If we registered a JVM shutdown hook, we don't need it anymore now:
			// We've already explicitly closed the context.
			if (this.shutdownHook != null) {
				try {
					Runtime.getRuntime().removeShutdownHook(this.shutdownHook);
				} catch (IllegalStateException ex) {
					// ignore - VM is already shutting down
				}
			}
		}
	}

	protected void doClose() {
		if (this.active.get() && this.closed.compareAndSet(false, true)) {

			// Destroy all cached singletons in the context's BeanFactory.
			destroyBeans();

			// Close the state of this context itself.
			closeBeanFactory();

			// Let subclasses do some final clean-up if they wish...
			onClose();

			this.active.set(false);
		}
	}

	protected void onClose() {
		// For subclasses: do nothing by default.
	}

	// ---------------------------------------------------------------------
	// Implementation of BeanFactory interface
	// ---------------------------------------------------------------------

	public Object getBean(String name) {
		return getBeanFactory().getBean(name);
	}

	public <T> T getBean(String name, Class<T> requiredType) {
		return getBeanFactory().getBean(name, requiredType);
	}

	public Object getBean(String name, Object... args) {
		return getBeanFactory().getBean(name, args);
	}

	public <T> T getBean(Class<T> requiredType) {
		return getBeanFactory().getBean(requiredType);
	}

	public <T> T getBean(Class<T> requiredType, Object... args) {

		return getBeanFactory().getBean(requiredType, args);
	}

	public boolean containsBean(String name) {
		return getBeanFactory().containsBean(name);
	}

	public boolean isSingleton(String name) {

		return getBeanFactory().isSingleton(name);
	}

	public boolean isPrototype(String name) {

		return getBeanFactory().isPrototype(name);
	}

	public Class<?> getType(String name) {

		return getBeanFactory().getType(name);
	}

	public String[] getAliases(String name) {
		return getBeanFactory().getAliases(name);
	}

	// ---------------------------------------------------------------------
	// Implementation of ListableBeanFactory interface
	// ---------------------------------------------------------------------

	public boolean containsBeanDefinition(String beanName) {
		return getBeanFactory().containsBeanDefinition(beanName);
	}

	public int getBeanDefinitionCount() {
		return getBeanFactory().getBeanDefinitionCount();
	}

	public String[] getBeanDefinitionNames() {
		return getBeanFactory().getBeanDefinitionNames();
	}

	public Set<String> getBeanNamesForType(Class<?> type) {
		return getBeanFactory().getBeanNamesForType(type);
	}

	public <T> Map<String, T> getBeansOfType(Class<T> type) {
		return getBeanFactory().getBeans(type);
	}

	public boolean containsLocalBean(String name) {
		return getBeanFactory().containsBean(name);
	}

	protected void refreshBeanFactory() {
		destroyBeans();
		closeBeanFactory();
		BeanFactory beanFactory = new BeanFactory();
		beanFactory.setContext(this);
		loadBeanDefinitions(beanFactory);
		synchronized (this.beanFactoryMonitor) {
			this.beanFactory = beanFactory;
		}

	}

	protected void loadBeanDefinitions(BeanFactory beanFactory) {
		// Create a new XmlBeanDefinitionReader for the given BeanFactory.
		BeanDefinitionReader beanDefinitionReader = new BeanDefinitionReader(beanFactory);
		beanDefinitionReader.setBeanClassLoader(Thread.currentThread().getContextClassLoader());
		// Allow a subclass to provide custom initialization of the reader,
		// then proceed with actually loading the bean definitions.
		loadBeanDefinitions(beanDefinitionReader);
	}

	protected void loadBeanDefinitions(BeanDefinitionReader reader) {
		String[] configLocations = getConfigLocations();
		if (configLocations != null) {
			reader.loadBeanDefinitions(configLocations);
		}
	}

	protected void closeBeanFactory() {
		if (beanFactory != null) {
			beanFactory.destroy();
		}
	}

	protected void destroyBeans() {
		if (this.beanFactory != null) {
			this.beanFactory.destroySingletons();
		}
	}

	public BeanFactory getBeanFactory() {
		return beanFactory;
	}

	public Environment getEnvironment() {
		return environment;
	}

	public void setEnvironment(Environment environment) {
		this.environment = environment;
	}

	public void setBeanFactory(BeanFactory beanFactory) {
		this.beanFactory = beanFactory;
	}

	public List<Processor> getProcessors() {
		return getBeanFactory().getProcessors();
	}

	public Processor getLifecycleProcessor() {
		return lifecycleProcessor;
	}

	public String[] getConfigLocations() {
		return configLocations;
	}

	public void setConfigLocations(String[] configLocations) {
		this.configLocations = configLocations;
	}

	public PropertyResolverHandler getPropertyResolverHandler() {
		return propertyResolverHandler;
	}

}
