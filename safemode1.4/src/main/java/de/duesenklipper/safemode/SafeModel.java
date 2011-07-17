package de.duesenklipper.safemode;

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
            String methodName = method.getName();
            if (methodName.equals("__propertyPath")) {
                return __propertyPath();
            } else if (methodName.equals("__root")) {
                return __root();
            } else if (methodName.startsWith("get")) {
                String propertyName = new StringBuilder()
                        .append(Character.toLowerCase(methodName.charAt(3)))
                        .append(methodName.substring(4)).toString();
                final String fullPropertyPath = __propertyPath() + "." + propertyName;
                Class<?> returnType = method.getReturnType();
                final PropertyFinderImpl next = new PropertyFinderImpl(root,
                        method.invoke(current), fullPropertyPath);
                if (Modifier.isFinal(returnType.getModifiers())) {
                    fallback.set(next);
                    return method.invoke(current);
                } else {
                    return ClassImposteriser.INSTANCE.imposterise(next, returnType,
                            PropertyFinder.class);
                }
            } else {
                throw new UnsupportedOperationException("SafeModel only supports getters");
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
