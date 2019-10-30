
package com.bc.simple.bean.common.stereotype;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Beans on which the current bean depends. Any beans specified are guaranteed to be created by the
 * container before this bean. Used infrequently in cases where a bean does not explicitly depend on
 * another through properties or constructor arguments, but rather depends on the side effects of
 * another bean's initialization.
 *
 * <p>
 * May be used on any class directly or indirectly annotated with
 * {@link org.springframework.stereotype.Component} or on methods annotated with {@link Bean}.
 *
 * <p>
 * Using {@link DependsOn} at the class level has no effect unless component-scanning is being used.
 * If a {@link DependsOn}-annotated class is declared via XML, {@link DependsOn} annotation metadata
 * is ignored, and {@code <bean depends-on="..."/>} is respected instead.
 *
 * @author Juergen Hoeller
 * @since 3.0
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DependsOn {

	String[] value() default {};

}
