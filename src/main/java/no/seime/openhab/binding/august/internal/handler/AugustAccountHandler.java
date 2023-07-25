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

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.storage.Storage;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.binding.BaseBridgeHandler;
import org.openhab.core.types.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;

import no.seime.openhab.binding.august.internal.AugustException;
import no.seime.openhab.binding.august.internal.AuthenticationStatus;
import no.seime.openhab.binding.august.internal.comm.AccessTokenUpdatedListener;
import no.seime.openhab.binding.august.internal.comm.PubNubListener;
import no.seime.openhab.binding.august.internal.comm.PubNubMessageSubscriber;
import no.seime.openhab.binding.august.internal.comm.RestApiClient;
import no.seime.openhab.binding.august.internal.comm.RestCommunicationException;
import no.seime.openhab.binding.august.internal.config.AccountConfiguration;
import no.seime.openhab.binding.august.internal.config.EcoSystem;
import no.seime.openhab.binding.august.internal.dto.GetLocksRequest;
import no.seime.openhab.binding.august.internal.dto.GetLocksResponse;
import no.seime.openhab.binding.august.internal.dto.GetSessionRequest;
import no.seime.openhab.binding.august.internal.dto.GetSessionResponse;
import no.seime.openhab.binding.august.internal.dto.GetValidationCodeRequest;
import no.seime.openhab.binding.august.internal.dto.GetValidationCodeResponse;
import no.seime.openhab.binding.august.internal.dto.ValidateCodeRequest;
import no.seime.openhab.binding.august.internal.dto.ValidateCodeResponse;
import no.seime.openhab.binding.august.internal.model.Lock;

/**
 * The {@link AugustAccountHandler} is responsible for authentication
 *
 *
 * @author Arne Seime - Initial contribution
 */
@NonNullByDefault
public class AugustAccountHandler extends BaseBridgeHandler implements AccessTokenUpdatedListener, PubNubListener {
    public static final String STORAGE_KEY_AUTH_STATUS = "AUTH_STATUS";
    public static final String STORAGE_KEY_INSTALLID = "INSTALL_ID";
    public static final String STORAGE_KEY_ACCESS_TOKEN = "ACCESS_TOKEN";
    public static final String STORAGE_KEY_ACCESS_TOKEN_EXPIRY = "ACCESS_TOKEN_EXPIRY";
    public static final String STORAGE_KEY_USERID = "USERID";
    public static final String STORAGE_KEY_PREVIOUS_ECOSYSTEM = "ECO_SYSTEM";
    private final Logger logger = LoggerFactory.getLogger(AugustAccountHandler.class);
    private Optional<ScheduledFuture<?>> statusFuture = Optional.empty();
    @Nullable
    private AccountConfiguration config;
    private RestApiClient restApiClient;

    private PubNubMessageSubscriber messageSubscriber;
    private Storage<String> storage;

    private Map<String, Lock> locks = new HashMap<>();
    private Map<String, PubNubListener> eventListeners = new ConcurrentHashMap<>();

    public AugustAccountHandler(final Bridge bridge, RestApiClient restApiClient, Storage<String> storage) {
        super(bridge);
        this.restApiClient = restApiClient;
        this.storage = storage;
        this.messageSubscriber = new PubNubMessageSubscriber();
        restApiClient.init(bridge.getUID(), this);
    }

    @Override
    public void initialize() {

        logger.debug("Initializing bridge");
        // Stop any pending updates if any
        stopScheduledUpdate();

        updateStatus(ThingStatus.UNKNOWN);
        config = getConfigAs(AccountConfiguration.class);

        if ((null == config.email || null == config.phone || null == config.password)) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "Provide email address, phone number and password");
            return;
        }

        // Set urls
        @Nullable
        String previousEcoSystem = storage.get(STORAGE_KEY_PREVIOUS_ECOSYSTEM);
        if (previousEcoSystem != null && config.ecoSystem != EcoSystem.valueOf(previousEcoSystem)) {
            // Reset configuration
            clearStorage();
        }

        storage.put(STORAGE_KEY_PREVIOUS_ECOSYSTEM, config.ecoSystem.toString());

        // Set baseurl to use for communication
        if (restApiClient.getApiEndpoint() == null) {
            restApiClient.setApiEndpoint(config.ecoSystem.getUrl());
        } else {
            logger.warn("Not setting API endpoint as it was already set to {}", restApiClient.getApiEndpoint());
        }

        AuthenticationStatus status;

        @Nullable
        String authStatus = storage.get(STORAGE_KEY_AUTH_STATUS);
        if (authStatus == null) {
            status = AuthenticationStatus.NOT_VALIDATED;
            storage.put(STORAGE_KEY_AUTH_STATUS, status.toString());
        } else {
            status = AuthenticationStatus.valueOf(authStatus);
        }

        // Set accesstoken if available
        @Nullable
        String accessToken = storage.get(STORAGE_KEY_ACCESS_TOKEN);
        if (accessToken != null) {
            restApiClient.setAccessToken(accessToken);
        } else {
            logger.debug("No previous access token");
        }

        try {
            switch (status) {
                case NOT_VALIDATED:
                    // First time usage, installation must be validated
                    handleAccountNotValidated();
                    break;
                case VALIDATION_REQUESTED:
                    handleAccountValidationRequested();
                    break;
                case VALIDATED:
                    loginComplete();
                    break;
            }

        } catch (RestCommunicationException f) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, f.getMessage());
            logger.warn("Error communicating with API. Will retry in 2 minutes", f);
            statusFuture = Optional.of(scheduler.schedule(this::initialize, 2, TimeUnit.MINUTES));
        } catch (AugustException e) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "Internal error: " + e.getMessage()
                    + "\nNew 2 factor login must be done by disabling and re-enabling bridge.");
            logger.warn("Error logging in. Clearing all data, new 2 factor auth necessary", e);
            clearStorage();
        }
    }

    @Override
    public void handleConfigurationUpdate(Map<String, Object> configurationParameters) {
        super.handleConfigurationUpdate(configurationParameters);
        initialize();
    }

    private void handleAccountValidationRequested() throws AugustException {
        if (null == config.validationCode || !StringUtils.isNumeric(config.validationCode)
                || config.validationCode.length() != 6) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, String
                    .format("Verification code is not a 6 digit number: %s. Enter code again", config.validationCode));
        } else {
            logger.info("Verifying 2 factor code");
            ValidateCodeRequest validateCodeRequest = new ValidateCodeRequest(config.validationCode, config.email,
                    config.phone);
            ValidateCodeResponse validateCodeResponse = restApiClient.sendRequest(validateCodeRequest,
                    new TypeToken<ValidateCodeResponse>() {
                    }.getType());
            if ("token_incomplete".equals(validateCodeResponse.resolution)) {
                // All good
                logger.info("2 factor authentication complete");
                getThing().getConfiguration().remove("validationCode");
                storage.put(STORAGE_KEY_AUTH_STATUS, AuthenticationStatus.VALIDATED.toString());
                loginComplete();
            }
        }
    }

    private void handleAccountNotValidated() throws AugustException {
        logger.info("No initial setup, performing 2 factor authentication");
        String installationId = "openHAB-" + UUID.randomUUID();
        storage.put(STORAGE_KEY_INSTALLID, installationId);

        boolean loginOK = obtainNewSession();
        if (loginOK) {

            // Initiate 2 factor
            GetValidationCodeRequest validationCodeRequest = new GetValidationCodeRequest(config.email);
            GetValidationCodeResponse validationCodeResponse = restApiClient.sendRequest(validationCodeRequest,
                    new TypeToken<GetValidationCodeResponse>() {
                    }.getType());
            if ("sent".equals(validationCodeResponse.code)) {
                logger.info("Validation code has been sent to {}. Enter the code in the thing configuration and save",
                        validationCodeResponse.value);
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_PENDING,
                        String.format("A verification code has been sent to %s. Enter the code and save configuration",
                                validationCodeResponse.value));
                storage.put(STORAGE_KEY_AUTH_STATUS, AuthenticationStatus.VALIDATION_REQUESTED.toString());
            } else {
                logger.warn("2 factor authentication failed, code {}", validationCodeResponse.code);
                // Something went wrong, update state and reset storage
                clearStorage();
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, String
                        .format("Expected validation code to be sent, but received '%s'", validationCodeResponse.code));
            }
        } else {
            loginError();
        }
    }

    private void loginComplete() throws AugustException {
        boolean loginOK = obtainNewSession();
        if (loginOK) {
            messageSubscriber.init(storage.get(STORAGE_KEY_USERID), this, config.ecoSystem);
            doPoll();
            statusFuture = Optional.of(scheduler.scheduleWithFixedDelay(this::doPoll, config.refreshIntervalSeconds,
                    config.refreshIntervalSeconds, TimeUnit.SECONDS));
        } else {
            loginError();
        }
    }

    private void loginError() {
        updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                "Check email / phone / password / validation code");
    }

    private boolean obtainNewSession() throws AugustException {
        GetSessionRequest getSessionRequestRefresh = new GetSessionRequest(config.email, config.password,
                storage.get(STORAGE_KEY_INSTALLID));
        GetSessionResponse getSessionResponseRefresh = restApiClient.sendRequest(getSessionRequestRefresh,
                new TypeToken<GetSessionResponse>() {
                }.getType());

        if (StringUtils.trimToNull(getSessionResponseRefresh.userId) != null) {
            logger.debug("New access token obtained, expiry {}", getSessionResponseRefresh.expiresAt);

            storage.put(STORAGE_KEY_ACCESS_TOKEN, restApiClient.getLastAccessTokenFromHeader());
            storage.put(STORAGE_KEY_ACCESS_TOKEN_EXPIRY, getSessionResponseRefresh.expiresAt.toString());
            storage.put(STORAGE_KEY_USERID, getSessionResponseRefresh.userId);
            return true;
        } else {
            storage.remove(STORAGE_KEY_ACCESS_TOKEN);
            storage.remove(STORAGE_KEY_ACCESS_TOKEN_EXPIRY);
            storage.remove(STORAGE_KEY_USERID);
            return false;
        }
    }

    @Override
    public void handleCommand(final ChannelUID channelUID, final Command command) {
        // Ignore commands as none are supported
    }

    public Map<String, Lock> getLocks() {
        return locks;
    }

    private void clearStorage() {
        storage.remove(STORAGE_KEY_ACCESS_TOKEN);
        storage.remove(STORAGE_KEY_ACCESS_TOKEN_EXPIRY);
        storage.remove(STORAGE_KEY_INSTALLID);
        storage.remove(STORAGE_KEY_AUTH_STATUS);
        storage.remove(STORAGE_KEY_USERID);
        storage.remove(STORAGE_KEY_PREVIOUS_ECOSYSTEM);
    }

    @Override
    public void dispose() {
        messageSubscriber.dispose();
        stopScheduledUpdate();
        super.dispose();
    }

    public synchronized void doPoll() {

        logger.info("Polling for new account status/lock overview");
        try {
            if (isSessionExpired()) {
                boolean loginOK = obtainNewSession();
                if (!loginOK) {
                    loginError();
                    return;
                }
            }

            GetLocksRequest getLocksRequest = new GetLocksRequest();
            final GetLocksResponse getLocksResponse = restApiClient.sendRequest(getLocksRequest,
                    new TypeToken<GetLocksResponse>() {
                    }.getType());

            locks = getLocksResponse.entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, entry -> new Lock(entry.getValue())));
            updateStatus(ThingStatus.ONLINE);
            logger.info("Fetching lock overview success, found {} lock(s)", locks.size());
        } catch (final RestCommunicationException e) {
            logger.warn("Error initializing data: {}, retrying at specified refreshInterval", e.getMessage());
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                    "Error fetching data: " + e.getMessage());
        } catch (final AugustException e) {
            logger.warn("Error initializing August Lock data: {}. Not retrying", e.getMessage());
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "Error fetching data: " + e.getMessage());
            stopScheduledUpdate();
        }
    }

    private boolean isSessionExpired() {
        String expiryString = storage.get(STORAGE_KEY_ACCESS_TOKEN_EXPIRY);
        if (expiryString != null) {
            ZonedDateTime accessTokenExpiryTime = ZonedDateTime.parse(expiryString);
            logger.debug("Access token expiry time {}", expiryString);
            // Refresh token if expiry is in 7 days or less
            if (!accessTokenExpiryTime.isBefore(ZonedDateTime.now().plus(7, ChronoUnit.DAYS))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Stops this thing's polling future
     */
    private void stopScheduledUpdate() {
        statusFuture.ifPresent(future -> {
            if (!future.isCancelled()) {
                future.cancel(true);
            }
            statusFuture = Optional.empty();
        });
    }

    public RestApiClient getApiBridge() {
        return restApiClient;
    }

    @Override
    public void onAccessTokenUpdated(@Nullable String updatedAccessToken) {
        if (updatedAccessToken != null && !updatedAccessToken.isEmpty()) {
            logger.debug("Storing new access token");
            storage.put(STORAGE_KEY_ACCESS_TOKEN, updatedAccessToken);
        }
    }

    @Override
    public void onPubNubDisconnect(String channelName) {
        // Forward to lock handler
        PubNubListener pubNubListener = eventListeners.get(channelName);
        if (pubNubListener != null) {
            pubNubListener.onPubNubDisconnect(channelName);
        }
    }

    @Override
    public void onPubNubConnect(String channelName) {
        // Forward to lock handler
        PubNubListener pubNubListener = eventListeners.get(channelName);
        if (pubNubListener != null) {
            pubNubListener.onPubNubConnect(channelName);
        } else {
            logger.warn("No listener registered for channel {}", channelName);
        }
    }

    @Override
    public void onPushMessage(String channelName, JsonElement message) {
        // Find correct handler
        PubNubListener pubNubListener = eventListeners.get(channelName);
        if (pubNubListener != null) {
            pubNubListener.onPushMessage(channelName, message);
        } else {
            logger.warn("No message listener on channel {}, discarding message", channelName);
        }
    }

    public void deregisterForEvents(AugustLockHandler augustLockHandler) {
        Optional<Map.Entry<String, PubNubListener>> first = eventListeners.entrySet().stream()
                .filter(e -> e.getValue() == augustLockHandler).findFirst();
        first.ifPresent(e -> {
            String channelName = e.getKey();
            messageSubscriber.removeListener(channelName);
            eventListeners.remove(channelName);
        });
        if (first.isEmpty()) {
            logger.error("No listener found for {}", augustLockHandler);
        }
    }

    public void registerForEvents(AugustLockHandler augustLockHandler, String channelName) {
        eventListeners.computeIfAbsent(channelName, k -> {
            messageSubscriber.addListener(channelName);
            return augustLockHandler;
        });
    }
}
