package com.velocityorm.reactive;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * @author sandeepkumarjakkaraju
 */
public interface ReactiveRepository<T, ID> {
    Mono<T> saveReactive(T entity);
    Mono<T> updateReactive(T entity);
    Mono<Void> deleteReactive(ID id);
    Mono<T> findByIdReactive(ID id);
    Flux<T> findAllReactive();
}
