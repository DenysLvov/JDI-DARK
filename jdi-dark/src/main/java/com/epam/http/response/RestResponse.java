package com.epam.http.response;

import com.epam.http.logger.AllureLogger;
import com.jdiai.tools.func.JAction1;
import com.jdiai.tools.func.JAction2;
import com.jdiai.tools.map.MapArray;
import com.jdiai.tools.pairs.Pair;
import io.restassured.http.Header;
import io.restassured.http.Headers;
import io.restassured.mapper.ObjectMapper;
import io.restassured.response.Response;
import io.restassured.response.ValidatableResponse;
import org.hamcrest.Matcher;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static com.epam.http.ExceptionHandler.exception;
import static com.epam.http.JdiHttpSettings.logger;
import static com.epam.http.response.ResponseStatusType.CLIENT_ERROR;
import static com.epam.http.response.ResponseStatusType.OK;
import static com.jdiai.tools.StringUtils.LINE_BREAK;
import static java.lang.String.format;

/**
 * Represents full HTTP response.
 *
 * @author <a href="mailto:roman.iovlev.jdi@gmail.com">Roman_Iovlev</a>
 */
public class RestResponse {
    private final Response raResponse;
    private final long responseTimeMSec;
    private String body = null;
    private ResponseStatus status = null;
    private String contentType = "";
    public static JAction2<RestResponse, String> LOG_RESPONSE = RestResponse::logResponse;
    private final static JAction2<RestResponse, String> LOG_RESPONSE_DEFAULT = LOG_RESPONSE;

    public ResponseStatus getResponseStatus() {
        return status;
    }

    public String getContentType() {
        return this.contentType;
    }

    public RestResponse() {
        this.raResponse = null;
        responseTimeMSec = 0;
    }

    public RestResponse(Response raResponse) {
        this.raResponse = raResponse;
        responseTimeMSec = raResponse.getTime();
        body = raResponse.body().asString();
        status = new ResponseStatus(raResponse);
        contentType = raResponse.contentType();
    }

    public static void resetLogResponse() {
        LOG_RESPONSE = LOG_RESPONSE_DEFAULT;
    }

    public void logResponse(String uuid) {
        logger.info(toString());
        AllureLogger.passStep(toString(), uuid);
    }

    public RestResponse set(JAction1<RestResponse> valueFunc) {
        RestResponse thisObj = this;
        valueFunc.execute(thisObj);
        return thisObj;
    }

    public boolean verify(Function<RestResponse, Boolean> validator) {
        return validator.apply(this);
    }

    /**
     * Check the validity of the response.
     *
     * @param validator function to validate assuming the result would be boolean
     * @return Rest Assured validatable response
     */
    public ValidatableResponse validate(Function<RestResponse, Boolean> validator) {
        if (!verify(validator))
            throw exception("Bad raResponse: " + toString());
        return assertThat();
    }

    /**
     * Check if response status is OK(code starts with 2).
     *
     * @return result of assertion
     */
    public ValidatableResponse isOk() {
        return isStatus(OK);
    }

    /**
     * Check if response status has any errors.
     *
     * @return result of assertion
     */
    public ValidatableResponse hasErrors() {
        return isStatus(CLIENT_ERROR);
    }

    /**
     * Validate the status.
     *
     * @param type of status as enumeration value
     * @return result of assertion
     */
    public ValidatableResponse isStatus(ResponseStatusType type) {
        return validate(r -> status.type == type);
    }

    public String getBody() {
        return this.body;
    }

    public ResponseStatus getStatus() {
        return this.status;
    }


    public ValidatableResponse isEmpty() {
        return validate(r -> body.isEmpty());
    }

    /**
     * Check response body according to expected values.
     *
     * @param params key name and matcher with expected value for that key
     * @return Rest Assured response
     */
    public ValidatableResponse assertBody(MapArray<String, Matcher<?>> params) {
        ValidatableResponse vr = assertThat();
        try {
            for (Pair<String, Matcher<?>> pair : params)
                vr.body(pair.key, pair.value);
            return vr;
        } catch (Exception ex) {
            throw new RuntimeException("Only <String, Matcher> pairs available for assertBody");
        }
    }

    /**
     * Check response body according to expected values.
     *
     * @param params key name and matcher with expected value for that key
     * @return Rest Assured response
     */
    public ValidatableResponse assertBody(Object[][] params) {
        return assertBody(new MapArray<>(params));
    }

    /**
     * Get text/html media type content by path.
     *
     * @param path the HTML path
     * @return string matching the provided HTML path
     */
    public String getFromHtml(String path) {
        return raResponse.body().htmlPath().getString(path);
    }

    /**
     * Get response headers as list.
     *
     * @return response headers list
     */
    public List<Header> headersAsList() {
        return raResponse.getHeaders().asList();
    }

    /**
     * Get response headers as list.
     *
     * @return response headers list
     */
    public Headers headers() {
        return raResponse.getHeaders();
    }

    /**
     * Get response header value.
     *
     * @param name header name
     * @return response header value
     */
    public String header(String name) {
        return raResponse.getHeader(name);
    }

    /**
     * Get response cookie associated by the given name.
     *
     * @param name cookie key name
     * @return cookie value
     */
    public String cookie(String name) {
        return raResponse.getCookie(name);
    }

    /**
     * Get response cookie associated by the given name.
     *
     * @return cookie value
     */
    public Map<String, String> cookies() {
        return raResponse.getCookies();
    }

    /**
     * Get Rest Assured response.
     *
     * @return Rest Assured response
     */
    public Response getRaResponse() {
        return raResponse;
    }

    /**
     * Time taken to perform HTTP request.
     *
     * @return time
     */
    public long responseTime() {
        return responseTimeMSec;
    }

    /**
     * Returns validatable response.
     *
     * @return validatable Rest Assured response
     */
    public ValidatableResponse assertThat() {
        return raResponse.then();
    }

    public <T> T asData(Class<T> cl) {
        isOk();
        return getRaResponse().as(cl);
    }

    public <T> T asData(Class<T> cl, ObjectMapper objectMapper) {
        isOk();
        return getRaResponse().as(cl, objectMapper);
    }

    public <T> T asData(Class<T> cl, String responseType) {
        isOk();
        switch (responseType) {
            case "List":
                return (T) Arrays.asList((T[]) getRaResponse().as(cl));
            case "Map":
                return (T) getRaResponse().jsonPath().getMap("$", String.class, HashMap.class);
            default:
                return getRaResponse().as(cl);
        }
    }

    /**
     * Verify the status of response.
     *
     * @param rs expected response status containing code, type and text message
     * @return response
     */
    public RestResponse assertStatus(ResponseStatus rs) {
        String errors = "";
        if (status.code != rs.code)
            errors += format("Wrong status code %s. Expected: %s", status.code, rs.code) + LINE_BREAK;
        if (!status.type.equals(rs.type))
            errors += format("Wrong status type %s. Expected: %s", status.type, rs.type) + LINE_BREAK;
        if (!status.text.equals(rs.text))
            errors += format("Wrong status text %s. Expected: %s", status.text, rs.text);
        if (!errors.isEmpty())
            throw exception(errors);
        return this;
    }

    @Override
    public String toString() {
        return format("Response status: %s %s (%s)", status.code, status.text, status.type) + LINE_BREAK +
                "Response body: " + body;
    }
}
