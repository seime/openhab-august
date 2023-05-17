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
package org.openhab.binding.august.internal.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.lang.reflect.Type;
import java.time.ZonedDateTime;

import org.junit.jupiter.api.Test;

import com.google.gson.reflect.TypeToken;

/**
 * Testing class for serialization and deserialization of http json playloads
 * 
 * @author Arne Seime - Initial contribution
 */
class SerializationDeserializationTest {

    protected org.openhab.binding.august.internal.dto.WireHelper wireHelper = new org.openhab.binding.august.internal.dto.WireHelper();

    @Test
    void testGetSessionRequest() throws IOException {
        final Type type = new TypeToken<GetSessionRequest>() {
        }.getType();

        final GetSessionRequest message = wireHelper
                .deSerializeFromClasspathResource("/mock_responses/get_session_request.json", type);

        assertEquals("installId", message.installId);
        assertEquals("email:email@address.com", message.loginId);
        assertEquals("password", message.password);
    }

    @Test
    void testGetSessionResponse() throws IOException {
        final Type type = new TypeToken<GetSessionResponse>() {
        }.getType();

        final GetSessionResponse message = wireHelper
                .deSerializeFromClasspathResource("/mock_responses/get_session_response.json", type);

        assertEquals(ZonedDateTime.parse("2023-05-07T15:24:20.799Z"), message.expiresAt);
        assertEquals(true, message.hasInstallId);
    }

    @Test
    void testGetValidationCodeRequest() throws IOException {
        final Type type = new TypeToken<GetValidationCodeRequest>() {
        }.getType();

        final GetValidationCodeRequest message = wireHelper
                .deSerializeFromClasspathResource("/mock_responses/get_validation_code_request.json", type);

        assertEquals("email@address.com", message.value);
    }

    @Test
    void testGetValidationCodeResponse() throws IOException {
        final Type type = new TypeToken<GetValidationCodeResponse>() {
        }.getType();

        final GetValidationCodeResponse message = wireHelper
                .deSerializeFromClasspathResource("/mock_responses/get_validation_code_response.json", type);

        assertEquals("sent", message.code);
        assertEquals("email@address.com", message.value);
    }

    @Test
    void testValidateCodeRequest() throws IOException {
        final Type type = new TypeToken<ValidateCodeRequest>() {
        }.getType();

        final ValidateCodeRequest message = wireHelper
                .deSerializeFromClasspathResource("/mock_responses/validate_code_request.json", type);

        assertEquals("email@address.com", message.email);
        assertEquals("+4700000000", message.phone);
        assertEquals("000000", message.code);
    }

    @Test
    void testValidateCodeResponse() throws IOException {
        final Type type = new TypeToken<ValidateCodeResponse>() {
        }.getType();

        final ValidateCodeResponse message = wireHelper
                .deSerializeFromClasspathResource("/mock_responses/validate_code_response.json", type);

        assertEquals("UUID", message.userId);
        assertEquals("email:email@address.com", message.value);
        assertEquals("token_incomplete", message.resolution);
    }

    @Test
    void testGetLocksResponse() throws IOException {
        final Type type = new TypeToken<GetLocksResponse>() {
        }.getType();

        final GetLocksResponse locks = wireHelper
                .deSerializeFromClasspathResource("/mock_responses/get_locks_response.json", type);

        assertNotNull(locks);
        assertEquals(2, locks.size());

        LockDTO lock = locks.get("LockId1");
        assertEquals("LockName1", lock.lockName);
        assertEquals("superuser", lock.userType);
        assertEquals("00:00:00:00:00:00", lock.macAddress);
        assertEquals("House-UUID-1", lock.houseId);
        assertEquals("HouseName1", lock.houseName);
    }

    @Test
    void testGetLockResponse() throws IOException {
        final Type type = new TypeToken<GetLockResponse>() {
        }.getType();

        final GetLockResponse message = wireHelper
                .deSerializeFromClasspathResource("/mock_responses/get_lock_response.json", type);

        assertEquals("LockName", message.lockName);
        assertEquals("LockId1", message.lockId);
        assertEquals(7, message.type);
        assertEquals("HouseName1", message.houseName);
        assertEquals(false, message.calibrated);
        assertEquals(0.47750721243210137, message.batteryPercentage);
        assertEquals("SkuNumber", message.skuNumber);
        assertEquals("00:00:00:00:00:00", message.macAddress);
        assertEquals("SerialNumber1", message.serialNumber);
        assertEquals("3.2.1-3.3.0-2.0.8", message.currentFirmwareVersion);
        assertNotNull(message.lockStatus);

        assertEquals("locked", message.lockStatus.lockStatus);
        assertEquals(ZonedDateTime.parse("2023-01-07T15:54:31.935Z"), message.lockStatus.statusTimestamp);
        assertEquals(true, message.lockStatus.isLockStatusChanged);
        assertEquals(true, message.lockStatus.valid);
        assertEquals("closed", message.lockStatus.doorStatus);

        assertEquals(2, message.userList.loaded.size());
        UserDTO user1 = message.userList.loaded.get(0);
        assertEquals("FirstName", user1.firstName);
        assertEquals("LastName", user1.lastName);
        UserDTO user2 = message.userList.loaded.get(1);
        assertEquals("FirstName2", user2.firstName);
        assertEquals("LastName2", user2.lastName);
    }

    @Test
    void testRemoteOperateLockResponse() throws IOException {
        final Type type = new TypeToken<RemoteOperateLockResponse>() {
        }.getType();

        final RemoteOperateLockResponse message = wireHelper
                .deSerializeFromClasspathResource("/mock_responses/remoteoperate_lock_response.json", type);

        assertEquals("kAugLockState_Unlocked", message.lockState);
        assertEquals("kAugDoorState_Closed", message.doorState);
    }

    @Test
    void testLockAndDoorPushMessage() throws IOException {
        final Type type = new TypeToken<LockStatusDTO>() {
        }.getType();

        final LockStatusDTO message = wireHelper
                .deSerializeFromClasspathResource("/mock_responses/pubnub/lock_status_unlocked_async.json", type);

        assertEquals("unlocked", message.lockStatus);
        assertEquals("closed", message.doorStatus);
    }

    @Test
    void testLockPushMessage() throws IOException {
        final Type type = new TypeToken<LockStatusDTO>() {
        }.getType();

        final LockStatusDTO message = wireHelper
                .deSerializeFromClasspathResource("/mock_responses/pubnub/lock_status_no_doorstate_async.json", type);

        assertEquals("unlocked", message.lockStatus);
    }
}
