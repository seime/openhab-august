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
package no.seime.openhab.binding.august.internal.config;

/**
 * The {@link EcoSystem} describes which eco system the bridge should communicate with, either the legacy AUGUST or the
 * new YALE_HOME
 *
 * @author Arne Seime - Initial contribution
 */
public enum EcoSystem {
    AUGUST("https://api-production.august.com", "sub-c-1030e062-0ebe-11e5-a5c2-0619f8945a4f",
            "pub-c-567d7f2d-270a-438a-a785-f0af12ad8312"),
    YALE_HOME("https://api.aaecosystem.com", "sub-c-c9c38d4d-5796-46c9-9262-af20cf6a1d42",
            "pub-c-353e8881-cf58-4b26-9baf-96f296de0677");

    private final String url;

    private final String pubNubSubscribeKey;

    private final String pubNubPublishKey;

    EcoSystem(String url, String pubNubSubscribeKey, String pubNubPublishKey) {
        this.url = url;
        this.pubNubSubscribeKey = pubNubSubscribeKey;
        this.pubNubPublishKey = pubNubPublishKey;
    }

    public String getUrl() {
        return url;
    }

    public String getPubNubSubscribeKey() {
        return pubNubSubscribeKey;
    }

    public String getPubNubPublishKey() {
        return pubNubPublishKey;
    }
}
