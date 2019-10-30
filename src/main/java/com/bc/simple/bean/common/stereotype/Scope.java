
package com.bc.simple.bean.common.stereotype;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * When used as a type-level annotation in conjunction with
 * {@link org.springframework.stereotype.Component @Component}, {@code @Scope} indicates the name of
 * a scope to use for instances of the annotated type.
 *
 * <p>
 * When used as a method-level annotation in conjunction with {@link Bean @Bean}, {@code @Scope}
 * indicates the name of a scope to use for the instance returned from the method.
 *
 * <p>
 * <b>NOTE:</b> {@code @Scope} annotations are only introspected on the concrete bean class (for
 * annotated components) or the factory method (for {@code @Bean} methods). In contrast to XML bean
 * definitions, there is no notion of bean definition inheritance, and inheritance hierarchies at
 * the class level are irrelevant for metadata purposes.
 *
 * <p>
 * In this context, <em>scope</em> means the lifecycle of an instance, such as {@code singleton},
 * {@code prototype}, and so forth. Scopes provided out of the box in Spring may be referred to
 * using the {@code SCOPE_*} constants available in the {@link ConfigurableBeanFactory} and
 * {@code WebApplicationContext} interfaces.
 *
 * <p>
 * To register additional custom scopes, see
 * {@link org.Factory.beans.factory.config.CustomScopeConfigurer CustomScopeConfigurer}.
 *
 * @author Mark Fisher
 * @author Chris Beams
 * @author Sam Brannen
 * @since 2.5
 * @see org.springframework.stereotype.Component
 * @see org.springframework.context.annotation.Bean
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Scope {

	/**
	 * Alias for {@link #scopeName}.
	 * 
	 * @see #scopeName
	 */
	String value() default "";

	/**
	 * Specifies the name of the scope to use for the annotated component/bean.
	 * <p>
	 * Defaults to an empty string ({@code ""}) which implies
	 * {@link ConfigurableBeanFactory#SCOPE_SINGLETON SCOPE_SINGLETON}.
	 * 
	 * @since 4.2
	 * @see ConfigurableBeanFactory#SCOPE_PROTOTYPE
	 * @see ConfigurableBeanFactory#SCOPE_SINGLETON
	 * @see org.springframework.web.context.WebApplicationContext#SCOPE_REQUEST
	 * @see org.springframework.web.context.WebApplicationContext#SCOPE_SESSION
	 * @see #value
	 */
	String scopeName() default "";



}
