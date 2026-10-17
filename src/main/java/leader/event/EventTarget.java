package leader.event;

import leader.event.types.Priority;

import java.lang.annotation.*;

/** Event listener marker derived from DarkMagician6's eventapi. */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface EventTarget {
    byte value() default Priority.MEDIUM;
}
