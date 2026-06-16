package com.velocityorm.core.query;

import java.io.Serializable;
import java.util.function.Function;

/**
 * @author sandeepkumarjakkaraju
 */
@FunctionalInterface
public interface PropertyFunc<T, R> extends Function<T, R>, Serializable {
}
