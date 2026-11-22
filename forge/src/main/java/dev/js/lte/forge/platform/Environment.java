package dev.js.lte.forge.platform;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Fabric {@code @Environment} 的等价物：Forge 不按注解裁剪类，这里只保留语义标记。 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD})
public @interface Environment {
    EnvType value();
}
