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
package org.openhab.binding.august.internal.comm;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.jetbrains.annotations.NotNull;
import org.openhab.binding.august.internal.config.EcoSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.pubnub.api.PNConfiguration;
import com.pubnub.api.PubNub;
import com.pubnub.api.PubNubException;
import com.pubnub.api.UserId;
import com.pubnub.api.callbacks.SubscribeCallback;
import com.pubnub.api.enums.PNLogVerbosity;
import com.pubnub.api.enums.PNReconnectionPolicy;
import com.pubnub.api.models.consumer.PNStatus;
import com.pubnub.api.models.consumer.message_actions.PNMessageAction;
import com.pubnub.api.models.consumer.objects_api.channel.PNChannelMetadataResult;
import com.pubnub.api.models.consumer.objects_api.membership.PNMembershipResult;
import com.pubnub.api.models.consumer.objects_api.uuid.PNUUIDMetadataResult;
import com.pubnub.api.models.consumer.pubsub.PNMessageResult;
import com.pubnub.api.models.consumer.pubsub.PNPresenceEventResult;
import com.pubnub.api.models.consumer.pubsub.PNSignalResult;
import com.pubnub.api.models.consumer.pubsub.files.PNFileEventResult;
import com.pubnub.api.models.consumer.pubsub.message_actions.PNMessageActionResult;

/**
 * The {@link PubNubMessageSubscriber} wrapper class around PubNub async notifications
 *
 * @author Arne Seime - Initial contribution
 */
public class PubNubMessageSubscriber {

    private static final Logger logger = LoggerFactory.getLogger(PubNubMessageSubscriber.class);

    private final Set<String> channels = new HashSet<>();

    private PubNub pub = null;

    private boolean initialized = false;

    SubscribeCallback callback = null;

    public void init(String userIdString, PubNubListener messageListener, EcoSystem ecoSystem)
            throws PubNubMessageException {

        try {
            final UserId userId = new UserId("fn-" + userIdString.toUpperCase(Locale.ROOT));
            PNConfiguration pnConfiguration = new PNConfiguration(userId);
            pnConfiguration.setSubscribeKey(ecoSystem.getPubNubSubscribeKey());
            pnConfiguration.setReconnectionPolicy(PNReconnectionPolicy.EXPONENTIAL);
            pnConfiguration.setMaximumReconnectionRetries(100);
            pnConfiguration.setLogVerbosity(PNLogVerbosity.BODY);

            pub = new PubNub(pnConfiguration);

            logger.debug("Starting PubNub subscription");

            callback = new SubscribeCallback() {

                // PubNub status
                @Override
                public void status(@NotNull PubNub pubnub, @NotNull PNStatus status) {
                    logger.debug("Event {} ", status);

                    switch (status.getOperation()) {
                        // combine unsubscribe and subscribe handling for ease of use
                        case PNSubscribeOperation:
                        case PNUnsubscribeOperation:
                            // Note: subscribe statuses never have traditional errors,
                            // just categories to represent different issues or successes
                            // that occur as part of subscribe
                            switch (status.getCategory()) {
                                case PNConnectedCategory:
                                case PNReconnectedCategory:
                                    status.getAffectedChannels().forEach(e -> messageListener.onPubNubConnect(e));
                                    break;
                                // Subscribe temporarily failed but reconnected.
                                // There is no longer any issue.
                                case PNBadRequestCategory:
                                    // case PNDisconnectedCategory:
                                case PNUnexpectedDisconnectCategory:
                                    // Usually an issue with the internet connection.
                                    // This is an error: handle appropriately.
                                    status.getAffectedChannels().forEach(e -> messageListener.onPubNubDisconnect(e));
                                    pub.reconnect();
                                    break;
                                case PNAccessDeniedCategory:
                                    // PAM does not allow this client to subscribe to this
                                    // channel and channel group configuration. This is
                                    // another explicit error.
                                    logger.warn("Permission denied subscribing to notifications. This is unexpected");
                                    break;
                                default:
                                    // You can directly specify more errors by creating
                                    // explicit cases for other error categories of
                                    // `PNStatusCategory` such as `PNTimeoutCategory` or
                                    // `PNMalformedFilterExpressionCategory` or
                                    // `PNDecryptionErrorCategory`.
                            }
                            break;

                        case PNHeartbeatOperation:
                            // Heartbeat operations can in fact have errors,
                            // so it's important to check first for an error.
                            // For more information on how to configure heartbeat notifications
                            // through the status PNObjectEventListener callback, refer to
                            // /docs/android-java/api-reference-configuration#configuration_basic_usage
                            if (status.isError()) {
                                // There was an error with the heartbeat operation, handle here
                                pub.reconnect();
                            } else {
                                // heartbeat operation was successful
                            }
                            break;
                        default: {
                            // Encountered unknown status type
                        }
                    }
                }

                // Messages
                @Override
                public void message(@NotNull PubNub pubnub, @NotNull PNMessageResult message) {
                    // Handle new message stored in message.message
                    logger.debug("Message {} ", message);
                    if (message.getChannel() != null) {
                        logger.debug("Received message on channel {}: {}", message.getChannel(), message.getMessage());
                        messageListener.onPushMessage(message.getChannel(), message.getMessage());
                    }
                }

                // Presence
                @Override
                public void presence(@NotNull PubNub pubnub, @NotNull PNPresenceEventResult presence) {
                    logger.debug("Presence Event: {}", presence.getEvent());
                    // Can be join, leave, state-change or timeout

                    logger.debug("Presence Channel: {}", presence.getChannel());
                    // The channel to which the message was published

                    logger.debug("Presence Occupancy: {}", presence.getOccupancy());
                    // Number of users subscribed to the channel

                    logger.debug("Presence State: {}", presence.getState());
                    // User state

                    logger.debug("Presence UUID: {}", presence.getUuid());
                    // UUID to which this event is related
                }

                // Signals
                @Override
                public void signal(@NotNull PubNub pubnub, @NotNull PNSignalResult signal) {
                    logger.debug("Signal publisher: {}", signal.getPublisher());
                    logger.debug("Signal payload: {}", signal.getMessage());
                    logger.debug("Signal subscription: {}", signal.getSubscription());
                    logger.debug("Signal channel: {}", signal.getChannel());
                    logger.debug("Signal timetoken: {}", signal.getTimetoken());
                }

                @Override
                public void uuid(@NotNull PubNub pubnub, @NotNull PNUUIDMetadataResult pnUUIDMetadataResult) {
                    logger.debug("UUID {}", pnUUIDMetadataResult);
                }

                @Override
                public void channel(@NotNull PubNub pubnub, @NotNull PNChannelMetadataResult pnChannelMetadataResult) {
                    logger.debug("Channel {}", pnChannelMetadataResult);
                }

                @Override
                public void membership(@NotNull PubNub pubnub, @NotNull PNMembershipResult pnMembershipResult) {
                    logger.debug("Membership {}", pnMembershipResult);
                }

                // Message actions
                @Override
                public void messageAction(@NotNull PubNub pubnub, @NotNull PNMessageActionResult pnActionResult) {
                    PNMessageAction pnMessageAction = pnActionResult.getMessageAction();
                    logger.debug("Message action type: {}", pnMessageAction.getType());
                    logger.debug("Message action value: {}", pnMessageAction.getValue());
                    logger.debug("Message action uuid: {}", pnMessageAction.getUuid());
                    logger.debug("Message action actionTimetoken: {}", pnMessageAction.getActionTimetoken());
                    logger.debug("Message action messageTimetoken: {}", pnMessageAction.getMessageTimetoken());
                    logger.debug("Message action subscription: {}", pnActionResult.getSubscription());
                    logger.debug("Message action channel: {}", pnActionResult.getChannel());
                    logger.debug("Message action timetoken: {}", pnActionResult.getTimetoken());
                }

                // Files
                @Override
                public void file(@NotNull PubNub pubnub, @NotNull PNFileEventResult pnFileEventResult) {
                    logger.debug("File channel: {}", pnFileEventResult.getChannel());
                    logger.debug("File publisher: {}", pnFileEventResult.getPublisher());
                    logger.debug("File message: {}", pnFileEventResult.getMessage());
                    logger.debug("File timetoken: {}", pnFileEventResult.getTimetoken());
                    logger.debug("File file.id: {}", pnFileEventResult.getFile().getId());
                    logger.debug("File file.name: {}", pnFileEventResult.getFile().getName());
                    logger.debug("File file.url: {}", pnFileEventResult.getFile().getUrl());
                }
            };

            pub.addListener(callback);

            initialized = true;

        } catch (PubNubException e) {
            throw new PubNubMessageException("Error creating PubNub subscription", e);
        }
    }

    public synchronized void addListener(String channelName) {

        if (!initialized) {
            logger.debug("Cannot add listener before component is initialized");
            return;
        }

        boolean added = channels.add(channelName);
        if (added) {
            logger.debug("Adding listener for channel {}", channelName);
            pub.subscribe().channels(List.of(channelName)).execute();
        } else {
            logger.warn("Duplicate listener registered for channel {} ignored", channelName);
        }
    }

    public synchronized void removeListener(String channelName) {

        if (!initialized) {
            logger.debug("Cannot remove listener before component is initialized");
            return;
        }

        boolean removed = channels.remove(channelName);
        if (removed) {
            logger.debug("Removing listener for channel {}", channelName);
            pub.unsubscribe().channels(List.of(channelName)).execute();
        } else {
            logger.warn("Listener for channel {} not found, cannot remove", channelName);
        }
    }

    public void dispose() {
        logger.debug("Disposing pubNub");
        if (initialized) {
            pub.unsubscribeAll();
            if (callback != null) {
                pub.removeListener(callback);
            }
            pub.disconnect();
            pub.destroy();
            pub.forceDestroy();
            channels.clear();
        }
    }
}
