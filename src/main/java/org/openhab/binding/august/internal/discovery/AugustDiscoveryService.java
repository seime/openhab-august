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
package org.openhab.binding.august.internal.discovery;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.august.internal.BindingConstants;
import org.openhab.binding.august.internal.handler.AugustAccountHandler;
import org.openhab.core.config.discovery.AbstractDiscoveryService;
import org.openhab.core.config.discovery.DiscoveryResult;
import org.openhab.core.config.discovery.DiscoveryResultBuilder;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.ThingUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Arne Seime - Initial contribution
 */
@NonNullByDefault
public class AugustDiscoveryService extends AbstractDiscoveryService {
    public static final Set<ThingTypeUID> DISCOVERABLE_THING_TYPES_UIDS = Collections
            .singleton(BindingConstants.THING_TYPE_LOCK);
    private static final long DISCOVERY_INTERVAL_MINUTES = 60 * 4; // EVERY 4 HOUR
    public static final String LOCK_ID_PROPERTY = "lockId";
    private final Logger logger = LoggerFactory.getLogger(AugustDiscoveryService.class);
    private final AugustAccountHandler accountHandler;
    private Optional<ScheduledFuture<?>> discoveryJob = Optional.empty();

    public AugustDiscoveryService(final AugustAccountHandler accountHandler) {
        super(DISCOVERABLE_THING_TYPES_UIDS, 10);
        this.accountHandler = accountHandler;
    }

    @Override
    protected void startBackgroundDiscovery() {
        discoveryJob = Optional
                .of(scheduler.scheduleWithFixedDelay(this::startScan, 0, DISCOVERY_INTERVAL_MINUTES, TimeUnit.MINUTES));
    }

    @Override
    protected void startScan() {
        logger.debug("Start scan for August locks");
        synchronized (this) {
            removeOlderResults(getTimestampOfLastScan(), null, accountHandler.getThing().getUID());
            final ThingUID accountUID = accountHandler.getThing().getUID();
            accountHandler.doPoll();

            accountHandler.getLocks().entrySet().forEach(e -> {
                ThingTypeUID thingType = BindingConstants.THING_TYPE_LOCK;

                final ThingUID deviceUID = new ThingUID(thingType, accountUID, e.getKey());

                final DiscoveryResult discoveryResult = DiscoveryResultBuilder.create(deviceUID).withBridge(accountUID)
                        .withProperty(LOCK_ID_PROPERTY, e.getKey())
                        .withLabel(e.getValue().getHouseName() + " / " + e.getValue().getLockName())
                        .withRepresentationProperty(LOCK_ID_PROPERTY).build();
                thingDiscovered(discoveryResult);

            });
        }
    }

    @Override
    protected void stopBackgroundDiscovery() {
        stopScan();
        discoveryJob.ifPresent(job -> {
            if (!job.isCancelled()) {
                job.cancel(true);
            }
            discoveryJob = Optional.empty();
        });
    }

    @Override
    protected synchronized void stopScan() {
        logger.debug("Stop scan for devices.");
        super.stopScan();
    }
}
