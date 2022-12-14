package com.epam.http.requests;

import com.epam.http.JdiHttpSettings;
import com.epam.http.annotations.DELETE;
import com.epam.http.annotations.GET;
import com.epam.http.annotations.HEAD;
import com.epam.http.annotations.OPTIONS;
import com.epam.http.annotations.PATCH;
import com.epam.http.annotations.POST;
import com.epam.http.annotations.PUT;
import com.epam.http.annotations.*;
import com.epam.http.requests.errorhandler.ErrorHandler;
import com.jdiai.tools.func.JAction;
import com.jdiai.tools.map.MapArray;
import com.jdiai.tools.pairs.Pair;
import io.restassured.authentication.AuthenticationScheme;
import io.restassured.mapper.ObjectMapper;
import io.restassured.specification.RequestSpecification;

import java.lang.reflect.Field;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.epam.http.ExceptionHandler.exception;
import static com.epam.http.JdiHttpSettings.logger;
import static com.epam.http.requests.RestMethodTypes.*;
import static com.jdiai.tools.LinqUtils.where;
import static com.jdiai.tools.ReflectionUtils.isClass;
import static java.lang.reflect.Modifier.isStatic;

/**
 * The entry point for initialising the Service Object classes.
 * In order to effectively use JDI HTTP it's recommended to statically import:
 * <pre>
 *     {@code com.epam.http.requests.ServiceInit.init}
 * </pre>
 *
 * @author <a href="mailto:roman.iovlev.jdi@gmail.com">Roman_Iovlev</a>
 */
public class ServiceInit {

    public static MapArray<String, JAction> PRE_INIT =
            new MapArray<>("WebSettings", JdiHttpSettings::init);
    public static boolean initialized = false;

    public static void preInit() {
        if (PRE_INIT == null) return;
        if (!initialized) {
            for (Pair<String, JAction> action : PRE_INIT)
                try {
                    action.value.execute();
                } catch (Exception ex) {
                    logger.error(ex.getMessage());
                    throw exception("Preinit '%s' failed. Please correct PageFactory.PRE_INIT function", action.key);
                }
            initialized = true;
        }
    }


    /**
     * Initialise the Service Object class.
     *
     * @param c   class describing Service
     * @param <T> type
     * @return initialised Service Object
     */
    public static <T> T init(Class<T> c) {
        return init(c, ServiceSettings.builder().build());
    }


    /**
     * Initialise the Service Object class.
     *
     * @param c               class describing Service
     * @param <T>             type
     * @param serviceSettings predefined settings for service
     * @return initialised Service Object
     */
    public static <T> T init(Class<T> c, ServiceSettings serviceSettings) {
        preInit();
        T instance = getService(c);
        List<Field> methods = where(c.getDeclaredFields(),
                f -> isClass(f.getType(), RestMethod.class));
        for (Field method : methods) {
            try {
                method.setAccessible(true);
                Object rm = getRestMethod(method, c, serviceSettings.getRequestSpecification(), serviceSettings.getObjectMapper(), serviceSettings.getErrorHandler(), serviceSettings.getAuthenticationScheme(), serviceSettings.getDomain());
                if (isStatic(method.getModifiers())) {
                    method.set(null, rm);
                    }
                else {
                    method.set(instance, rm);
                    }
            } catch (Throwable ex) {
                logger.error(ex.getMessage());
                throw exception("Can't init method %s for class %s", method.getName(), c.getName());
            }
        }
        return instance;
    }

    /**
     * Initialise the Service Object class.
     *
     * @param c                    class describing Service
     * @param <T>                  type
     * @param requestSpecification predefined request specification
     * @return initialised Service Object
     */
    public static <T> T init(Class<T> c, RequestSpecification requestSpecification) {
        return init(c, ServiceSettings.builder().requestSpecification(requestSpecification).build());
    }

    /**
     * Initialise the Service Object class.
     *
     * @param c                    class describing Service
     * @param <T>                  type
     * @param authenticationScheme predefined authenticationScheme
     * @return initialised Service Object
     */
    public static <T> T init(Class<T> c, AuthenticationScheme authenticationScheme) {
        return init(c, ServiceSettings.builder().authenticationScheme(authenticationScheme).build());
    }
    /**
     * Initialise the Service Object class.
     *
     * @param c class describing Service
     * @param <T> type
     * @param domain domain string
     * @return initialised Service Object
     */
    public static <T> T init(Class<T> c, String domain) {
        return init(c, ServiceSettings.builder().domain(domain).build());
    }

    /**
     * Helper method to instantiate the class.
     *
     * @param c class describing Service
     * @return instantiated service
     */
    private static <T> T getService(Class<T> c) {
        try {
            return c.newInstance();
        } catch (IllegalAccessException | InstantiationException ex) {
            throw exception(
                    "Can't instantiate class %s, Service class should have empty constructor",
                    c.getSimpleName());
        }
    }

    /**
     * @param field
     * @param c
     * @param requestSpecification
     * @param objectMapper
     * @param errorHandler
     * @param authenticationScheme
     * @param domain
     * @param <T>
     * @return
     */
    private static <T> Object getRestMethod(
            Field field,
            Class<T> c,
            RequestSpecification requestSpecification,
            ObjectMapper objectMapper,
            ErrorHandler errorHandler,
            AuthenticationScheme authenticationScheme,
            String domain) {
    MethodData mtData = getMethodData(field);
    String url;
        if (domain == null) {
            url = field.isAnnotationPresent(URL.class)
                            ? field.getAnnotation(URL.class).value()
                            : getDomain(c);
        } else {
            url = replaceTemplateVariableInDomain(domain);
        }
    String path = mtData.path;
    RestMethod method = field.getType() == SoapMethod.class
            ? new SoapMethod<>(field, c)
            : field.getType() == RestDataMethod.class
                    ? new RestDataMethod<>(field)
                    : new RestMethod();
    method.setup(mtData.type, path, url, requestSpecification);
    method.setObjectMapper(objectMapper);
    method.setErrorHandler(errorHandler);
    method.data.setAuthScheme(authenticationScheme);
    if (field.isAnnotationPresent(ContentType.class))
        method.data.setContentType(field.getAnnotation(ContentType.class).value());
    if (field.isAnnotationPresent(Header.class))
        method.header.add(field.getAnnotation(Header.class));
    if (field.isAnnotationPresent(Headers.class))
        method.header.addAll(field.getAnnotation(Headers.class).value());
    if (field.isAnnotationPresent(Cookie.class)) {
        setupCookie(method, field.getAnnotation(Cookie.class));
    }
    if (field.isAnnotationPresent(Cookies.class)) {
        for (Cookie cookie : field.getAnnotation(Cookies.class).value()) {
            setupCookie(method, cookie);
        }
    }
    /* Case for class annotations*/
    if (c.isAnnotationPresent(Header.class))
        method.header.add(c.getAnnotation(Header.class));
    if (c.isAnnotationPresent(Headers.class))
        method.header.addAll(c.getAnnotation(Headers.class).value());
    if (c.isAnnotationPresent(Cookie.class)) {
        setupCookie(method, c.getAnnotation(Cookie.class));
    }
    if (c.isAnnotationPresent(Cookies.class)) {
        for (Cookie cookie : c.getAnnotation(Cookies.class).value()) {
            setupCookie(method, cookie);
        }
    }
    if (c.isAnnotationPresent(QueryParameter.class))
        method.queryParams.add(c.getAnnotation(QueryParameter.class));
    if (c.isAnnotationPresent(QueryParameters.class))
        method.queryParams.addAll(c.getAnnotation(QueryParameters.class).value());
    if (c.isAnnotationPresent(FormParameter.class))
        method.formParams.add(c.getAnnotation(FormParameter.class));
    if (c.isAnnotationPresent(FormParameters.class))
        method.formParams.addAll(c.getAnnotation(FormParameters.class).value());
    if (c.isAnnotationPresent(TrustStore.class))
        method.data.setTrustStore(c.getAnnotation(TrustStore.class));
    if (c.isAnnotationPresent(RetryOnFailure.class))
        method.reTryData = new RetryData(c.getAnnotation(RetryOnFailure.class));
    /* Case for method annotations*/
    if (field.isAnnotationPresent(QueryParameter.class))
        method.queryParams.add(field.getAnnotation(QueryParameter.class));
    if (field.isAnnotationPresent(QueryParameters.class))
        method.queryParams.addAll(field.getAnnotation(QueryParameters.class).value());
    if (field.isAnnotationPresent(FormParameter.class))
        method.formParams.add(field.getAnnotation(FormParameter.class));
    if (field.isAnnotationPresent(FormParameters.class))
        method.formParams.addAll(field.getAnnotation(FormParameters.class).value());
    if (field.isAnnotationPresent(MultiPart.class))
        method.addMultiPartParams(field.getAnnotation(MultiPart.class));
    if (field.isAnnotationPresent(Proxy.class))
        method.data.setProxySpec(field.getAnnotation(Proxy.class));
    if (field.isAnnotationPresent(TrustStore.class))
        method.data.setTrustStore(field.getAnnotation(TrustStore.class));
    if (field.isAnnotationPresent(RetryOnFailure.class)) {
        RetryOnFailure methodRetryData = field.getAnnotation(RetryOnFailure.class);
        method.reTryData = (method.reTryData != null) ? method.reTryData.merge(methodRetryData) :
                new RetryData(methodRetryData);
    }
    if (field.isAnnotationPresent(IgnoreRetry.class))
        method.reTryData = null;
    return method;
}

    /**
     * Check whether the annotation present and add these values to request data.
     *
     * @param field                HTTP method described in Service Object class as a field
     * @param c                    class describing service
     * @param requestSpecification custom request specification
     * @param objectMapper         custom ObjectMapper
     * @return http method with request data
     */
    private static <T> Object getRestMethod(Field field, Class<T> c, RequestSpecification requestSpecification, ObjectMapper objectMapper, ErrorHandler errorHandler, AuthenticationScheme authenticationScheme) {
        return getRestMethod(field, c, requestSpecification, objectMapper, errorHandler, authenticationScheme, null);
    }

    private static void setupCookie(RestMethod method, Cookie cookie) {
        if (cookie.value().equals("[unassigned]")) {
            method.cookies.add(cookie.name());
        } else
            method.cookies.add(cookie);
        if (!cookie.additionalValues()[0].equals("[unassigned]"))
            method.cookies.add(cookie.name(), cookie.additionalValues());
    }

    /**
     * Create method data.
     *
     * @param method annotated method field
     * @return method data with url and type of request
     */
    private static MethodData getMethodData(Field method) {
        if (method.isAnnotationPresent(GET.class))
            return new MethodData(method.getAnnotation(GET.class).value(), GET);
        if (method.isAnnotationPresent(POST.class))
            return new MethodData(method.getAnnotation(POST.class).value(), POST);
        if (method.isAnnotationPresent(PUT.class))
            return new MethodData(method.getAnnotation(PUT.class).value(), PUT);
        if (method.isAnnotationPresent(DELETE.class))
            return new MethodData(method.getAnnotation(DELETE.class).value(), DELETE);
        if (method.isAnnotationPresent(PATCH.class))
            return new MethodData(method.getAnnotation(PATCH.class).value(), PATCH);
        if (method.isAnnotationPresent(HEAD.class))
            return new MethodData(method.getAnnotation(HEAD.class).value(), HEAD);
        if (method.isAnnotationPresent(OPTIONS.class))
            return new MethodData(method.getAnnotation(OPTIONS.class).value(), OPTIONS);
        return new MethodData(null, GET);
    }

    /**
     * Get service domain.
     *
     * @param c Service Object class
     * @return service domain string
     */
    private static <T> String getDomain(Class<T> c) {
        if (!c.isAnnotationPresent(ServiceDomain.class)) return JdiHttpSettings.getDomain();
        final String valueFromAnnotation = c
                .getAnnotation(ServiceDomain.class)
                .value();

        return replaceTemplateVariableInDomain(valueFromAnnotation);
    }

    /**
     * Replace template variable in domain url if present
     *
     * @param domain service domain string
     * @param <T>
     * @return service domain string
     */
    private static <T> String replaceTemplateVariableInDomain(String domain) {
        Matcher m = Pattern.compile("\\$\\{(.*)}").matcher(domain);
        return m.find() ? m.replaceFirst(JdiHttpSettings.getDomain(m.group(1))) : domain;
    }
}
