/**
 *
 * Copyright (c) 2014, Deem Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 *
 */
package com.deem.zkui;

import com.deem.zkui.dao.Dao;
import java.io.File;
import java.io.FileInputStream;
import java.util.Date;
import java.util.Collections;
import javax.servlet.SessionTrackingMode;
import java.util.Properties;
import org.eclipse.jetty.ee8.annotations.AnnotationConfiguration;
import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.util.resource.ResourceFactory;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.ee8.webapp.Configuration;
import org.eclipse.jetty.ee8.webapp.WebInfConfiguration;
import org.eclipse.jetty.ee8.webapp.WebXmlConfiguration;
import org.eclipse.jetty.ee8.webapp.MetaInfConfiguration;
import org.eclipse.jetty.ee8.webapp.FragmentConfiguration;
import org.eclipse.jetty.ee8.webapp.JettyWebXmlConfiguration;
import org.eclipse.jetty.ee8.webapp.WebAppContext;
import org.eclipse.jetty.ee8.webapp.WebAppConfiguration;
import org.slf4j.LoggerFactory;

public class Main {

    private final static org.slf4j.Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) throws Exception {

        logger.debug("Starting ZKUI!");
        Properties globalProps = new Properties();
        File f = new File("config.cfg");
        if (f.exists()) {
            globalProps.load(new FileInputStream("config.cfg"));
        } else {
            System.out.println("Please create config.cfg properties file and then execute the program!");
            System.exit(1);
        }

        com.deem.zkui.utils.EnvironmentConfig.apply(globalProps, System.getenv());
        com.deem.zkui.utils.TreeWork.configureBuffer(globalProps);
        String contextPath = com.deem.zkui.utils.WebPaths.context(globalProps.getProperty("baseURL", "/"));
        String urlPrefix = "/".equals(contextPath) ? "" : contextPath;
        new com.deem.zkui.utils.PathPolicy(globalProps).hosts(globalProps);
        com.deem.zkui.utils.ZkClientAuth.validate(globalProps);
        com.deem.zkui.utils.ZooKeeperUtil.INSTANCE.setDefaultAcl(globalProps.getProperty("defaultAcl"));
        globalProps.setProperty("uptime", new Date().toString());
        Dao history = new Dao(globalProps);
        history.checkNCreate();
        com.deem.zkui.utils.HostsBootstrap.Result bootstrap = com.deem.zkui.utils.HostsBootstrap.initialize(globalProps);
        if (bootstrap.changed()) history.insertHistory("startup", "local",
                "Hosts bootstrap created=" + bootstrap.created() + "; updated=" + bootstrap.updated());

        String webFolder = "webapp";
        Server server = new Server();
        Resource staticResources = ResourceFactory.of(server).newClassLoaderResource(webFolder);

        WebAppContext servletContextHandler = new WebAppContext();
        servletContextHandler.setContextPath(contextPath);
        servletContextHandler.setThrowUnavailableOnStartupException(true);
        servletContextHandler.setProtectedTargets(new String[]{"/WEB-INF", "/META-INF", "/template"});
        servletContextHandler.getSessionHandler().getSessionCookieConfig().setHttpOnly(true);
        servletContextHandler.getSessionHandler().setSessionTrackingModes(Collections.singleton(SessionTrackingMode.COOKIE));
        servletContextHandler.setBaseResource(staticResources);
        // Explicit configuration also works in the assembled JAR, where service files may overlap.
        servletContextHandler.setConfigurations(new Configuration[]{new WebAppConfiguration(), new WebInfConfiguration(),
            new WebXmlConfiguration(), new MetaInfConfiguration(), new FragmentConfiguration(),
            new AnnotationConfiguration(), new JettyWebXmlConfiguration()});
        servletContextHandler.setAttribute("org.eclipse.jetty.server.webapp.ContainerIncludeJarPattern", ".*(/target/classes/|.*.jar)");
        servletContextHandler.setParentLoaderPriority(true);
        servletContextHandler.setInitParameter("useFileMappedBuffer", "false");
        servletContextHandler.setAttribute("globalProps", globalProps);

        ResourceHandler staticResourceHandler = new ResourceHandler() {
            @Override public boolean handle(org.eclipse.jetty.server.Request request,
                    org.eclipse.jetty.server.Response response, org.eclipse.jetty.util.Callback callback)
                    throws Exception {
                String path = org.eclipse.jetty.server.Request.getPathInContext(request);
                // Only assets bypass authentication, never templates or servlet paths.
                if (!(path.startsWith("/css/") || path.startsWith("/js/")
                        || path.startsWith("/images/") || path.startsWith("/fonts/"))) return false;
                return super.handle(request, response, callback);
            }
        };
        staticResourceHandler.setDirAllowed(false);
        staticResourceHandler.setBaseResource(staticResources);
        staticResourceHandler.setWelcomeFiles(new String[]{"html/index.html"});

        org.eclipse.jetty.server.handler.ContextHandler staticContext = new org.eclipse.jetty.server.handler.ContextHandler();
        staticContext.setContextPath(contextPath);
        staticContext.setHandler(staticResourceHandler);
        Handler indexRedirect = new Handler.Abstract() {
            @Override public boolean handle(org.eclipse.jetty.server.Request request,
                    org.eclipse.jetty.server.Response response, org.eclipse.jetty.util.Callback callback)
                    throws Exception {
                String uri = request.getHttpURI().getPath();
                if (uri.equals(urlPrefix + "/") || (!urlPrefix.isEmpty() && uri.equals(urlPrefix))
                        || uri.equals(urlPrefix + "/html/index.html")) {
                    org.eclipse.jetty.server.Response.sendRedirect(request, response, callback, urlPrefix + "/home");
                    return true;
                }
                return false;
            }
        };
        Handler.Sequence handlers = new Handler.Sequence(indexRedirect, staticContext, servletContextHandler.get());

        server.setHandler(handlers);
        HttpConfiguration http_config = new HttpConfiguration();
        http_config.setSecureScheme("https");
        http_config.setSecurePort(Integer.parseInt(globalProps.getProperty("serverPort")));
        
        if ("true".equalsIgnoreCase(globalProps.getProperty("https", "false"))) {
            File keystoreFile = new File(globalProps.getProperty("keystoreFile"));
            SslContextFactory.Server sslContextFactory = new SslContextFactory.Server();
            sslContextFactory.setKeyStorePath(keystoreFile.getAbsolutePath());
            sslContextFactory.setKeyStorePassword(globalProps.getProperty("keystorePwd"));
            sslContextFactory.setKeyManagerPassword(globalProps.getProperty("keystoreManagerPwd"));
            HttpConfiguration https_config = new HttpConfiguration(http_config);
            https_config.addCustomizer(new SecureRequestCustomizer());

            ServerConnector https = new ServerConnector(server, new SslConnectionFactory(sslContextFactory, HttpVersion.HTTP_1_1.asString()), new HttpConnectionFactory(https_config));
            https.setPort(Integer.parseInt(globalProps.getProperty("serverPort")));
            server.setConnectors(new Connector[]{https});
        } else {
            if("true".equalsIgnoreCase(globalProps.getProperty("X-Forwarded-For", "false"))) {
                http_config.addCustomizer(new org.eclipse.jetty.server.ForwardedRequestCustomizer());
            }
            ServerConnector http = new ServerConnector(server, new HttpConnectionFactory(http_config));
            http.setPort(Integer.parseInt(globalProps.getProperty("serverPort")));
            server.setConnectors(new Connector[]{http});
        }

        server.start();
        server.join();
    }

}
