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

import java.net.URLEncoder;
import java.nio.charset.Charset;

import com.google.gson.annotations.SerializedName;

/**
 * All classes in the .dto are data transfer classes used by the GSON mapper. This class reflects a
 * part of a request/response data structure.
 *
 * @author Arne Seime - Initial contribution.
 */

public class GetSessionRequest implements AbstractRequest {

    @SerializedName("identifier")
    public String loginId;
    public String password;
    public String installId;

    public GetSessionRequest(String loginId, String password, String installId) {
        this.loginId = "email:" + loginId;
        this.password = URLEncoder.encode(password, Charset.defaultCharset());
        this.installId = installId;
    }

    @Override
    public String getMethod() {
        return "POST";
    }

    @Override
    public String getRequestUrl() {
        return "/session";
    }
}
