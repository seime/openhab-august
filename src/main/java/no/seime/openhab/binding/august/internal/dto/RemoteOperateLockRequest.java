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

import org.eclipse.jetty.http.HttpMethod;

/**
 * All classes in the .dto are data transfer classes used by the GSON mapper. This class reflects a
 * part of a request/response data structure.
 *
 * @author Arne Seime - Initial contribution.
 */
public class RemoteOperateLockRequest implements AbstractRequest {

    String lockId;

    Operation operation;

    public RemoteOperateLockRequest(String lockId, Operation operation) {
        this.lockId = lockId;
        this.operation = operation;
    }

    @Override
    public String getRequestUrl() {
        return String.format("/remoteoperate/%s/%s", lockId, operation.urlWord);
    }

    @Override
    public String getMethod() {
        return HttpMethod.PUT.asString();
    }

    public enum Operation {
        STATUS("status"),
        LOCK("lock"),
        UNLOCK("unlock");

        private final String urlWord;

        Operation(String urlWord) {
            this.urlWord = urlWord;
        }

        public String getUrlWord() {
            return urlWord;
        }
    }
}
