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
 * The {@link LockConfiguration} class contains fields mapping thing configuration parameters.
 *
 * @author Arne Seime - Initial contribution
 */
public class LockConfiguration {
    /*
     * Lock Id
     */
    public String lockId;

    @Override
    public String toString() {
        return "LockConfiguration{" + "lockId='" + lockId + '\'' + '}';
    }
}
