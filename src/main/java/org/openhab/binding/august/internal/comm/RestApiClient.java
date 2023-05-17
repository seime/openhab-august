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

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.util.BytesContentProvider;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpStatus;
import org.openhab.binding.august.internal.AugustException;
import org.openhab.binding.august.internal.ConfigurationException;
import org.openhab.binding.august.internal.dto.AbstractRequest;
import org.openhab.binding.august.internal.logging.RequestLogger;
import org.openhab.core.thing.ThingUID;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * The {@link RestApiClient} is responsible for API login and communication
 *
 * @author Arne Seime - Initial contribution
 */
public class RestApiClient {
    public static final String HEADER_ACCESS_TOKEN = "x-august-access-token";
    public static final String HEADER_CONTENT_TYPE_APPLICATION_JSON = "application/json";
    private static final String API_KEY = "79fd0eb6-381d-4adf-95a0-47721289d1d9";

    private HttpClient httpClient;

    @Nullable
    private String accessToken = null;

    @Nullable
    private String apiEndpoint;

    private Gson gson;
    @Nullable
    private RequestLogger requestLogger = null;
    private AccessTokenUpdatedListener listener;

    public RestApiClient(HttpClient httpClient, Gson gson) {
        this.httpClient = httpClient;
        this.gson = gson;
    }

    public void setApiEndpoint(String apiEndpoint) {
        this.apiEndpoint = apiEndpoint;
    }

    public String getApiEndpoint() {
        return apiEndpoint;
    }

    public void init(ThingUID bridgeUid, AccessTokenUpdatedListener listener) {
        this.requestLogger = new RequestLogger(bridgeUid.getId(), gson);
        this.listener = listener;
    }

    private Request buildRequest(final AbstractRequest req) {

        Objects.requireNonNull(apiEndpoint, "API endpoint not set");

        Request request = httpClient.newRequest(apiEndpoint + req.getRequestUrl()).method(req.getMethod());

        request.getHeaders().remove(HttpHeader.USER_AGENT);
        request.getHeaders().remove(HttpHeader.ACCEPT);
        request.header(HttpHeader.USER_AGENT, "August/2019.12.16.4708 CFNetwork/1121.2.2 Darwin/19.3.0");
        request.header(HttpHeader.ACCEPT, HEADER_CONTENT_TYPE_APPLICATION_JSON);
        request.header(HttpHeader.CONTENT_TYPE, HEADER_CONTENT_TYPE_APPLICATION_JSON);
        request.header("Accept-Version", "0.0.1");
        request.header("x-kease-api-key", API_KEY);
        request.header("x-august-api-key", API_KEY);
        if (accessToken != null) {
            request.header(HEADER_ACCESS_TOKEN, accessToken);
        }

        if (!req.getMethod().contentEquals(HttpMethod.GET.asString())) { // POST, PATCH, PUT
            final String reqJson = gson.toJson(req);
            request = request.content(new BytesContentProvider(reqJson.getBytes(StandardCharsets.UTF_8)),
                    HEADER_CONTENT_TYPE_APPLICATION_JSON);
        }

        requestLogger.listenTo(request, new String[] {});

        return request;
    }

    public <T> T sendRequest(final AbstractRequest req, final Type responseType) throws AugustException {

        try {

            return sendRequestInternal(buildRequest(req), req, responseType);
        } catch (InterruptedException | TimeoutException | ExecutionException e) {
            Thread.currentThread().interrupt();
            throw new RestCommunicationException(String.format("Error sending request to server: %s", e.getMessage()),
                    e);
        }
    }

    public <T> T sendRequestInternal(final Request httpRequest, final AbstractRequest req, final Type responseType)
            throws AugustException, ExecutionException, InterruptedException, TimeoutException {

        try {
            final ContentResponse contentResponse = httpRequest.send();
            String newAccessToken = contentResponse.getHeaders().get(HEADER_ACCESS_TOKEN);
            if (accessToken == null || !accessToken.equals(newAccessToken)) {
                listener.onAccessTokenUpdated(newAccessToken);
            }
            accessToken = newAccessToken;

            final String responseJson = contentResponse.getContentAsString();
            if (contentResponse.getStatus() == HttpStatus.OK_200) {
                final JsonObject o = JsonParser.parseString(responseJson).getAsJsonObject();
                if (o.has("message")) {
                    throw new RestCommunicationException(req, o.get("message").getAsString());
                } else {
                    return gson.fromJson(responseJson, responseType);
                }
            } else if (contentResponse.getStatus() == HttpStatus.UNAUTHORIZED_401) {
                if (accessToken == null) {
                    throw new RestCommunicationException("Could not renew token");
                } else {
                    accessToken = null; // expired
                    return sendRequest(req, responseType); // Retry login + request
                }

                // See some error codes here;
                // https://github.com/snjoetw/py-august/blob/78b25da03194d68d70115f36bf926a3a6443e555/august/api_async.py#L260

            } else if (contentResponse.getStatus() == HttpStatus.FORBIDDEN_403) {
                throw new ConfigurationException("Invalid credentials");
            } else if (contentResponse.getStatus() == HttpStatus.REQUEST_TIMEOUT_408) {
                throw new RestCommunicationException(
                        "The operation timed out because the bridge (connect) failed to respond");
            } else if (contentResponse.getStatus() == HttpStatus.LOCKED_423) {
                throw new RestCommunicationException("The operation failed because the bridge (connect) is in use");
            } else if (contentResponse.getStatus() == HttpStatus.UNPROCESSABLE_ENTITY_422) {
                throw new RestCommunicationException("The operation failed because the bridge (connect) is offline.");
            } else if (contentResponse.getStatus() == HttpStatus.TOO_MANY_REQUESTS_429) {
                throw new RestCommunicationException("Too many requests, reduce polling time");
            } else {
                throw new RestCommunicationException("Error sending request to server. Server responded with "
                        + contentResponse.getStatus() + " and payload " + responseJson);
            }
        } catch (Exception e) {
            throw new RestCommunicationException(
                    String.format("Exception caught trying to communicate with API: %s", e.getMessage()), e);
        }
    }

    public String getLastAccessTokenFromHeader() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }
}
