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

import no.seime.openhab.binding.august.internal.AugustException;
import no.seime.openhab.binding.august.internal.dto.AbstractRequest;

/**
 * The {@link RestCommunicationException} class wraps exceptions raised when communicating with the API
 *
 * @author Arne Seime - Initial contribution
 */
@NonNullByDefault
public class RestCommunicationException extends AugustException {
    private static final long serialVersionUID = 1L;

    public RestCommunicationException(final String message, final Throwable cause) {
        super(message, cause);
    }

    public RestCommunicationException(final String message) {
        super(message);
    }

    public RestCommunicationException(final AbstractRequest req, final String overallStatus) {
        super("Server responded with error to request " + req.getClass().getSimpleName() + "/" + req.getRequestUrl()
                + ": " + overallStatus);
    }
}
