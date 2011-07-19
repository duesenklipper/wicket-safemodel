package de.wicketbuch.safemodel;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import org.apache.wicket.model.IModel;
import org.apache.wicket.model.PropertyModel;
import org.jmock.api.Invocation;
import org.jmock.api.Invokable;
import org.jmock.lib.legacy.ClassImposteriser;

public final class SafeModel {
    private SafeModel() {
        // prevent instantiation
    }

    public static interface PropertyFinder {
        // marker
    }

    private final static ThreadLocal<StringBuilder> path = new ThreadLocal<StringBuilder>();
    private final static ThreadLocal<Object> root = new ThreadLocal<Object>();
    private static final ThreadLocal<Object> currentTarget = new ThreadLocal<Object>();

    private static class PropertyFinderImpl implements PropertyFinder, Invokable {
        private static final PropertyFinderImpl INSTANCE = new PropertyFinderImpl();

        private PropertyFinderImpl() {
        }

        public Object invoke(final Invocation invocation) throws Throwable {
            final Method method = invocation.getInvokedMethod();
            final String methodName = method.getName();
            final StringBuilder pathBuilder = path.get();
            final Object current = currentTarget.get();
            final Object callResult = current != null ? method.invoke(current,
                    invocation.getParametersAsArray()) : null;
            final Class<?> returnType = callResult != null ? callResult.getClass() : method
                    .getReturnType();
            if (methodName.equals("get") && (invocation.getParameterCount() == 1)) {
                pathBuilder.append("[");
                pathBuilder.append(invocation.getParameter(0));
                pathBuilder.append("]");
            } else if (methodName.startsWith("get")) {
                final String propertyName = new StringBuilder()
                        .append(Character.toLowerCase(methodName.charAt(3)))
                        .append(methodName.substring(4)).toString();
                if (pathBuilder.length() > 0) {
                    pathBuilder.append(".");
                }
                pathBuilder.append(propertyName);
            } else {
                throw new UnsupportedOperationException(
                        "SafeModel only supports JavaBean-style getters");
            }
            currentTarget.set(callResult);
            if (returnType.isPrimitive()) {
                return callResult;
            }
            if (Modifier.isFinal(returnType.getModifiers())) {
                return callResult;
            } else if (Object.class.equals(returnType)) {
                return ClassImposteriser.INSTANCE.imposterise(INSTANCE, PropertyFinder.class);
            } else {
                return ClassImposteriser.INSTANCE.imposterise(INSTANCE, returnType,
                        PropertyFinder.class);
            }
        }
    }

    public static <T> IModel<T> model(final T metaTarget) {
        final Object target = root.get();
        root.remove();
        final StringBuilder pathBuilder = path.get();
        path.remove();
        currentTarget.remove();
        if (target == null) {
            throw new IllegalArgumentException("target not set - did you forget to use from()?");
        }
        if (pathBuilder == null) {
            throw new IllegalArgumentException("path not set - did you forget to use from()?");
        }
        return new PropertyModel<T>(target, pathBuilder.toString());
    }

    @SuppressWarnings("unchecked")
    public static <U> U from(final U target) {
        path.set(new StringBuilder());
        root.set(target);
        currentTarget.set(target);
        return (U) ClassImposteriser.INSTANCE.imposterise(PropertyFinderImpl.INSTANCE,
                target.getClass(), PropertyFinder.class);
    }
}
