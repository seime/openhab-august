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

import org.openhab.binding.august.internal.AugustException;

/**
 * The {@link PubNubMessageException} class wraps exceptions raised when communicating with the PubNub async
 * message api
 *
 * @author Arne Seime - Initial contribution
 */
public class PubNubMessageException extends AugustException {
    public PubNubMessageException(String message, Throwable cause) {
        super(message, cause);
    }
}
