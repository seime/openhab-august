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
package no.seime.openhab.binding.august.internal.handler;

import java.util.Collections;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.openhab.core.config.discovery.DiscoveryService;
import org.openhab.core.io.net.http.HttpClientFactory;
import org.openhab.core.storage.StorageService;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.ThingUID;
import org.openhab.core.thing.binding.BaseThingHandlerFactory;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.thing.binding.ThingHandlerFactory;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceRegistration;
import org.osgi.framework.wiring.BundleWiring;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import com.google.gson.Gson;

import no.seime.openhab.binding.august.internal.BindingConstants;
import no.seime.openhab.binding.august.internal.GsonFactory;
import no.seime.openhab.binding.august.internal.comm.RestApiClient;
import no.seime.openhab.binding.august.internal.discovery.AugustDiscoveryService;

/**
 * The {@link AugustThingHandlerFactory} is responsible for creating things and thing
 * handlers.
 *
 * @author Arne Seime - Initial contribution
 */
@NonNullByDefault
@Component(configurationPid = "binding.august", service = ThingHandlerFactory.class)
public class AugustThingHandlerFactory extends BaseThingHandlerFactory {
    public static final Set<ThingTypeUID> SUPPORTED_THING_TYPES_UIDS = Collections.unmodifiableSet(Stream
            .of(BindingConstants.THING_TYPE_ACCOUNT, BindingConstants.THING_TYPE_LOCK).collect(Collectors.toSet()));
    @NonNullByDefault({})
    private final HttpClient httpClient;
    @NonNullByDefault({})
    private final StorageService storageService;
    private final Map<ThingUID, ServiceRegistration<?>> discoveryServiceRegs = new HashMap<>();

    private final Gson gson = GsonFactory.create();

    @Activate
    public AugustThingHandlerFactory(@Reference HttpClientFactory httpClientFactory,
            @Reference StorageService storageService) {
        this.httpClient = httpClientFactory.getCommonHttpClient();
        this.storageService = storageService;
    }

    @Override
    protected @Nullable ThingHandler createHandler(final Thing thing) {
        final ThingTypeUID thingTypeUID = thing.getThingTypeUID();
        if (BindingConstants.THING_TYPE_LOCK.equals(thingTypeUID)) {
            return new AugustLockHandler(thing, gson);
        } else if (BindingConstants.THING_TYPE_ACCOUNT.equals(thingTypeUID)) {
            RestApiClient restApiClient = new RestApiClient(httpClient, gson);
            AugustAccountHandler accountHandler = new AugustAccountHandler((Bridge) thing, restApiClient,
                    storageService.getStorage(thing.getUID().toString(),
                            FrameworkUtil.getBundle(getClass()).adapt(BundleWiring.class).getClassLoader()));
            registerDeviceDiscoveryService(accountHandler);
            return accountHandler;
        }
        return null;
    }

    private void registerDeviceDiscoveryService(AugustAccountHandler accountHandler) {
        AugustDiscoveryService discoveryService = new AugustDiscoveryService(accountHandler);
        discoveryServiceRegs.put(accountHandler.getThing().getUID(),
                bundleContext.registerService(DiscoveryService.class.getName(), discoveryService, new Hashtable<>()));
    }

    private void unregisterDeviceDiscoveryService(ThingUID thingUID) {
        if (discoveryServiceRegs.containsKey(thingUID)) {
            ServiceRegistration<?> serviceReg = discoveryServiceRegs.get(thingUID);
            serviceReg.unregister();
            discoveryServiceRegs.remove(thingUID);
        }
    }

    @Override
    protected void removeHandler(ThingHandler thingHandler) {
        if (thingHandler instanceof AugustAccountHandler) {
            ThingUID thingUID = thingHandler.getThing().getUID();
            unregisterDeviceDiscoveryService(thingUID);
        }
        super.removeHandler(thingHandler);
    }

    @Override
    public boolean supportsThingType(final ThingTypeUID thingTypeUID) {
        return SUPPORTED_THING_TYPES_UIDS.contains(thingTypeUID);
    }
}
