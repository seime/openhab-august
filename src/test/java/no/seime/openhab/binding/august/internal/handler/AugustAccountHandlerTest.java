/**
 * Copyright (c) 2023 Contributors to the Seime Openhab Addons project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package no.seime.openhab.binding.august.internal.handler;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import org.eclipse.jetty.client.HttpClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openhab.core.config.core.Configuration;
import org.openhab.core.storage.Storage;
import org.openhab.core.test.storage.VolatileStorage;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ThingUID;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.google.gson.Gson;

import no.seime.openhab.binding.august.internal.AuthenticationStatus;
import no.seime.openhab.binding.august.internal.GsonFactory;
import no.seime.openhab.binding.august.internal.comm.RestApiClient;
import no.seime.openhab.binding.august.internal.config.AccountConfiguration;
import no.seime.openhab.binding.august.internal.model.Lock;

/**
 * 
 * @author Arne Seime - Initial contribution
 */
@ExtendWith(MockitoExtension.class)
class AugustAccountHandlerTest {

    private WireMockServer wireMockServer;

    private HttpClient httpClient;

    private @Mock Configuration configuration;
    private @Mock Bridge bridge;

    private Storage<String> storage;

    private RestApiClient restApiClient;

    private Gson gson = GsonFactory.create();

    @BeforeEach
    public void setUp() throws Exception {
        wireMockServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMockServer.start();

        int port = wireMockServer.port();
        WireMock.configureFor("localhost", port);

        httpClient = new HttpClient();
        httpClient.start();

        restApiClient = new RestApiClient(httpClient, gson);
        restApiClient.setApiEndpoint("http://localhost:" + port);

        storage = new VolatileStorage<>();
    }

    @AfterEach
    public void shutdown() throws Exception {
        httpClient.stop();
    }

    @Test
    void testCredentialsError() throws IOException {
        // Setup account
        final AccountConfiguration accountConfig = new AccountConfiguration();
        accountConfig.email = "email@address.com";
        accountConfig.phone = "+4700000000";
        accountConfig.password = "password";
        when(configuration.as(AccountConfiguration.class)).thenReturn(accountConfig);

        // Setup get session response
        preparePostNetworkResponse("/session", "/mock_responses/get_session_response_error.json", 200);

        when(bridge.getConfiguration()).thenReturn(configuration);
        when(bridge.getUID()).thenReturn(new ThingUID("august:account:thinguid"));

        AugustAccountHandler accountHandler = Mockito.spy(new AugustAccountHandler(bridge, restApiClient, storage));

        // First init
        accountHandler.initialize();

        assertAuthState(AuthenticationStatus.NOT_VALIDATED);
    }

    @Test
    void testInitial2FactorLogin() throws IOException {
        // Setup account
        final AccountConfiguration accountConfig = new AccountConfiguration();
        accountConfig.email = "email@address.com";
        accountConfig.phone = "+4700000000";
        accountConfig.password = "password";
        when(configuration.as(AccountConfiguration.class)).thenReturn(accountConfig);

        // Setup get session response
        preparePostNetworkResponse("/session", "/mock_responses/get_session_response.json", 200);
        // Setup get 2 factor code response
        preparePostNetworkResponse("/validation/email", "/mock_responses/get_validation_code_response.json", 200);

        when(bridge.getConfiguration()).thenReturn(configuration);
        when(bridge.getUID()).thenReturn(new ThingUID("august:account:thinguid"));

        AugustAccountHandler accountHandler = Mockito.spy(new AugustAccountHandler(bridge, restApiClient, storage));

        // First init
        accountHandler.initialize();

        assertAuthState(AuthenticationStatus.VALIDATION_REQUESTED);

        // Setup validate 2 factor code response
        preparePostNetworkResponse("/validate/email", "/mock_responses/validate_code_response.json", 200);
        // Setup get locks response
        prepareGetNetworkResponse("/users/locks/mine", "/mock_responses/get_locks_response.json", 200);

        // Second init / TODO must check what kind of event is sent when config is updated
        accountConfig.validationCode = "000000";
        // After code has been provided
        accountHandler.initialize();

        assertAuthState(AuthenticationStatus.VALIDATED);

        Map<String, Lock> locks = accountHandler.getLocks();
        assertEquals(2, locks.size());
    }

    @Test
    void testAlreadyLoggedInValidToken() throws IOException {
        // Setup account
        final AccountConfiguration accountConfig = new AccountConfiguration();
        accountConfig.email = "email@address.com";
        accountConfig.phone = "+4700000000";
        accountConfig.password = "password";
        when(configuration.as(AccountConfiguration.class)).thenReturn(accountConfig);

        storage.put(AugustAccountHandler.STORAGE_KEY_AUTH_STATUS, AuthenticationStatus.VALIDATED.toString());
        storage.put(AugustAccountHandler.STORAGE_KEY_INSTALLID, "InstallID");
        storage.put(AugustAccountHandler.STORAGE_KEY_ACCESS_TOKEN, "ACCESSTOKEN");
        storage.put(AugustAccountHandler.STORAGE_KEY_ACCESS_TOKEN_EXPIRY,
                ZonedDateTime.now().plus(1, ChronoUnit.MONTHS).toString());
        // storage.put(AugustAccountHandler.STORAGE_KEY_USERID, "UserId");

        preparePostNetworkResponse("/session", "/mock_responses/get_session_response.json", 200);
        prepareGetNetworkResponse("/users/locks/mine", "/mock_responses/get_locks_response.json", 200);

        when(bridge.getConfiguration()).thenReturn(configuration);
        when(bridge.getUID()).thenReturn(new ThingUID("august:account:thinguid"));
        AugustAccountHandler accountHandler = new AugustAccountHandler(bridge, restApiClient, storage);

        accountHandler.initialize();

        Map<String, Lock> locks = accountHandler.getLocks();
        assertEquals(2, locks.size());
    }

    @Test
    void testAlreadyLoggedInExpiredToken() throws IOException {
        // Setup account
        final AccountConfiguration accountConfig = new AccountConfiguration();
        accountConfig.email = "email@address.com";
        accountConfig.phone = "+4700000000";
        accountConfig.password = "password";
        when(configuration.as(AccountConfiguration.class)).thenReturn(accountConfig);

        storage.put(AugustAccountHandler.STORAGE_KEY_AUTH_STATUS, AuthenticationStatus.VALIDATED.toString());
        storage.put(AugustAccountHandler.STORAGE_KEY_INSTALLID, "InstallID");
        storage.put(AugustAccountHandler.STORAGE_KEY_ACCESS_TOKEN, "ACCESSTOKEN");
        storage.put(AugustAccountHandler.STORAGE_KEY_ACCESS_TOKEN_EXPIRY,
                ZonedDateTime.now().minus(1, ChronoUnit.MONTHS).toString());
        // storage.put(AugustAccountHandler.STORAGE_KEY_USERID, "UserId");

        preparePostNetworkResponse("/session", "/mock_responses/get_session_response.json", 200);
        prepareGetNetworkResponse("/users/locks/mine", "/mock_responses/get_locks_response.json", 200);

        when(bridge.getConfiguration()).thenReturn(configuration);
        when(bridge.getUID()).thenReturn(new ThingUID("august:account:thinguid"));
        AugustAccountHandler accountHandler = new AugustAccountHandler(bridge, restApiClient, storage);

        accountHandler.initialize();

        Map<String, Lock> locks = accountHandler.getLocks();
        assertEquals(2, locks.size());
    }

    private void assertAuthState(AuthenticationStatus status) {
        assertTrue(storage.containsKey(AugustAccountHandler.STORAGE_KEY_AUTH_STATUS));
        assertEquals(storage.get(AugustAccountHandler.STORAGE_KEY_AUTH_STATUS), status.toString());
    }

    private void preparePostNetworkResponse(String urlPath, String responseResource, int responseCode)
            throws IOException {
        stubFor(post(urlEqualTo(urlPath))
                .willReturn(aResponse().withStatus(responseCode).withBody(getClasspathJSONContent(responseResource))
                        .withHeader(RestApiClient.HEADER_ACCESS_TOKEN, "ACCESSTOKEN")));
    }

    private void prepareGetNetworkResponse(String urlPath, String responseResource, int responseCode)
            throws IOException {
        stubFor(get(urlEqualTo(urlPath))
                .willReturn(aResponse().withStatus(responseCode).withBody(getClasspathJSONContent(responseResource))
                        .withHeader(RestApiClient.HEADER_ACCESS_TOKEN, "ACCESSTOKEN")));
    }

    private String getClasspathJSONContent(String path) throws IOException {
        return new String(getClass().getResourceAsStream(path).readAllBytes(), StandardCharsets.UTF_8);
    }
}
