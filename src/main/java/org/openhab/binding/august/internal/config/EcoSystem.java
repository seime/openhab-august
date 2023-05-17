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
package org.openhab.binding.august.internal.config;

/**
 * The {@link EcoSystem} describes which eco system the bridge should communicate with, either the legacy AUGUST or the
 * new YALE_HOME
 *
 * @author Arne Seime - Initial contribution
 */
public enum EcoSystem {
    AUGUST("https://api-production.august.com"),
    YALE_HOME("https://api.aaecosystem.com");

    private final String url;

    EcoSystem(String url) {
        this.url = url;
    }

    public String getUrl() {
        return url;
    }
}
