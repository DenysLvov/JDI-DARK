package com.epam.http.requests;

import com.epam.http.JdiHttpSettigns;
import com.epam.http.annotations.ContentType;
import com.epam.http.annotations.Cookie;
import com.epam.http.annotations.Cookies;
import com.epam.http.annotations.DELETE;
import com.epam.http.annotations.FormParameter;
import com.epam.http.annotations.FormParameters;
import com.epam.http.annotations.GET;
import com.epam.http.annotations.HEAD;
import com.epam.http.annotations.Header;
import com.epam.http.annotations.Headers;
import com.epam.http.annotations.MultiPart;
import com.epam.http.annotations.OPTIONS;
import com.epam.http.annotations.PATCH;
import com.epam.http.annotations.POST;
import com.epam.http.annotations.PUT;
import com.epam.http.annotations.Proxy;
import com.epam.http.annotations.QueryParameter;
import com.epam.http.annotations.QueryParameters;
import com.epam.http.annotations.ServiceDomain;
import com.epam.http.annotations.URL;
import com.epam.http.requests.errorhandler.ErrorHandler;
import com.epam.jdi.tools.func.JAction;
import com.epam.jdi.tools.map.MapArray;
import com.epam.jdi.tools.pairs.Pair;
import io.restassured.authentication.AuthenticationScheme;
import io.restassured.mapper.ObjectMapper;
import io.restassured.specification.RequestSpecification;

import java.lang.reflect.Field;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.epam.http.ExceptionHandler.exception;
import static com.epam.http.requests.RestMethodTypes.DELETE;
import static com.epam.http.requests.RestMethodTypes.GET;
import static com.epam.http.requests.RestMethodTypes.HEAD;
import static com.epam.http.requests.RestMethodTypes.OPTIONS;
import static com.epam.http.requests.RestMethodTypes.PATCH;
import static com.epam.http.requests.RestMethodTypes.POST;
import static com.epam.http.requests.RestMethodTypes.PUT;
import static com.epam.jdi.tools.LinqUtils.where;
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
            new MapArray<>("WebSettings", JdiHttpSettigns::init);
    public static boolean initialized = false;

    public static void preInit() {
        if (PRE_INIT == null) return;
        if (!initialized) {
            for (Pair<String, JAction> action : PRE_INIT)
                try {
                    action.value.execute();
                } catch (Exception ex) {
                    throw exception("Preinit '%s' failed. Please correct PageFactory.PRE_INIT function", action.key);
                }
            initialized = true;
        }
    }



    /**
     * Initialise the Service Object class.
     *
     * @param c class describing Service
     * @return initialised Service Object
     */
    public static <T> T init(Class<T> c) {
        return init(c, ServiceSettings.builder().build());
    }


    /**
     * Initialise the Service Object class.
     *
     * @param c                    class describing Service
     * @param serviceSettings predefined settings for service
     * @return initialised Service Object
     */
    public static <T> T init(Class<T> c, ServiceSettings serviceSettings) {
        preInit();
        List<Field> methods = where(c.getDeclaredFields(),
                f -> f.getType().equals(RestMethod.class));
        for (Field method : methods) {
            try {
                method.setAccessible(true);
                if (isStatic(method.getModifiers()))
                    method.set(null, getRestMethod(method, c, serviceSettings.getRequestSpecification(), serviceSettings.getObjectMapper(), serviceSettings.getErrorHandler(), serviceSettings.getAuthenticationScheme()));
                if (!isStatic(method.getModifiers()) && method.get(getService(c)) == null)
                    method.set(getService(c), getRestMethod(method, c, serviceSettings.getRequestSpecification(), serviceSettings.getObjectMapper(), serviceSettings.getErrorHandler(), serviceSettings.getAuthenticationScheme()));
            } catch (IllegalAccessException ex) {
                throw exception("Can't init method %s for class %s", method.getName(), c.getName());
            }
        }
        return getService(c);
    }


    private static Object service;

    /**
     * Helper method to instantiate the class.
     *
     * @param c class describing Service
     * @return instantiated service
     */
    private static <T> T getService(Class<T> c) {
        if (service != null && service.getClass().equals(c)) return (T) service;
        try {
            return (T) (service = c.newInstance());
        } catch (IllegalAccessException | InstantiationException ex) {
            throw exception(
                    "Can't instantiate class %s, Service class should have empty constructor",
                    c.getSimpleName());
        }
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
    private static <T> RestMethod getRestMethod(Field field, Class<T> c, RequestSpecification requestSpecification, ObjectMapper objectMapper, ErrorHandler errorHandler, AuthenticationScheme authenticationScheme) {
        MethodData mtData = getMethodData(field);
        String url = field.isAnnotationPresent(URL.class) ? field.getAnnotation(URL.class).value() : getDomain(c);
        String path = mtData.getPath();
        RestMethod method = new RestMethod(mtData.getType(), url, path, requestSpecification);
        method.setObjectMapper(objectMapper);
        method.setErrorHandler(errorHandler);
        method.setAuthenticationScheme(authenticationScheme);
        if (field.isAnnotationPresent(ContentType.class))
            method.setContentType(field.getAnnotation(ContentType.class).value());
        if (field.isAnnotationPresent(Header.class))
            method.addHeader(field.getAnnotation(Header.class));
        if (field.isAnnotationPresent(Headers.class))
            method.addHeaders(field.getAnnotation(Headers.class).value());
        if (field.isAnnotationPresent(Cookie.class)) {
            setupCookie(method, field.getAnnotation(Cookie.class));
        }
        if (field.isAnnotationPresent(Cookies.class)) {
            for (Cookie cookie : field.getAnnotation(Cookies.class).value()) {
                setupCookie(method, cookie);
            }
        }
        /* Case for class annotations*/
        if (c.isAnnotationPresent(QueryParameter.class))
            method.addQueryParameters(c.getAnnotation(QueryParameter.class));
        if (c.isAnnotationPresent(QueryParameters.class))
            method.addQueryParameters(c.getAnnotation(QueryParameters.class).value());
        if (c.isAnnotationPresent(FormParameter.class))
            method.addFormParameters(c.getAnnotation(FormParameter.class));
        if (c.isAnnotationPresent(FormParameters.class))
            method.addFormParameters(c.getAnnotation(FormParameters.class).value());
        /* Case for method annotations*/
        if (field.isAnnotationPresent(QueryParameter.class))
            method.addQueryParameters(field.getAnnotation(QueryParameter.class));
        if (field.isAnnotationPresent(QueryParameters.class))
            method.addQueryParameters(field.getAnnotation(QueryParameters.class).value());
        if (field.isAnnotationPresent(FormParameter.class))
            method.addFormParameters(field.getAnnotation(FormParameter.class));
        if (field.isAnnotationPresent(FormParameters.class))
            method.addFormParameters(field.getAnnotation(FormParameters.class).value());
        if (field.isAnnotationPresent(MultiPart.class))
            method.addMultiPartParams(field.getAnnotation(MultiPart.class));
        if (field.isAnnotationPresent(Proxy.class))
            method.setProxy(field.getAnnotation(Proxy.class));
        return method;
    }

    private static void setupCookie(RestMethod method, Cookie cookie) {
        if (cookie.value().equals("[unassigned]")) {
            method.addCookie(cookie.name());
        } else if (cookie.additionalValues()[0].equals("[unassigned]")) {
            method.addCookie(cookie.name(), cookie.value());
        } else {
            method.addCookie(cookie.name(), cookie.value(), cookie.additionalValues());
        }
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
     * Get and check URL from request data.
     *
     * @param domain     string
     * @param uri        adres string
     * @param methodName string
     * @param className className
     * @return normalized URL as string
     */
    private static String getUrlFromDomain(String domain, String uri, String methodName, String className) {
        if (uri == null)
            return null;
        if (uri.contains("://"))
            return uri;
        if (domain == null)
            throw exception(
                    "Can't instantiate method '%s' for service '%s'. " +
                            "Domain undefined and method url not contains '://'",
                    methodName, className);
        return domain.replaceAll("/*$", "");
    }

    /**
     * Get service domain.
     *
     * @param c Service Object class
     * @return service domain string
     */
    private static <T> String getDomain(Class<T> c) {
        if (c.isAnnotationPresent(ServiceDomain.class)) {
            Matcher m = Pattern.compile("\\$\\{(.*)}").matcher(c.getAnnotation(ServiceDomain.class).value());
            if (m.find()) {
                return JdiHttpSettigns.getDomain(m.group(1));
            }
            else {
                return c.getAnnotation(ServiceDomain.class).value();
            }
        }
        return JdiHttpSettigns.getDomain();
    }
}
