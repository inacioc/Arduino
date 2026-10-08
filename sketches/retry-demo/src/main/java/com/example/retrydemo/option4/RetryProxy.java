package com.example.retrydemo.option4;

import io.github.resilience4j.retry.Retry;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;

/**
 * OPTION 4b: a generic JDK dynamic proxy that adds retry to every method of any interface.
 * This is, in miniature, what an annotation-based approach does for you behind the scenes.
 */
public final class RetryProxy {

    private RetryProxy() {
    }

    @SuppressWarnings("unchecked")
    public static <T> T wrap(Class<T> type, T target, Retry retry) {
        return (T) Proxy.newProxyInstance(
                type.getClassLoader(),
                new Class<?>[]{type},
                (proxy, method, args) -> retry.executeCheckedSupplier(() -> {
                    try {
                        return method.invoke(target, args);
                    } catch (InvocationTargetException e) {
                        // Unwrap, otherwise Resilience4j would see InvocationTargetException
                        // and never match HttpServerErrorException & co.
                        throw e.getCause();
                    }
                }));
    }
}
