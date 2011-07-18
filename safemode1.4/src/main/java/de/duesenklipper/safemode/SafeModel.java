package de.duesenklipper.safemode;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;

import org.apache.wicket.model.IModel;
import org.apache.wicket.model.PropertyModel;
import org.jmock.api.Invocation;
import org.jmock.api.Invokable;
import org.jmock.lib.legacy.ClassImposteriser;

public final class SafeModel {
    private SafeModel() {
        // prevent instantiation
    }

    private static interface PropertyFinder {
        String __propertyPath();

        Object __root();
    }

    private final static ThreadLocal<PropertyFinder> fallback = new ThreadLocal<PropertyFinder>();

    private static class PropertyFinderImpl implements PropertyFinder, Invokable {
        private final Object root;
        private final String propertyPath;
        private final Object current;

        private PropertyFinderImpl(Object root, Object current, String propertyPath) {
            this.root = root;
            this.current = current;
            this.propertyPath = propertyPath;
        }

        public String __propertyPath() {
            return propertyPath;
        }

        public Object __root() {
            return root;
        }

        public Object invoke(Invocation invocation) throws Throwable {
            final Method method = invocation.getInvokedMethod();
            final String methodName = method.getName();
            final String fullPropertyPath;
            Class<?> returnType = null;
            final PropertyFinderImpl next;
            if (methodName.equals("__propertyPath")) {
                return __propertyPath();
            } else if (methodName.equals("__root")) {
                return __root();
            } else if (methodName.equals("get")) {
                if (List.class.isAssignableFrom(method.getDeclaringClass())) {
                    fullPropertyPath = new StringBuilder(__propertyPath()).append("[")
                            .append(invocation.getParameter(0)).append("]").toString();
                    if (current != null) {
                        ParameterizedType genericSuperclass = (ParameterizedType) current.getClass().getGenericSuperclass();
                        Type type = genericSuperclass.getActualTypeArguments()[0];
                        returnType = type.
                    }
                } else {
                    throw new UnsupportedOperationException();
                }
            } else if (methodName.startsWith("get")) {
                String propertyName = new StringBuilder()
                        .append(Character.toLowerCase(methodName.charAt(3)))
                        .append(methodName.substring(4)).toString();
                final StringBuilder fullPropertyPathBuilder = new StringBuilder(__propertyPath());
                if (fullPropertyPathBuilder.length() > 0) {
                    fullPropertyPathBuilder.append(".");
                }
                fullPropertyPathBuilder.append(propertyName);
                fullPropertyPath = fullPropertyPathBuilder.toString();
            } else {
                throw new UnsupportedOperationException("SafeModel only supports getters");
            }
            final Object actualResult;
            if (current != null) {
                actualResult = method.invoke(current, invocation.getParametersAsArray());
            } else {
                actualResult = null;
            }
            if ((actualResult != null) && Object.class.equals(method.getReturnType())) {
                
            }
            next = new PropertyFinderImpl(root, actualResult, fullPropertyPath);
            fallback.set(next);
            if (Modifier.isFinal(returnType.getModifiers())) {
                return actualResult;
            } else if (Object.class.equals(returnType)) {
                return ClassImposteriser.INSTANCE.imposterise(next, PropertyFinder.class);
            } else {
                return ClassImposteriser.INSTANCE.imposterise(next, returnType,
                        PropertyFinder.class);
            }
        }
    }

    public static <T> IModel<T> model(T metaTarget) {
        Object root;
        String propertyPath;
        PropertyFinder propFinder;
        if (metaTarget instanceof PropertyFinder) {
            propFinder = (PropertyFinder) metaTarget;
        } else if (fallback.get() != null) {
            propFinder = fallback.get();
        } else {
            throw new IllegalArgumentException("metaTarget must be a PropertyFinder - use from()");
        }
        root = propFinder.__root();
        propertyPath = propFinder.__propertyPath();
        return new PropertyModel<T>(root, propertyPath);
    }

    public static <U> U from(U target) {
        fallback.set(null);
        return (U) ClassImposteriser.INSTANCE
                .imposterise(new PropertyFinderImpl(target, target, ""), target.getClass(),
                        PropertyFinder.class);
    }
}
