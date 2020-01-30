package com.epam.jdi.httptests.support;

import org.eclipse.jetty.server.*;
import org.eclipse.jetty.webapp.WebAppContext;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.rules.ExpectedException;

import java.io.File;

public abstract class WithJetty {
    private static Server server;

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @BeforeClass
    public static void startJetty() throws Exception {
        server = new Server();

        HttpConfiguration httpConfig = new HttpConfiguration();
        httpConfig.setSecureScheme("https");
        httpConfig.setSecurePort(8443);
        httpConfig.setOutputBufferSize(32768);

        ServerConnector http = new ServerConnector(server, new HttpConnectionFactory(httpConfig));
        http.setPort(8080);
        http.setIdleTimeout(30000);

        String warpath = "target/webapps/scalatra-webapp.war";
        File warfile = new File(warpath);

        WebAppContext context = new WebAppContext();
        context.setWar(warfile.getAbsolutePath());
        context.setContextPath("/");
        context.setParentLoaderPriority(true);
        server.setHandler(context);
        server.setConnectors(new Connector[]{http});
        server.start();
    }

    @AfterClass
    public static void stopJetty() throws Exception {
        server.stop();
        server.join();
    }

}
