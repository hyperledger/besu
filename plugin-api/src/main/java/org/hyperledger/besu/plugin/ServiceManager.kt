/*
 * Copyright ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.plugin

import org.hyperledger.besu.plugin.services.BesuService
import java.util.*

/** Adds and accesses BesuServices for plugins to provide or use.  */
interface ServiceManager {
    /**
     * Add service. Used by core besu or other plugins to add services to the service manager.
     *
     * @param <T> the type parameter
     * @param serviceType the service type
     * @param service the service
    </T> */
    fun <T : BesuService> addService(serviceType: Class<T>, service: T)

    /**
     * Get the requested service, if it is available. There are a number of reasons that a service may
     * not be available:
     *
     *
     *  * The service may not have started yet. Most services are not available before the [       ][BesuPlugin.start] method is called
     *  * The service is not supported by this version of Besu
     *  * The service may not be applicable to the current configuration. For example some services
     * may only be available when a proof of authority network is in use, or when user provided.
     *
     *
     *
     * Since plugins are automatically loaded, unless the user has specifically requested
     * functionality provided by the plugin, no error should be raised if required services are
     * unavailable.
     *
     * @param serviceType the class defining the requested service.
     * @param <T> the service type
     * @return an optional containing the instance of the requested service, or empty if the service
     * is unavailable
    </T> */
    fun <T : BesuService> getService(serviceType: Class<T>): Optional<T>

    /** A basic implementation of ServiceManager, suitable for tests.  */
    class SimpleServiceManager
    /** default constructor. commented here to appease the linter.  */
        : ServiceManager {
        private val services: MutableMap<Class<out BesuService>, BesuService> = HashMap()

        override fun <T : BesuService> addService(serviceType: Class<T>, service: T) {
            services[serviceType] = service
        }

        override fun <T : BesuService> getService(serviceType: Class<T>): Optional<T> {
            return Optional.ofNullable(serviceType.cast(services[serviceType]))
        }
    }
}
