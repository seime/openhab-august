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

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.OpenClosedType;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.library.unit.Units;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
import org.openhab.core.types.State;
import org.openhab.core.types.UnDefType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;

import no.seime.openhab.binding.august.internal.AugustException;
import no.seime.openhab.binding.august.internal.BindingConstants;
import no.seime.openhab.binding.august.internal.comm.PubNubListener;
import no.seime.openhab.binding.august.internal.comm.RestApiClient;
import no.seime.openhab.binding.august.internal.config.LockConfiguration;
import no.seime.openhab.binding.august.internal.dto.AsyncLockStatusDTO;
import no.seime.openhab.binding.august.internal.dto.GetLockRequest;
import no.seime.openhab.binding.august.internal.dto.GetLockResponse;
import no.seime.openhab.binding.august.internal.dto.RemoteOperateLockRequest;
import no.seime.openhab.binding.august.internal.dto.RemoteOperateLockResponse;

/**
 * The {@link AugustLockHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Arne Seime - Initial contribution
 */
public class AugustLockHandler extends BaseThingHandler implements PubNubListener {

    public static final String ERROR_MESSAGE_UNSUPPORTED_COMMAND = "Unsupported command {} for channel {}";
    public static final int LOCK_POLLING_SECONDS = 1800;

    private final Logger logger = LoggerFactory.getLogger(AugustLockHandler.class);

    private LockConfiguration config;

    private RestApiClient restApiClient;
    private Gson gson;

    @Nullable
    private AugustAccountHandler handler;

    public AugustLockHandler(Thing thing, Gson gson) {
        super(thing);
        this.gson = gson;

        addKnownLockType("1002", "Yale Doorman V2N");
        addKnownLockType("7", "Yale Doorman L3");
        addKnownLockType("12", "Yale Unity Security Screen");
        addKnownLockType("6", "Yale Linus Smart Lock");
        lockTypeToLockName.put("", "(Not reported)");
    }

    private void addKnownLockType(String lockType, String lockName) {
        lockTypeToLockName.put(lockType, String.format("%s (Type %s)", lockName, lockType));
    }

    private String getLockName(String lockType) {
        String lockName = lockTypeToLockName.get(lockType);
        if (lockName == null) {
            lockName = lockType;
        }

        return lockName;
    }

    private GetLockResponse lock;

    private static final Map<String, String> lockTypeToLockName = new ConcurrentHashMap<>();

    // Map of userIds to human-readable names.
    private Map<String, String> userIdToName = new HashMap<>();

    private Optional<ScheduledFuture<?>> statusFuture = Optional.empty();

    @Override
    public void initialize() {
        updateStatus(ThingStatus.UNKNOWN);
        config = getConfigAs(LockConfiguration.class);

        logger.info("{} Initializing lock", config.lockId);
        stopScheduledUpdate(); // If any

        // Workaround for testing - need to inject RestApiClient but having difficulties injecting the bridge handler in
        // the
        // tests
        Bridge bridge = getBridge();
        if (bridge != null) {
            AugustAccountHandler accountHandler = (AugustAccountHandler) bridge.getHandler();
            if (accountHandler != null) {
                restApiClient = accountHandler.getApiBridge();
                this.handler = accountHandler;
            }
        }

        Objects.requireNonNull(restApiClient,
                "RestApiClient is null - must be set either directly in constructor or fetched via getBridge().getHandler()");
        statusFuture = Optional.of(scheduler.schedule(this::doPoll, 1, TimeUnit.SECONDS));
        logger.info("{} Lock init successful", config.lockId);
    }

    @Override
    public void dispose() {
        handler.deregisterForEvents(this);

        stopScheduledUpdate();
        super.dispose();
    }

    public void doPoll() {

        Bridge bridge = getBridge();

        if (bridge != null && bridge.getStatus() != ThingStatus.ONLINE) {
            logger.warn("{} Not polling lock since bridge isn't online yet. Bridge reported status {}", config.lockId,
                    getBridge().getStatus());
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE);
            // Schedule reconnect retry every 5 seconds
            statusFuture = Optional.of(scheduler.schedule(this::doPoll, 5, TimeUnit.SECONDS));

            return;
        }

        logger.info("{} Polling for updated lock status", config.lockId);
        try {
            final GetLockRequest getLockRequest = new GetLockRequest(config.lockId);

            lock = restApiClient.sendRequest(getLockRequest, new TypeToken<GetLockResponse>() {
            }.getType());

            parseUserMap(lock);
            updateThingProperties(lock);

            thing.getChannels().forEach(e -> handleCommandInternal(e.getUID(), null));
            // Register listener
            handler.registerForEvents(this, lock.pubsubChannel);
        } catch (AugustException ex) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                    "Error retrieving data from server: " + ex.getMessage());
            // Undef all channels if error
            thing.getChannels().forEach(e -> updateState(e.getUID(), UnDefType.UNDEF));
        }
        // Do poll to catch up with any message not received via PubNub
        statusFuture = Optional.of(scheduler.schedule(this::doPoll, LOCK_POLLING_SECONDS, TimeUnit.SECONDS));
    }

    private void updateThingProperties(GetLockResponse lockResponse) {
        Map<String, String> properties = editProperties();
        properties.put("macAddress", lockResponse.macAddress);
        properties.put("firmwareVersion", lockResponse.currentFirmwareVersion);
        properties.put("skuNumber", lockResponse.skuNumber);
        properties.put("lockName", lockResponse.lockName);
        properties.put("houseName", lockResponse.houseName);
        properties.put("lockSerialNumber", lockResponse.serialNumber);
        properties.put("lockType", getLockName("" + lockResponse.type));

        updateThing(editThing().withProperties(properties).build());
    }

    private void updateThingProperties(RemoteOperateLockResponse.Info info) {
        Map<String, String> properties = editProperties();
        if (info.bridgeSerialNumber != null) {
            properties.put("bridgeSerialNumber", info.bridgeSerialNumber);
        }
        updateProperties(properties);
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

    @Override
    public void handleCommand(final ChannelUID channelUID, final @Nullable Command command) {
        handleCommandInternal(channelUID, command);
    }

    private void handleCommandInternal(final ChannelUID channelUID, final Command command) {
        switch (channelUID.getId()) {
            case BindingConstants.CHANNEL_BATTERY:
                handleBatteryCommand(channelUID, command);
                break;
            case BindingConstants.CHANNEL_LOCK_STATE:
                handleLockStateCommand(channelUID, command);
                break;
            case BindingConstants.CHANNEL_DOOR_STATE:
                handleDoorStateCommand(channelUID, command);
                break;
            case BindingConstants.CHANNEL_UNLOCKED_BY_USER:
                // No support for RefreshType
                break;
            default:
                logger.debug("{} Received command on unknown channel {}, ignoring", config.lockId, channelUID.getId());
        }
    }

    private void handleDoorStateCommand(ChannelUID channelUID, Command command) {
        if (command == null || command instanceof RefreshType) {
            if (lock.lockStatus.doorStatus != null) {
                logger.info("{} Updating door state channel with cloud state", config.lockId);
                updateState(channelUID, parseDoorState(lock.lockStatus.doorStatus));
            } else {
                updateState(channelUID, UnDefType.UNDEF);
            }
        } else {
            logger.debug(ERROR_MESSAGE_UNSUPPORTED_COMMAND, command, channelUID);
        }
    }

    private void handleLockStateCommand(ChannelUID channelUID, Command command) {
        if (command == null) {
            logger.info("{} Updating lock state channel with cloud state", config.lockId);
            updateState(channelUID, parseLockState(lock.lockStatus.lockStatus));
        } else if (command instanceof OnOffType || command instanceof RefreshType) {
            try {
                logger.info("{} Querying lock/performing operation for lock state", config.lockId);
                final RemoteOperateLockRequest operateLockRequest = new RemoteOperateLockRequest(config.lockId,
                        getOperationFromCommand(command));
                restApiClient.sendRequest(operateLockRequest, new TypeToken<RemoteOperateLockResponse>() {
                }.getType());
            } catch (AugustException e) {
                logger.warn("{} Error contacting lock", config.lockId, e);
                updateState(channelUID, UnDefType.UNDEF);
            }

        } else {
            logger.debug(ERROR_MESSAGE_UNSUPPORTED_COMMAND, command, channelUID);
        }
    }

    private void handleBatteryCommand(ChannelUID channelUID, Command command) {
        if (command instanceof RefreshType || command == null) {
            updateState(channelUID, new QuantityType<>(lock.batteryPercentage * 100, Units.PERCENT));
        } else {
            logger.debug(ERROR_MESSAGE_UNSUPPORTED_COMMAND, command, channelUID);
        }
    }

    private RemoteOperateLockRequest.Operation getOperationFromCommand(Command command) {
        if (command instanceof RefreshType) {
            return RemoteOperateLockRequest.Operation.STATUS;
        } else if (command instanceof OnOffType) {
            return command == OnOffType.ON ? RemoteOperateLockRequest.Operation.LOCK
                    : RemoteOperateLockRequest.Operation.UNLOCK;
        } else {
            return null;
        }
    }

    @Override
    public void onPubNubConnect(String channelName) {
        logger.info("{} PubNub connected", lock.lockId);
        updateStatus(ThingStatus.ONLINE);
    }

    @Override
    public void onPubNubDisconnect(String channelName) {
        logger.info("{} PubNub disconnected", lock.lockId);
        stopScheduledUpdate();
        updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, "Server push message connection lost");
    }

    @Override
    public void onPushMessage(String channelName, JsonElement message) {

        try {
            logger.info("{} Received pubsub message {}", lock.lockId, gson.toJson(message));
            JsonElement eventType = message.getAsJsonObject().get("remoteEvent");
            if (eventType != null) {
                handleRemoteEventPushMessage(message, eventType);
            } else {

                if (message.getAsJsonObject().has("bridgeID")) {
                    logger.debug("Skipping bridge status push message");
                } else {

                    handleSimplePushMessage(message);
                }
            }
        } catch (

        Exception e) {
            logger.error("Error handling pubnub message on channel {}: {}", channelName, message, e);
        }
    }

    ScheduledFuture<?> unlockCommandStatus = null;

    State previousLockState = UnDefType.UNDEF;

    State previousDoorState = UnDefType.UNDEF;

    private void handleSimplePushMessage(JsonElement message) {
        AsyncLockStatusDTO asyncStatus = gson.fromJson(message, new TypeToken<AsyncLockStatusDTO>() {
        }.getType());

        if (asyncStatus.lockState != null) {
            State newLockState = parseLockState(asyncStatus.lockState);
            updateState(BindingConstants.CHANNEL_LOCK_STATE, newLockState);

            State newDoorState = parseDoorState(asyncStatus.doorState);
            if ((!previousDoorState.equals(newDoorState) && newDoorState != UnDefType.UNDEF)
                    && newLockState.equals(previousLockState)) {
                logger.debug("Ignoring only door state changed");
            } else if (asyncStatus.callingUserID != null && newLockState == OnOffType.OFF
                    && (newDoorState == OpenClosedType.CLOSED || newDoorState == UnDefType.UNDEF)) {
                // Lock unlocked, cancel any future
                cancelUnlockedByUserFuture();
                if ("manualunlock".equals(asyncStatus.callingUserID) && previousLockState == OnOffType.ON) {
                    logger.debug("Delaying unlock status for manual user in case a real username follows");
                    unlockCommandStatus = scheduler.schedule(() -> {
                        logger.debug("Updating unlock channel with manual user");
                        updateState(BindingConstants.CHANNEL_UNLOCKED_BY_USER,
                                translateUserIdToName(asyncStatus.callingUserID));
                    }, 2, TimeUnit.SECONDS);
                } else {
                    logger.info("Updating unlock channel with current user {}", asyncStatus.callingUserID);
                    updateState(BindingConstants.CHANNEL_UNLOCKED_BY_USER,
                            translateUserIdToName(asyncStatus.callingUserID));
                }

            } else if (newLockState == OnOffType.ON) {
                cancelUnlockedByUserFuture();
            }
            previousLockState = newLockState;
        }

        if (asyncStatus.doorState != null) {
            State updatedDoorState = parseDoorState(asyncStatus.doorState);
            updateState(BindingConstants.CHANNEL_DOOR_STATE, updatedDoorState);
            previousDoorState = updatedDoorState;
        }
    }

    private void cancelUnlockedByUserFuture() {
        if (unlockCommandStatus != null && !unlockCommandStatus.isCancelled()) {
            logger.debug("Cancelling lock update future");
            unlockCommandStatus.cancel(false);
            unlockCommandStatus = null;
        }
    }

    private void handleRemoteEventPushMessage(JsonElement message, JsonElement eventType) {
        switch (eventType.getAsInt()) {
            case 1:
                if (message.getAsJsonObject().get("error") != null) {
                    logger.debug("Ignoring error message from bridge: {}", message.getAsJsonObject().get("error"));
                } else {

                    RemoteOperateLockResponse remoteEvent = gson.fromJson(message,
                            new TypeToken<RemoteOperateLockResponse>() {
                            }.getType());
                    if (remoteEvent.info != null) {
                        updateThingProperties(remoteEvent.info);
                    }

                    if (remoteEvent.lockState != null && !"kAugLockState_Unlocking".equals(remoteEvent.lockState)
                            && !"kAugLockState_Locking".equals(remoteEvent.lockState)) {
                        State lockState = parseLockState(remoteEvent.lockState);
                        updateState(BindingConstants.CHANNEL_LOCK_STATE, lockState);

                    }
                    if (remoteEvent.doorState != null) {
                        updateState(BindingConstants.CHANNEL_DOOR_STATE, parseDoorState(remoteEvent.doorState));
                    }
                }
                break;
            // Other events may occur
            default:
                logger.info("Unhandled EVENT of type {}", eventType);
        }
    }

    private State parseLockState(String stateString) {
        State state = UnDefType.UNDEF;
        switch (stateString) {
            case "locked":
            case "kAugLockState_Locked":
                state = OnOffType.ON;
                break;
            case "unlatched":
            case "unlocked":
            case "kAugLockState_Unlocked":
                state = OnOffType.OFF;
                break;
            case "unknown":
                state = UnDefType.UNDEF;
                break;
            default:
                logger.warn("Unexpected lockState '{}' returned, setting to UNDEF", stateString);

        }
        return state;
    }

    private State parseDoorState(String stateString) {
        State state = UnDefType.UNDEF;
        if (stateString != null) {
            switch (stateString) {
                case "open":
                case "kAugDoorState_Open":
                case "kAugLockDoorState_Open":
                    state = OpenClosedType.OPEN;
                    break;
                case "closed":
                case "kAugDoorState_Closed":
                case "kAugLockDoorState_Closed":
                    state = OpenClosedType.CLOSED;
                    break;
                case "unknown":
                    state = UnDefType.UNDEF;
                    break;
                default:
                    logger.warn("Unexpected doorState '{}' returned, setting to UNDEF", stateString);
            }
        }
        return state;
    }

    private State translateUserIdToName(String callingUserID) {

        if (callingUserID == null) {
            return UnDefType.UNDEF;
        } else if ("manuallock".equals(callingUserID) || "manualunlock".equals(callingUserID)) {
            return new StringType("Manual");
        } else {
            String user = userIdToName.get(callingUserID);
            if (user != null) {
                return new StringType(user);
            } else {
                return UnDefType.UNDEF;
            }
        }
    }

    private void parseUserMap(GetLockResponse lock) {
        if (lock.userList != null) {
            lock.userList.loaded.forEach(
                    user -> userIdToName.put(user.userID, String.format("%s %s", user.firstName, user.lastName)));
        }
    }

    @Override
    public String toString() {
        return "AugustLockHandler{" + "config=" + config + '}';
    }

    /**
     * Only used for testing, need to make public for mocking purposes
     *
     * @return
     */
    @Override
    public @Nullable Bridge getBridge() {
        return super.getBridge();
    }

    void addUser(String userId, String name) {
        userIdToName.put(userId, name);
    }
}
