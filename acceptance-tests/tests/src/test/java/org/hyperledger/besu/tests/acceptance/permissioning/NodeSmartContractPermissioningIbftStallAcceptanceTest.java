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
package org.hyperledger.besu.tests.acceptance.permissioning;

import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.permissioning.PermissioningConfiguration;
import org.hyperledger.besu.ethereum.permissioning.SmartContractPermissioningConfiguration;
import org.hyperledger.besu.tests.acceptance.dsl.node.BesuNode;

import java.io.IOException;
import java.util.Optional;

import org.junit.Ignore;
import org.junit.Test;

public class NodeSmartContractPermissioningIbftStallAcceptanceTest
    extends NodeSmartContractPermissioningAcceptanceTestBase {

  private static final String GENESIS_FILE =
      "/permissioning/simple_permissioning_ibft_genesis.json";

  @Ignore("Temorarily disabled: See hyperledger/besu#1011")
  @Test
  public void restartedIbftClusterShouldNotStall() throws IOException {
    final BesuNode bootnode = besu.createIbft2NonValidatorBootnode("bootnode", GENESIS_FILE);
    final BesuNode nodeA = besu.createIbft2Node("nodeA", GENESIS_FILE);
    final BesuNode nodeB = besu.createIbft2Node("nodeB", GENESIS_FILE);
    final BesuNode nodeC = besu.createIbft2Node("nodeC", GENESIS_FILE);
    final BesuNode nodeD = besu.createIbft2Node("nodeD", GENESIS_FILE);

    permissionedCluster.start(bootnode, nodeA, nodeB, nodeC, nodeD);

    bootnode.verify(net.awaitPeerCount(4));

    // update onchain smart contract to whitelist nodes
    nodeA.execute(allowNode(bootnode));
    nodeA.verify(nodeIsAllowed(bootnode));
    nodeA.execute(allowNode(nodeA));
    nodeA.verify(nodeIsAllowed(nodeA));
    nodeA.execute(allowNode(nodeB));
    nodeA.verify(nodeIsAllowed(nodeB));
    nodeA.execute(allowNode(nodeC));
    nodeA.verify(nodeIsAllowed(nodeC));
    nodeA.execute(allowNode(nodeD));
    nodeA.verify(nodeIsAllowed(nodeD));

    waitForBlockHeight(bootnode, 10);
    permissionedCluster.stop();

    // Create permissioning config
    final SmartContractPermissioningConfiguration smartContractPermissioningConfiguration =
        new SmartContractPermissioningConfiguration();
    smartContractPermissioningConfiguration.setSmartContractNodeWhitelistEnabled(true);
    smartContractPermissioningConfiguration.setNodeSmartContractAddress(
        Address.fromHexString(CONTRACT_ADDRESS));
    final PermissioningConfiguration permissioningConfiguration =
        new PermissioningConfiguration(
            Optional.empty(), Optional.of(smartContractPermissioningConfiguration));

    // Set permissioning configurations on nodes
    bootnode.setPermissioningConfiguration(permissioningConfiguration);
    nodeA.setPermissioningConfiguration(permissioningConfiguration);
    nodeB.setPermissioningConfiguration(permissioningConfiguration);
    nodeC.setPermissioningConfiguration(permissioningConfiguration);
    nodeD.setPermissioningConfiguration(permissioningConfiguration);

    permissionedCluster.start(bootnode, nodeA, nodeB, nodeC, nodeD);

    // Verify blockchain is progressing
    waitForBlockHeight(bootnode, 15);
  }
}
