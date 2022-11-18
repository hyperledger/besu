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

import java.util.Optional;
import org.hyperledger.besu.cli.options.unstable.NatOptions;
import org.hyperledger.besu.ethereum.api.jsonrpc.JsonRpcConfiguration;
import org.hyperledger.besu.nat.NatMethod;
import org.hyperledger.besu.nat.NatService;
import org.hyperledger.besu.nat.core.NatManager;
import org.hyperledger.besu.nat.docker.DockerDetector;
import org.hyperledger.besu.nat.docker.DockerNatManager;
import org.hyperledger.besu.nat.kubernetes.KubernetesDetector;
import org.hyperledger.besu.nat.upnp.UpnpNatManager;
import org.hyperledger.besu.springmain.config.properties.P2PProperties;
import org.springframework.context.annotation.Bean;

import static java.util.function.Predicate.isEqual;
import static java.util.function.Predicate.not;

public class NatServiceConfiguration {

    @Bean
    public NatService natService(Optional<NatManager> manager, NatOptions natOptions) {
        final Boolean natMethodFallbackEnabled = natOptions.getNatMethodFallbackEnabled();
        return new NatService(manager, natMethodFallbackEnabled);
    }

    @Bean
    public Optional<NatManager> natManager(NatMethod natMethod, JsonRpcConfiguration jsonRpcConfiguration, P2PProperties p2pProperties) {
        final NatMethod detectedNatMethod =
                Optional.of(natMethod)
                        .filter(not(isEqual(NatMethod.AUTO)))
                        .orElse(NatService.autoDetectNatMethod(new KubernetesDetector(), new DockerDetector()));
        switch (detectedNatMethod) {
            case UPNP:
                return Optional.of(new UpnpNatManager());
            case DOCKER:
                return Optional.of(
                        new DockerNatManager(p2pProperties.getHost(), p2pProperties.getPort(), jsonRpcConfiguration.getPort()));
            case NONE:
            default:
                return Optional.empty();
        }
    }

    @Bean
    public NatMethod NatMethod() {
        return NatMethod.AUTO;
    }

    @Bean
    public NatOptions natOptions() {
        return NatOptions.create();
    }
}
