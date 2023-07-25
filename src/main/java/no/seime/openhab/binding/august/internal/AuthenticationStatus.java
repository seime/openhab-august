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
package no.seime.openhab.binding.august.internal;

/**
 * The {@link AuthenticationStatus} represents the state of the authentication and validation scheme (2 factor auth
 * required initially)
 *
 * @author Arne Seime - Initial contribution
 */
public enum AuthenticationStatus {
    // New installation, 2 factor auth not completed
    NOT_VALIDATED,
    // 2 factor code requested from server
    VALIDATION_REQUESTED,
    // 2 factor auth complete
    VALIDATED
}
