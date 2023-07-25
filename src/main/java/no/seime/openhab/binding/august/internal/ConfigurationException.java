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

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * The {@link ConfigurationException} class wraps exceptions raised when due to configuration errors
 *
 * @author Arne Seime - Initial contribution
 */
@NonNullByDefault
public class ConfigurationException extends AugustException {
    private static final long serialVersionUID = 1L;

    public ConfigurationException(final String message) {
        super(message);
    }
}
