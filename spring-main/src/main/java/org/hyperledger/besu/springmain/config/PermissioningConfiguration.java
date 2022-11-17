/*
 *
 *  * Copyright Hyperledger Besu Contributors.
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 *  * the License. You may obtain a copy of the License at
 *  *
 *  * http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 *  * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 *  * specific language governing permissions and limitations under the License.
 *  *
 *  * SPDX-License-Identifier: Apache-2.0
 *
 */

package org.hyperledger.besu.springmain.config;

import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.Synchronizer;
import org.hyperledger.besu.ethereum.p2p.config.DiscoveryConfiguration;
import org.hyperledger.besu.ethereum.p2p.permissions.PeerPermissions;
import org.hyperledger.besu.ethereum.p2p.permissions.PeerPermissionsDenylist;
import org.hyperledger.besu.ethereum.permissioning.node.NodePermissioningController;
import org.hyperledger.besu.ethereum.permissioning.node.PeerPermissionsAdapter;
import org.hyperledger.besu.ethereum.transaction.TransactionSimulator;
import org.hyperledger.besu.plugin.data.EnodeURL;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;

public class PermissioningConfiguration {

//    @Bean
//    private Optional<NodePermissioningController> nodePermissioningController(
//            final List<EnodeURL> bootnodesAsEnodeURLs,
//            final Synchronizer synchronizer,
//            final TransactionSimulator transactionSimulator,
//            final Bytes localNodeId,
//            final Blockchain blockchain,
//            final MetricsSystem metricsSystem) {
//        final Collection<EnodeURL> fixedNodes = getFixedNodes(bootnodesAsEnodeURLs, staticNodes);
//
//        if (permissioningConfiguration.isPresent()) {
//            final org.hyperledger.besu.ethereum.permissioning.PermissioningConfiguration configuration = this.permissioningConfiguration.get();
//            final NodePermissioningController nodePermissioningController =
//                    new NodePermissioningControllerFactory()
//                            .create(
//                                    configuration,
//                                    synchronizer,
//                                    fixedNodes,
//                                    localNodeId,
//                                    transactionSimulator,
//                                    metricsSystem,
//                                    blockchain,
//                                    permissioningService.getConnectionPermissioningProviders());
//
//            return Optional.of(nodePermissioningController);
//        } else if (permissioningService.getConnectionPermissioningProviders().size() > 0) {
//            final NodePermissioningController nodePermissioningController =
//                    new NodePermissioningControllerFactory()
//                            .create(
//                                    new org.hyperledger.besu.ethereum.permissioning.PermissioningConfiguration(
//                                            Optional.empty(), Optional.empty(), Optional.empty()),
//                                    synchronizer,
//                                    fixedNodes,
//                                    localNodeId,
//                                    transactionSimulator,
//                                    metricsSystem,
//                                    blockchain,
//                                    permissioningService.getConnectionPermissioningProviders());
//
//            return Optional.of(nodePermissioningController);
//        } else {
//            return Optional.empty();
//        }
//    }


    @Bean
    @ConditionalOnBean(NodePermissioningController.class)
    public PeerPermissions peerPermissions(NodePermissioningController nodePermissioningController, Blockchain blockchain, DiscoveryConfiguration discoveryConfiguration, PeerPermissionsDenylist peerPermissionsDenylist) {
        return Optional.of(nodePermissioningController)
                .map(nodePC -> new PeerPermissionsAdapter(nodePC, discoveryConfiguration.getBootnodes(), blockchain))
                .map(nodePerms -> PeerPermissions.combine(nodePerms, peerPermissionsDenylist))
                .orElse(peerPermissionsDenylist);
    }

    @Bean
    @ConditionalOnMissingBean(NodePermissioningController.class)
    PeerPermissionsDenylist peerPermissionsDenylist() {
        return PeerPermissionsDenylist.create();
    }
}
