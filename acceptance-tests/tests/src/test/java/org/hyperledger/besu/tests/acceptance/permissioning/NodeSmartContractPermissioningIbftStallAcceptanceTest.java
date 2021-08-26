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

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.permissioning.PermissioningConfiguration;
import org.hyperledger.besu.ethereum.permissioning.SmartContractPermissioningConfiguration;
import org.hyperledger.besu.tests.acceptance.dsl.node.BesuNode;

import java.io.IOException;
import java.util.Optional;

import org.junit.Test;

public class NodeSmartContractPermissioningIbftStallAcceptanceTest
    extends NodeSmartContractPermissioningAcceptanceTestBase {

  private static final String GENESIS_FILE =
      "/permissioning/simple_permissioning_ibft_genesis.json";

  @Test
  public void restartedIbftClusterShouldNotStall() throws IOException {
    final BesuNode bootnode = besu.createIbft2NonValidatorBootnode("bootnode", GENESIS_FILE);
    final BesuNode nodeA = besu.createIbft2Node("nodeA", GENESIS_FILE);
    final BesuNode nodeB = besu.createIbft2Node("nodeB", GENESIS_FILE);

    permissionedCluster.start(bootnode, nodeA, nodeB);

    // make sure we are producing blocks before sending any transactions
    waitForBlockHeight(bootnode, 1);

    // allow nodes in onchain smart contract
    nodeA.execute(allowNode(bootnode));
    nodeA.execute(allowNode(nodeA));
    nodeA.execute(allowNode(nodeB));

    // verify the nodes are allowed
    nodeA.verify(nodeIsAllowed(bootnode));
    nodeA.verify(nodeIsAllowed(nodeA));
    nodeA.verify(nodeIsAllowed(nodeB));

    permissionedCluster.stop();

    // Create permissioning config
    final SmartContractPermissioningConfiguration smartContractPermissioningConfiguration =
        new SmartContractPermissioningConfiguration();
    smartContractPermissioningConfiguration.setSmartContractNodeAllowlistEnabled(true);
    smartContractPermissioningConfiguration.setNodeSmartContractAddress(
        Address.fromHexString(CONTRACT_ADDRESS));
    final PermissioningConfiguration permissioningConfiguration =
        new PermissioningConfiguration(
            Optional.empty(),
            Optional.of(smartContractPermissioningConfiguration),
            Optional.empty());

    // Set permissioning configurations on nodes
    bootnode.setPermissioningConfiguration(permissioningConfiguration);
    nodeA.setPermissioningConfiguration(permissioningConfiguration);
    nodeB.setPermissioningConfiguration(permissioningConfiguration);

    permissionedCluster.start(bootnode, nodeA, nodeB);

    // Verify blockchain is progressing
    permissionedCluster.verify(blockchain.reachesHeight(bootnode, 1, 120));
  }
}
