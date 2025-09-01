package hr.hrg.hipster.ioc;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
public  @interface  HipsterContext {
    /** If an implementation already exists, disable auto-build */
    Class<?> impl() default Void.class;
    Class<?>[] dependencies() default {};
}
