/**
 * Copyright (C) 2011 Carl-Eric Menzel <cmenzel@wicketbuch.de>
 * and possibly other SafeModel contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.wicketbuch.safemodel;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;

import org.apache.wicket.model.IModel;
import org.apache.wicket.model.LoadableDetachableModel;
import org.apache.wicket.model.PropertyModel;
import org.jmock.api.Invocation;
import org.jmock.api.Invokable;
import org.jmock.lib.legacy.ClassImposteriser;

import com.googlecode.gentyref.GenericTypeReflector;

public final class SafeModel {
    private static final String CGLIB_NAME_MARKER = "$$";

    private enum Mode {
        PROPERTY, SERVICE
    }

    private SafeModel() {
        // prevent instantiation
    }

    public static interface PropertyFinder {
        // marker
    }

    private final static ThreadLocal<StringBuilder> path = new ThreadLocal<StringBuilder>();
    private final static ThreadLocal<Object> root = new ThreadLocal<Object>();
    private static final ThreadLocal<Object> currentTarget = new ThreadLocal<Object>();
    private static final ThreadLocal<Method> serviceMethod = new ThreadLocal<Method>();
    private static final ThreadLocal<Mode> mode = new ThreadLocal<Mode>();
    private static final ThreadLocal<Object[]> serviceArguments = new ThreadLocal<Object[]>();

    private static class PropertyFinderImpl implements PropertyFinder, Invokable {
        private static final PropertyFinderImpl INSTANCE = new PropertyFinderImpl();

        private PropertyFinderImpl() {
        }

        public Object invoke(final Invocation invocation) throws Throwable {
            final Method method = invocation.getInvokedMethod();
            final String methodName = method.getName();
            final StringBuilder pathBuilder = path.get();
            final Object current;
            {
                final Object maybeModel = currentTarget.get();
                if (maybeModel == null) {
                    current = null;
                } else if (maybeModel instanceof IModel) {
                    current = ((IModel<?>) maybeModel).getObject();
                } else {
                    current = maybeModel;
                }
            }
            final Object callResult = current != null ? method.invoke(current, invocation.getParametersAsArray())
                    : null;
            final Class<?> returnType = callResult != null ? callResult.getClass() : method.getReturnType();
            if (methodName.equals("get") && (invocation.getParameterCount() == 1)) {
                pathBuilder.append("[");
                pathBuilder.append(invocation.getParameter(0));
                pathBuilder.append("]");
            } else if (methodName.startsWith("get")) {
                final String propertyName = new StringBuilder().append(Character.toLowerCase(methodName.charAt(3)))
                        .append(methodName.substring(4)).toString();
                if (pathBuilder.length() > 0) {
                    pathBuilder.append(".");
                }
                pathBuilder.append(propertyName);
            } else {
                throw new UnsupportedOperationException("SafeModel only supports JavaBean-style getters");
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
                return ClassImposteriser.INSTANCE.imposterise(INSTANCE, returnType, PropertyFinder.class);
            }
        }
    }

    /**
     * Build the model started by {@link SafeModel#from(Object)}.
     * 
     * @param metaTarget
     *            The property returned from calling getters on {@link #from(Object)}'s result
     * @return The desired model.
     */
    public static <T> IModel<T> model(final T metaTarget) {
        final Mode currentMode = mode.get();
        if (currentMode == Mode.PROPERTY) {
            return propertyModel(metaTarget);
        } else if (currentMode == Mode.SERVICE) {
            return serviceModel(metaTarget);
        } else {
            throw new IllegalStateException("No model was started - did you forget to use from() or fromService()?");
        }
    }

    private static interface TypeAware<T> {
        Class<T> __type();
    }

    private abstract static class TypeAwareLDM<T> extends LoadableDetachableModel<T> implements TypeAware<T> {
        private final Class<T> type;

        private TypeAwareLDM(final Class<T> type) {
            this.type = (Class<T>) unproxy(type)[0];
        }

        public Class<T> __type() {
            return type;
        }
    }

    @SuppressWarnings("unchecked")
    private static Class<?>[] unproxy(final Class<?> type) {
        if (type == null) {
            return null;
        }
        Class<?> result = type;
        // unwrap any cglib proxy...
        while (isCglibProxy(result)) {
            result = result.getSuperclass();
        }
        // ...unwrap any JDK proxy...
        if (Proxy.isProxyClass(result)) {
            // we proxy all these interfaces
            return result.getInterfaces();
        } else {
            // no jdk proxy -> we just subclass whatever this is
            return new Class<?>[] { result };
        }
    }

    public static <T> boolean isCglibProxy(Class<T> result) {
        return result.getName().contains(CGLIB_NAME_MARKER);
    }

    @SuppressWarnings("unchecked")
    private static <T> IModel<T> serviceModel(final T metaTarget) {
        final Object target = root.get();
        final Method method = serviceMethod.get();
        final Object[] arguments = serviceArguments.get();
        clear();
        if (target == null) {
            throw new IllegalArgumentException("target not set - did you forget to use fromService()?");
        }
        if (method == null) {
            throw new IllegalArgumentException("method not set - did you forget to use fromService()?");
        }
        final Class<T> modelObjectType = (Class<T>) (metaTarget != null ? metaTarget.getClass() : null);
        return new TypeAwareLDM<T>(modelObjectType) {
            @Override
            protected T load() {
                try {
                    return (T) method.invoke(target, arguments);
                } catch (final InvocationTargetException e) {
                    if (e.getCause() instanceof RuntimeException) {
                        throw (RuntimeException) e.getCause();
                    } else {
                        throw new RuntimeException(e.getCause());
                    }
                } catch (final IllegalArgumentException e) {
                    throw e;
                } catch (final IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            }
        };
    }

    @SuppressWarnings("unchecked")
    private static <T> IModel<T> propertyModel(final T metaTarget) {
        final Object target = root.get();
        final StringBuilder pathBuilder = path.get();
        clear();
        if (target == null) {
            throw new IllegalArgumentException("target not set - did you forget to use from()?");
        }
        if (pathBuilder == null) {
            throw new IllegalArgumentException("path not set - did you forget to use from()?");
        }
        final Class<T> modelObjectType = (Class<T>) (metaTarget != null ? metaTarget.getClass() : null);
        return new TypeAwarePropModel<T>(modelObjectType, target, pathBuilder.toString());
    }

    private static class TypeAwarePropModel<T> extends PropertyModel<T> implements TypeAware<T> {
        private final Class<T> type;

        private TypeAwarePropModel(final Class<T> type, final Object target, final String expression) {
            super(target, expression);
            this.type = (Class<T>) unproxy(type)[0];
        }

        public Class<T> __type() {
            return type;
        }
    }

    private static void clear() {
        serviceArguments.remove();
        root.remove();
        path.remove();
        currentTarget.remove();
        serviceMethod.remove();
        mode.remove();
    }

    /**
     * Start building a property model from the given target bean.
     * 
     * @param target
     *            the target bean
     * @return a proxy facilitating property path recording. Call getters until you get to the property you want, then
     *         pass the result to {@link #model(Object)}.
     */
    @SuppressWarnings("unchecked")
    public static <U> U from(final U target) {
        clear();
        path.set(new StringBuilder());
        root.set(target);
        currentTarget.set(target);
        mode.set(Mode.PROPERTY);
        return (U) imposterise(target.getClass(), PropertyFinderImpl.INSTANCE, PropertyFinder.class);
    }

    /**
     * Start building a property model from the given target bean.
     * 
     * @param target
     *            the target bean
     * @return a proxy facilitating property path recording. Call getters until you get to the property you want, then
     *         pass the result to {@link #model(Object)}.
     */
    @SuppressWarnings("unchecked")
    public static <U> U from(final IModel<U> target) {
        clear();
        path.set(new StringBuilder());
        root.set(target);
        currentTarget.set(target);
        mode.set(Mode.PROPERTY);
        final Class<U> classToImposterize;
        {
            if (target instanceof TypeAware) {
                final TypeAware<U> ta = (TypeAware<U>) target;
                if (ta.__type() != null) {
                    classToImposterize = ta.__type();
                } else {
                    classToImposterize = reflectModelObjectType(target);
                }
            } else {
                classToImposterize = reflectModelObjectType(target);
            }
        }
        return imposterise(classToImposterize, PropertyFinderImpl.INSTANCE, PropertyFinder.class);
    }

    public static <U> U imposterise(final Class<U> classToImposterize, Invokable handler, Class<?> handlerInterface) {
        final Class<?>[] classOrInterfaces = unproxy(classToImposterize);
        if (classOrInterfaces.length == 1) {
            return (U) ClassImposteriser.INSTANCE.imposterise(handler, classOrInterfaces[0], handlerInterface);
        } else {
            return (U) ClassImposteriser.INSTANCE.imposterise(handler, handlerInterface, classOrInterfaces);
        }
    }

    @SuppressWarnings("unchecked")
    private static <U> Class<U> reflectModelObjectType(final IModel<U> target) throws Error {
        final Method getObject;
        try {
            getObject = target.getClass().getMethod("getObject");
        } catch (final NoSuchMethodException e) {
            throw new Error();
        }
        final Type type = GenericTypeReflector.getExactReturnType(getObject, target.getClass());
        if (type instanceof Class) {
            return (Class<U>) type;
        } else if (type instanceof ParameterizedType) {
            return (Class<U>) ((ParameterizedType) type).getRawType();
        } else {
            throw new UnsupportedOperationException("don't know how to find the type");
        }
    }

    @SuppressWarnings("unchecked")
    /**
     * @param target may be a proxy. If it is a JDK proxy, it is assumed that the first interface
     * @return
     */
    public static <U> U fromService(final U target) {
        clear();
        root.set(target);
        mode.set(Mode.SERVICE);
        return (U) imposterise(target.getClass(), ServiceFinderImpl.INSTANCE, ServiceFinder.class);
    }

    public static interface ServiceFinder {
        // marker
    }

    public static class ServiceFinderImpl implements ServiceFinder, Invokable {
        private static final ServiceFinderImpl INSTANCE = new ServiceFinderImpl();
        private static final Invokable BLOCKER = new Invokable() {

            public Object invoke(final Invocation invocation) throws Throwable {
                throw new UnsupportedOperationException("a service model can only deal with one service and one method");
            }
        };

        private ServiceFinderImpl() {
            // singleton
        }

        public Object invoke(final Invocation invocation) throws Throwable {
            final Method method = invocation.getInvokedMethod();
            serviceMethod.set(method);
            final Object[] args = invocation.getParametersAsArray();
            serviceArguments.set(args);
            final Object rootObject = root.get();
            final Object callResult = rootObject != null ? method.invoke(rootObject, args) : null;
            final Class<?> returnType = callResult != null ? callResult.getClass() : method.getReturnType();
            if (returnType.isPrimitive()) {
                return callResult;
            }
            if (Modifier.isFinal(returnType.getModifiers())) {
                return callResult;
            } else if (Object.class.equals(returnType)) {
                return ClassImposteriser.INSTANCE.imposterise(BLOCKER, Object.class);
            } else {
                return ClassImposteriser.INSTANCE.imposterise(BLOCKER, returnType);
            }
        }
    }
}
