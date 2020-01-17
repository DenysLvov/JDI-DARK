package com.epam.jdi.httptests.steps;

import com.epam.jdi.httptests.ServiceExample;
import cucumber.api.java.en.Given;

import static com.epam.http.requests.ServiceInit.init;
import static com.epam.jdi.http.cucumber.Utils.*;

public class InitService {
    @Given("^I init service$")
    public void initService() {
        domainUrl.set(getDomain(ServiceExample.class));
        service.set(init(ServiceExample.class));
    }
}
