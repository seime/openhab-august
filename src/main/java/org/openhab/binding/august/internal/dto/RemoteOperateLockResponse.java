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
package org.openhab.binding.august.internal.dto;

import com.google.gson.annotations.SerializedName;

/**
 * All classes in the .dto are data transfer classes used by the GSON mapper. This class reflects a
 * part of a request/response data structure.
 *
 * @author Arne Seime - Initial contribution.
 */

public class RemoteOperateLockResponse {
    @SerializedName("status")
    public String lockState;

    public String doorState;

    public Info info;

    public static class Info {
        public String lockType;
        @SerializedName("serialNumber")
        public String lockSerialNumber;

        @SerializedName("bluetoothRSSI")
        public Integer rssi;

        public Integer wlanRSSI;

        // Bridge serial?
        @SerializedName("serial")
        public String bridgeSerialNumber;
    }
}
