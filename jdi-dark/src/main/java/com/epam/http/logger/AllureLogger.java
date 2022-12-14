package com.epam.http.logger;

import io.qameta.allure.Allure;
import io.qameta.allure.model.StepResult;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

import static com.epam.http.logger.LogLevels.getLogbackLevel;
import static io.qameta.allure.aspects.StepsAspects.getLifecycle;
import static io.qameta.allure.model.Status.FAILED;
import static io.qameta.allure.model.Status.PASSED;

public class AllureLogger {
    public static boolean writeToAllure = getLifecycle().getCurrentTestCase().isPresent();

    public static void setAllureRootLogLevel(LogLevels level) {
        Logger logger = LoggerFactory.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);
        if (logger instanceof ch.qos.logback.classic.Logger) {
            ((ch.qos.logback.classic.Logger) logger).setLevel(getLogbackLevel(level));
        }
    }

    public static String startStep(String message, String requestData) {
        if (!writeToAllure) return "";
        StepResult step = new StepResult().setName(message).setStatus(PASSED);

        String uuid = UUID.randomUUID().toString();
        getLifecycle().startStep(uuid, step);
        attachRequest(message, requestData);
        return uuid;
    }

    public static void failStep() {
        if (!writeToAllure) return;

        getLifecycle().updateStep(s -> s.setStatus(FAILED));
        getLifecycle().stopStep();
    }

    public static void passStep(String responseData, String uuid) {
        if (!writeToAllure || StringUtils.isBlank(uuid)) return;

        getLifecycle().updateStep(uuid, s -> s.setStatus(PASSED));
        attachResponse(responseData);
        getLifecycle().stopStep();
    }

    public static void attachRequest(String message, String requestData) {
        if (!writeToAllure) return;
        Allure.addAttachment("Request " + message, "text/html", requestData, "json");
    }

    public static void attachResponse(String responseData) {
        if (!writeToAllure) return;
        Allure.addAttachment("Response", "text/html", responseData, "json");
    }

}
