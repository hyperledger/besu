/*
 * Copyright Hyperledger Besu Contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See
 *  the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.springmain.config;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.metrics.MetricsOptions;
import org.hyperledger.besu.metrics.vertx.VertxMetricsAdapterFactory;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

public class VertxConfiguration {


    @Bean
    public Vertx vertx(VertxOptions vertxOptions){
        return Vertx.vertx(vertxOptions);
    }

    @Bean
    VertxOptions vertxOptions(MetricsSystem metricsSystem) {
        return new VertxOptions().setPreferNativeTransport(true)
                .setMetricsOptions(
                        new MetricsOptions()
                                .setEnabled(true)
                                .setFactory(new VertxMetricsAdapterFactory(metricsSystem)));
    }
}
