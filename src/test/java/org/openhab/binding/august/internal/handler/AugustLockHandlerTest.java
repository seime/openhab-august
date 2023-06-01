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
package org.openhab.binding.august.internal.handler;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.openhab.binding.august.internal.comm.RestApiClient.HEADER_ACCESS_TOKEN;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.eclipse.jetty.client.HttpClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openhab.binding.august.internal.BindingConstants;
import org.openhab.binding.august.internal.GsonFactory;
import org.openhab.binding.august.internal.comm.PubNubListener;
import org.openhab.binding.august.internal.comm.PubNubMessageSubscriber;
import org.openhab.binding.august.internal.comm.RestApiClient;
import org.openhab.binding.august.internal.config.EcoSystem;
import org.openhab.binding.august.internal.config.LockConfiguration;
import org.openhab.binding.august.internal.dto.RemoteOperateLockRequest;
import org.openhab.core.config.core.Configuration;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.OpenClosedType;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.library.unit.Units;
import org.openhab.core.test.storage.VolatileStorage;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingUID;
import org.openhab.core.thing.binding.ThingHandlerCallback;
import org.openhab.core.thing.binding.builder.ChannelBuilder;
import org.openhab.core.thing.internal.ThingImpl;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

/**
 *
 * @author Arne Seime - Initial contribution
 */

@ExtendWith(MockitoExtension.class)
class AugustLockHandlerTest implements PubNubListener {

    private WireMockServer wireMockServer;

    private HttpClient httpClient;

    private @Mock Configuration configuration;
    private @Mock Bridge bridge;

    private @Mock AugustAccountHandler accountHandler;

    private RestApiClient restApiClient;
    private PubNubMessageSubscriber messageSubscriber;
    private VolatileStorage<Object> storage;

    private Gson gson = GsonFactory.create();

    private Thing thing;

    private AugustLockHandler lockHandler;

    private ThingHandlerCallback thingHandlerCallback;

    LockConfiguration lockConfiguration;

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
        restApiClient.init(new ThingUID("august:bridge:1"), updatedAccessToken -> {
        });

        restApiClient.setAccessToken("ACCESSTOKEN");

        messageSubscriber = new PubNubMessageSubscriber();
        messageSubscriber.init("null", this, EcoSystem.AUGUST);

        lockConfiguration = new LockConfiguration();
        lockConfiguration.lockId = "LockId1";
        when(configuration.as(LockConfiguration.class)).thenReturn(lockConfiguration);

        thing = createLockThing();
        lockHandler = Mockito.spy(new AugustLockHandler(thing, gson));
        thingHandlerCallback = Mockito.mock(ThingHandlerCallback.class);
        lockHandler.setCallback(thingHandlerCallback);

        when(bridge.getStatus()).thenReturn(ThingStatus.ONLINE);
        when(lockHandler.getBridge()).thenReturn(bridge);
        when(bridge.getHandler()).thenReturn(accountHandler);
        when(accountHandler.getApiBridge()).thenReturn(restApiClient);

        storage = new VolatileStorage<>();
    }

    @AfterEach
    public void shutdown() throws Exception {
        httpClient.stop();
        lockHandler.dispose();
        verify(accountHandler).deregisterForEvents(eq(lockHandler));
    }

    @Test
    void testInitialize() throws IOException, InterruptedException {

        // Setup get lock response
        prepareGetNetworkResponse("/locks/" + lockConfiguration.lockId, "/mock_responses/get_lock_response.json", 200);

        lockHandler.initialize();

        Thread.sleep(2000);

        verify(thingHandlerCallback).stateUpdated(new ChannelUID(thing.getUID(), BindingConstants.CHANNEL_BATTERY),
                new QuantityType<>(47.75072124321014, Units.PERCENT));
        verify(thingHandlerCallback).stateUpdated(new ChannelUID(thing.getUID(), BindingConstants.CHANNEL_LOCK_STATE),
                OnOffType.ON);
        verify(thingHandlerCallback).stateUpdated(new ChannelUID(thing.getUID(), BindingConstants.CHANNEL_DOOR_STATE),
                OpenClosedType.CLOSED);

        lockHandler.onPubNubConnect("ignored");

        verify(accountHandler).registerForEvents(eq(lockHandler), eq("PubsubChannelUUID"));
    }

    @Test
    void testUnlockDoor() throws IOException, InterruptedException {
        // Setup account

        // Setup get lock response
        prepareGetNetworkResponse("/locks/" + lockConfiguration.lockId, "/mock_responses/get_lock_response.json", 200);

        lockHandler.initialize();

        Thread.sleep(2000);

        // await().until(() -> lockHandler.getThing().getStatus() == ThingStatus.ONLINE);

        verify(thingHandlerCallback).stateUpdated(new ChannelUID(thing.getUID(), BindingConstants.CHANNEL_BATTERY),
                new QuantityType<>(47.75072124321014, Units.PERCENT));
        verify(thingHandlerCallback).stateUpdated(new ChannelUID(thing.getUID(), BindingConstants.CHANNEL_LOCK_STATE),
                OnOffType.ON);
        verify(thingHandlerCallback).stateUpdated(new ChannelUID(thing.getUID(), BindingConstants.CHANNEL_DOOR_STATE),
                OpenClosedType.CLOSED);

        lockHandler.onPubNubConnect("ignored");

        verify(accountHandler).registerForEvents(eq(lockHandler), eq("PubsubChannelUUID"));

        preparePutNetworkResponse(
                String.format("/remoteoperate/%s/%s", lockConfiguration.lockId,
                        RemoteOperateLockRequest.Operation.UNLOCK.getUrlWord()),
                "/mock_responses/remoteoperate_lock_response.json", 200);

        lockHandler.handleCommand(new ChannelUID(thing.getUID(), BindingConstants.CHANNEL_LOCK_STATE), OnOffType.OFF);

        lockHandler.onPushMessage("ignored", JsonParser
                .parseString(getClasspathJSONContent("/mock_responses/pubnub/lock_status_unlocked_async.json")));
        verify(thingHandlerCallback).stateUpdated(new ChannelUID(thing.getUID(), BindingConstants.CHANNEL_LOCK_STATE),
                OnOffType.OFF);
    }

    @Test
    void testAsyncCallback() throws IOException, InterruptedException {
        prepareGetNetworkResponse("/locks/" + lockConfiguration.lockId, "/mock_responses/get_lock_response.json", 200);

        String knownUser = "Specific User";
        String manualUser = "Manual";

        lockHandler.initialize();
        lockHandler.addUser("knownUserID", knownUser);

        Thread.sleep(2000);

        reset(thingHandlerCallback);

        // Remote lock
        lockHandler.onPushMessage("ignored",
                JsonParser.parseString(getClasspathJSONContent("/mock_responses/pubnub/1_lock_remote.json")));
        verifyChannelUpdates(OnOffType.ON, OpenClosedType.CLOSED, null);

        // Manual unlock, door closed
        Mockito.reset(thingHandlerCallback);
        lockHandler.onPushMessage("ignored", JsonParser
                .parseString(getClasspathJSONContent("/mock_responses/pubnub/2_manual_unlock_door_closed.json")));
        Thread.sleep(3000);
        verifyChannelUpdates(OnOffType.OFF, OpenClosedType.CLOSED, manualUser);

        // Manual lock, door closed
        Mockito.reset(thingHandlerCallback);
        lockHandler.onPushMessage("ignored",
                JsonParser.parseString(getClasspathJSONContent("/mock_responses/pubnub/3_manual_lock.json")));
        verifyChannelUpdates(OnOffType.ON, OpenClosedType.CLOSED, null);

        // Manual unlock, door closed
        Mockito.reset(thingHandlerCallback);
        lockHandler.onPushMessage("ignored",
                JsonParser.parseString(getClasspathJSONContent("/mock_responses/pubnub/4_manual_unlock.json")));
        Thread.sleep(3000);
        verifyChannelUpdates(OnOffType.OFF, OpenClosedType.CLOSED, manualUser);

        // Door opened
        Mockito.reset(thingHandlerCallback);
        lockHandler.onPushMessage("ignored",
                JsonParser.parseString(getClasspathJSONContent("/mock_responses/pubnub/5_door_open.json")));
        verifyChannelUpdates(OnOffType.OFF, OpenClosedType.OPEN, null);

        // Door closed
        Mockito.reset(thingHandlerCallback);
        lockHandler.onPushMessage("ignored",
                JsonParser.parseString(getClasspathJSONContent("/mock_responses/pubnub/6_door_closed.json")));
        verifyChannelUpdates(OnOffType.OFF, OpenClosedType.CLOSED, null);

        // Manual lock outside
        Mockito.reset(thingHandlerCallback);
        lockHandler.onPushMessage("ignored",
                JsonParser.parseString(getClasspathJSONContent("/mock_responses/pubnub/7_manual_lock.json")));
        verifyChannelUpdates(OnOffType.ON, OpenClosedType.CLOSED, null);

        // Pin code unlock outside (sent with manualuser apparently)
        Mockito.reset(thingHandlerCallback);
        lockHandler.onPushMessage("ignored", JsonParser
                .parseString(getClasspathJSONContent("/mock_responses/pubnub/8_pin_unlock_door_closed.json")));
        verifyChannelUpdates(OnOffType.OFF, OpenClosedType.CLOSED, null);

        // Pin code unlock outside - now with username
        Mockito.reset(thingHandlerCallback);
        lockHandler.onPushMessage("ignored", JsonParser.parseString(
                getClasspathJSONContent("/mock_responses/pubnub/9_pin_unlock_door_closed_with_username.json")));
        verifyChannelUpdates(OnOffType.OFF, null, knownUser);

        // Door opened
        Mockito.reset(thingHandlerCallback);
        lockHandler.onPushMessage("ignored",
                JsonParser.parseString(getClasspathJSONContent("/mock_responses/pubnub/10_door_open.json")));
        verifyChannelUpdates(OnOffType.OFF, OpenClosedType.OPEN, null);

        // Door closed
        Mockito.reset(thingHandlerCallback);
        lockHandler.onPushMessage("ignored",
                JsonParser.parseString(getClasspathJSONContent("/mock_responses/pubnub/11_door_closed.json")));
        verifyChannelUpdates(OnOffType.OFF, OpenClosedType.CLOSED, null);

        // Door locked
        Mockito.reset(thingHandlerCallback);
        lockHandler.onPushMessage("ignored",
                JsonParser.parseString(getClasspathJSONContent("/mock_responses/pubnub/12_auto_lock.json")));
        verifyChannelUpdates(OnOffType.ON, OpenClosedType.CLOSED, null);
    }

    private void verifyChannelUpdates(OnOffType lockState, OpenClosedType doorState, String unlockedByUser) {
        verify(thingHandlerCallback).stateUpdated(new ChannelUID(thing.getUID(), BindingConstants.CHANNEL_LOCK_STATE),
                lockState);
        if (doorState == null) {
            verify(thingHandlerCallback, never())
                    .stateUpdated(eq(new ChannelUID(thing.getUID(), BindingConstants.CHANNEL_DOOR_STATE)), any());

        } else {
            verify(thingHandlerCallback)
                    .stateUpdated(new ChannelUID(thing.getUID(), BindingConstants.CHANNEL_DOOR_STATE), doorState);
        }
        if (unlockedByUser == null) {
            verify(thingHandlerCallback, never())
                    .stateUpdated(eq(new ChannelUID(thing.getUID(), BindingConstants.CHANNEL_UNLOCKED_BY_USER)), any());
        } else {
            verify(thingHandlerCallback).stateUpdated(
                    new ChannelUID(thing.getUID(), BindingConstants.CHANNEL_UNLOCKED_BY_USER),
                    new StringType(unlockedByUser));
        }
    }

    private ThingImpl createLockThing() {
        ThingImpl lockThing = new ThingImpl(BindingConstants.THING_TYPE_LOCK, "LockId1");
        lockThing.addChannel(
                ChannelBuilder.create(new ChannelUID(lockThing.getUID(), BindingConstants.CHANNEL_LOCK_STATE)).build());
        lockThing.addChannel(
                ChannelBuilder.create(new ChannelUID(lockThing.getUID(), BindingConstants.CHANNEL_DOOR_STATE)).build());
        lockThing.addChannel(
                ChannelBuilder.create(new ChannelUID(lockThing.getUID(), BindingConstants.CHANNEL_BATTERY)).build());
        lockThing.addChannel(ChannelBuilder
                .create(new ChannelUID(lockThing.getUID(), BindingConstants.CHANNEL_UNLOCKED_BY_USER)).build());
        lockThing.setConfiguration(configuration);
        return lockThing;
    }

    private void preparePutNetworkResponse(String urlPath, String responseResource, int responseCode)
            throws IOException {
        stubFor(put(urlEqualTo(urlPath)).willReturn(aResponse().withStatus(responseCode)
                .withBody(getClasspathJSONContent(responseResource)).withHeader(HEADER_ACCESS_TOKEN, "ACCESSTOKEN")));
    }

    private void prepareGetNetworkResponse(String urlPath, String responseResource, int responseCode)
            throws IOException {
        stubFor(get(urlEqualTo(urlPath)).willReturn(aResponse().withStatus(responseCode)
                .withBody(getClasspathJSONContent(responseResource)).withHeader(HEADER_ACCESS_TOKEN, "ACCESSTOKEN")));
    }

    private String getClasspathJSONContent(String path) throws IOException {
        return new String(getClass().getResourceAsStream(path).readAllBytes(), StandardCharsets.UTF_8);
    }

    @Override
    public void onPushMessage(String channelName, JsonElement message) {
    }

    @Override
    public void onPubNubDisconnect(String channelName) {
    }

    @Override
    public void onPubNubConnect(String channelName) {
    }
}
