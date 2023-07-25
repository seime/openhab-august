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
package no.seime.openhab.binding.august.internal.comm;

import org.eclipse.jdt.annotation.NonNullByDefault;

import com.google.gson.JsonElement;

/**
 * The {@link PubNubListener} interface is for handling connects and disconects from PubNub
 *
 * @author Arne Seime - Initial contribution
 */
@NonNullByDefault
public interface PubNubListener {

    void onPushMessage(String channelName, JsonElement message);

    void onPubNubDisconnect(String channelName);

    void onPubNubConnect(String channelName);
}
