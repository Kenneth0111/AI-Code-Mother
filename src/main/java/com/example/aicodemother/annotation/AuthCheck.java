package com.example.aicodemother.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD) // 限定自定义的注解只能用在【方法】上
@Retention(RetentionPolicy.RUNTIME) // 保留策略规定注解能活到【运行时】
public @interface AuthCheck {

    /**
     * 必须要有某个角色（""表示方法默认不需要任何必须要有的角色）
     */
    String mustRole() default "";
}
