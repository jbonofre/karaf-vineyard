/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.karaf.vineyard.itests;

import static org.ops4j.pax.exam.CoreOptions.maven;
import static org.ops4j.pax.exam.CoreOptions.mavenBundle;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.configureSecurity;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.editConfigurationFilePut;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.karafDistributionConfiguration;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.keepRuntimeFolder;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.logLevel;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.URL;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.ws.rs.HttpMethod;
import org.apache.karaf.itests.KarafTestSupport;
import org.apache.karaf.jaas.boot.principal.RolePrincipal;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.karaf.options.LogLevelOption;
import org.ops4j.pax.exam.options.MavenArtifactUrlReference;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class VineyardRegistryRestTest extends KarafTestSupport {

    private static final RolePrincipal[] ADMIN_ROLES = {
        new RolePrincipal("admin"), new RolePrincipal("manager")
    };

    @Override
    @Configuration
    public Option[] config() {
        MavenArtifactUrlReference karafUrl =
                maven().groupId("org.apache.karaf")
                        .artifactId("apache-karaf")
                        .versionAsInProject()
                        .type("tar.gz");

        String httpPort =
                Integer.toString(
                        getAvailablePort(
                                Integer.parseInt(MIN_HTTP_PORT), Integer.parseInt(MAX_HTTP_PORT)));
        String rmiRegistryPort =
                Integer.toString(
                        getAvailablePort(
                                Integer.parseInt(MIN_RMI_REG_PORT),
                                Integer.parseInt(MAX_RMI_REG_PORT)));
        String rmiServerPort =
                Integer.toString(
                        getAvailablePort(
                                Integer.parseInt(MIN_RMI_SERVER_PORT),
                                Integer.parseInt(MAX_RMI_SERVER_PORT)));
        String sshPort =
                Integer.toString(
                        getAvailablePort(
                                Integer.parseInt(MIN_SSH_PORT), Integer.parseInt(MAX_SSH_PORT)));
        String localRepository = System.getProperty("org.ops4j.pax.url.mvn.localRepository");
        if (localRepository == null) {
            localRepository = "";
        }

        return new Option[] {
            // KarafDistributionOption.debugConfiguration("8889", true),
            karafDistributionConfiguration()
                    .frameworkUrl(karafUrl)
                    .name("Apache Karaf")
                    .unpackDirectory(new File("target/exam")),
            // enable JMX RBAC security, thanks to the KarafMBeanServerBuilder
            configureSecurity().disableKarafMBeanServerBuilder(),
            // configureConsole().ignoreLocalConsole(),
            keepRuntimeFolder(),
            logLevel(LogLevelOption.LogLevel.INFO),
            mavenBundle().groupId("org.awaitility").artifactId("awaitility").versionAsInProject(),
            mavenBundle()
                    .groupId("org.apache.servicemix.bundles")
                    .artifactId("org.apache.servicemix.bundles.hamcrest")
                    .versionAsInProject(),
            mavenBundle()
                    .groupId("org.apache.karaf.itests")
                    .artifactId("common")
                    .versionAsInProject(),
            editConfigurationFilePut(
                    "etc/org.ops4j.pax.web.cfg", "org.osgi.service.http.port", httpPort),
            editConfigurationFilePut(
                    "etc/org.apache.karaf.management.cfg", "rmiRegistryPort", rmiRegistryPort),
            editConfigurationFilePut(
                    "etc/org.apache.karaf.management.cfg", "rmiServerPort", rmiServerPort),
            editConfigurationFilePut("etc/org.apache.karaf.shell.cfg", "sshPort", sshPort),
            editConfigurationFilePut(
                    "etc/org.ops4j.pax.url.mvn.cfg",
                    "org.ops4j.pax.url.mvn.localRepository",
                    localRepository),
            editConfigurationFilePut(
                    "etc/system.properties",
                    "vineyard.version",
                    System.getProperty("vineyard.version"))
        };
    }

    @Test
    public void testVineyardRegistryFeatureInstall() throws Exception {
        // adding vineyard features repository
        addFeaturesRepository(
                "mvn:org.apache.karaf.vineyard/apache-karaf-vineyard/"
                        + System.getProperty("vineyard.version")
                        + "/xml/features");

        installAndAssertFeature("vineyard");

        Thread.sleep(5000);

        String bundleList = executeCommand("bundle:list");
        System.out.println(bundleList);
        Assert.assertTrue(bundleList.contains("Apache Karaf :: Vineyard :: Common"));
        Assert.assertTrue(bundleList.contains("Apache Karaf :: Vineyard :: Registry"));
        Assert.assertTrue(bundleList.contains("Apache Karaf :: Vineyard :: Gateway"));
        Assert.assertTrue(bundleList.contains("Apache Karaf :: Vineyard :: Importer :: JSON"));

        String jdbcList = executeCommand("jdbc:ds-list");
        System.out.println(jdbcList);
        Assert.assertTrue(jdbcList.contains("jdbc:derby:data/vineyard/derby │ OK"));

        String apiId = "";

        // API
        try {
            String URL = "http://localhost:" + getHttpPort() + "/cxf/vineyard-registry/api";
            URL urlApi = new URL(URL);

            // Call add rest-api
            String jsonAddRestApi =
                    "{\n"
                            + "  \"name\": \"authenticate service\",\n"
                            + "  \"context\": \"api/authenticate\",\n"
                            + "  \"description\": \"use to authenticate user with token\""
                            + "}";
            System.out.println("Call POST " + URL);
            HttpURLConnection connection = (HttpURLConnection) urlApi.openConnection();
            connection.setRequestMethod(HttpMethod.POST);
            connection.setDoOutput(true);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            OutputStream os = connection.getOutputStream();
            os.write(jsonAddRestApi.getBytes());
            os.flush();

            if (connection.getResponseCode() == HttpURLConnection.HTTP_CREATED) {
                Assert.assertTrue(true);
                String location = connection.getHeaderField("Location");
                System.out.println("Location created: " + location);

                urlApi = new URL(location);
                connection = (HttpURLConnection) urlApi.openConnection();
                connection.setRequestMethod(HttpMethod.GET);
                connection.connect();

                StringBuffer sb = new StringBuffer();

                if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    BufferedReader buffer =
                            new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    String line;

                    while ((line = buffer.readLine()) != null) {
                        sb.append(line);
                    }
                    if (sb.length() == 0) {
                        System.out.println("api not found");
                        Assert.assertTrue(false);
                    } else {
                        System.out.println(sb.toString());
                        Assert.assertTrue(true);
                    }
                } else {
                    System.out.println(
                            "Error when sending GET method : HTTP_CODE = "
                                    + connection.getResponseCode());
                    Assert.assertTrue(false);
                }
                connection.disconnect();

                if (sb.length() != 0) {
                    JsonReader reader = Json.createReader(new StringReader(sb.toString()));
                    JsonObject jsonObject = reader.readObject();
                    apiId = jsonObject.getString("id");
                    System.out.println("API id = " + apiId);
                }

            } else {
                System.out.println(
                        "Error when sending POST method : HTTP_CODE = "
                                + connection.getResponseCode());
                Assert.assertTrue(false);
            }

            connection.disconnect();

        } catch (Exception e) {
            System.out.println(e.getMessage());
            Assert.assertTrue(false);
        }

        // RESOURCES
        try {
            String URL =
                    "http://localhost:"
                            + getHttpPort()
                            + "/cxf/vineyard-registry/api/"
                            + apiId
                            + "/rest-resources";
            URL urlResource = new URL(URL);

            // Call add rest-api
            // TODO update
            String jsonAddRestResource =
                    "{\n"
                            + "  \"description\": \"my api\",\n"
                            + "  \"path\": \"/token\",\n"
                            + "  \"method\": \"GET\",\n"
                            + "  \"version\": \"1.0.0\",\n"
                            + "  \"accept\": \"application/json\",\n"
                            + "  \"mediaType\": \"application/json\",\n"
                            + "  \"response\": \"response\",\n"
                            + "  \"endpoint\": \"http://localhost/my-api\""
                            + "}";
            System.out.println("Call POST " + URL);
            HttpURLConnection connection = (HttpURLConnection) urlResource.openConnection();
            connection.setRequestMethod(HttpMethod.POST);
            connection.setDoOutput(true);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            OutputStream os = connection.getOutputStream();
            os.write(jsonAddRestResource.getBytes());
            os.flush();

            if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                Assert.assertTrue(true);

                urlResource = new URL(URL);
                connection = (HttpURLConnection) urlResource.openConnection();
                connection.setRequestMethod(HttpMethod.GET);
                connection.connect();

                StringBuffer sb = new StringBuffer();

                if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    BufferedReader buffer =
                            new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    String line;

                    while ((line = buffer.readLine()) != null) {
                        sb.append(line);
                    }
                    if (sb.length() == 0) {
                        System.out.println("resource not found");
                        Assert.assertTrue(false);
                    } else {
                        System.out.println(sb.toString());
                        Assert.assertTrue(true);
                    }
                } else {
                    System.out.println(
                            "Error when sending GET method : HTTP_CODE = "
                                    + connection.getResponseCode());
                    Assert.assertTrue(false);
                }
                connection.disconnect();

            } else {
                System.out.println(
                        "Error when sending POST method : HTTP_CODE = "
                                + connection.getResponseCode());
                Assert.assertTrue(false);
            }

            connection.disconnect();

        } catch (Exception e) {
            System.out.println(e.getMessage());
            Assert.assertTrue(false);
        }
    }

    @After
    public void tearDown() {
        executeCommand("feature:uninstall vineyard", ADMIN_ROLES);
    }
}
