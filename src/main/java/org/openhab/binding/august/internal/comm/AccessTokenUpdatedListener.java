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

/**
 * The {@link AccessTokenUpdatedListener} is a callback interface if the access token returned from the service changes
 * 
 * @author Arne Seime - Initial contribution
 */
public interface AccessTokenUpdatedListener {
    void onAccessTokenUpdated(String updatedAccessToken);
}
