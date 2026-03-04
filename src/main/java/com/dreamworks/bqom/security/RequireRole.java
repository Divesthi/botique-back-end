package com.dreamworks.bqom.security;

import com.dreamworks.bqom.repository.enums.UserRole;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to restrict endpoint access to specific roles.
 * Can be applied at class or method level.
 * Method-level annotations override class-level ones.
 *
 * Example: @RequireRole(UserRole.TENANT_ADMIN)
 */
@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireRole {
    UserRole value();
}
