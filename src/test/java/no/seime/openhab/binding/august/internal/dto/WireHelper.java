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
package no.seime.openhab.binding.august.internal.dto;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;

import com.google.gson.Gson;

import no.seime.openhab.binding.august.internal.GsonFactory;

/**
 * @author Arne Seime - Initial contribution
 */
public class WireHelper {

    private final Gson gson;

    public WireHelper() {
        gson = GsonFactory.create();
    }

    public <T> T deSerializeFromClasspathResource(final String jsonClasspathName, final Type type) throws IOException {
        final String json = new String(WireHelper.class.getResourceAsStream(jsonClasspathName).readAllBytes(),
                StandardCharsets.UTF_8);
        return deSerializeFromString(json, type);
    }

    public <T> T deSerializeFromString(final String json, final Type type) {
        return gson.fromJson(json, type);
    }

    public <T> String serialize(final AbstractRequest req) {
        return gson.toJson(req);
    }
}
