/*
 * Copyright 2019 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.hyperledger.besu.tests.acceptance.crosschain.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.tests.acceptance.crosschain.common.CrosschainAcceptanceTestBase;

import java.math.BigInteger;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.web3j.protocol.besu.crypto.crosschain.BlsThresholdCryptoSystem;
import org.web3j.protocol.besu.response.crosschain.BlockchainNodeInformation;
import org.web3j.protocol.besu.response.crosschain.CoordinationContractInformation;
import org.web3j.protocol.besu.response.crosschain.CrossBlockchainPublicKeyResponse;
import org.web3j.protocol.besu.response.crosschain.KeyGenFailureToCompleteReason;
import org.web3j.protocol.besu.response.crosschain.KeyStatus;

/*
 * Two blockchains with one node are created. And all the crosschain API methods are then tested.
 * KeyGeneration is not well tested, because we do not currently support multiple nodes in a blockchain.
 * DevP2P for crosschain is WIP. TODO Testing all aspects of Key Generation and various ways it can fail.
 */

public class ApiTest extends CrosschainAcceptanceTestBase {
  private static final Logger LOG = LogManager.getLogger();

  @Before
  public void setUp() throws Exception {
    setUpCoordinationChain();
    setUpBlockchain1();
  }

  @Test
  /*
   * Tests the cross_addLinkedNode, cross_removeLinkedNode and cross_getListLinkedNodes APIs
   */
  public void multinodeApiTest() throws Exception {
    final int LIMIT = 100;
    final int OFFSET = 10;

    for (int i = 0; i < LIMIT; i++) {
      int portVal = 8545 + i;
      this.nodeOnBlockchain1.execute(
          crossTransactions.getAddLinkedNode(
              BigInteger.valueOf(i + OFFSET), "127.0.0.1:" + portVal));
    }
    List<BlockchainNodeInformation> nodes =
        this.nodeOnBlockchain1.execute(crossTransactions.getListLinkedNodes());
    assertThat(nodes.size()).isEqualTo(LIMIT);

    Set<BigInteger> chainIds = new HashSet<BigInteger>();
    Set<BigInteger> expectedChainIds = new HashSet<BigInteger>();
    for (int i = 0; i < LIMIT; i++) {
      chainIds.add(nodes.get(i).blockchainId);
      expectedChainIds.add(BigInteger.valueOf(i + OFFSET));
    }
    assertThat(chainIds).isEqualTo(expectedChainIds);

    for (int i = 0; i < LIMIT / 2; i++) {
      this.nodeOnBlockchain1.execute(
          crossTransactions.getRemoveLinkedNode(BigInteger.valueOf(i + OFFSET)));
      expectedChainIds.remove(BigInteger.valueOf(i + OFFSET));
    }

    nodes.clear();
    nodes = this.nodeOnBlockchain1.execute(crossTransactions.getListLinkedNodes());
    assertThat(nodes.size()).isEqualTo(LIMIT / 2);

    chainIds.clear();
    for (int i = 0; i < LIMIT / 2; i++) {
      chainIds.add(nodes.get(i).blockchainId);
    }
    assertThat(chainIds).isEqualTo(expectedChainIds);
  }

  @Test
  /*
   * Tests the APIs that are related to keys.
   */
  public void keyTest() throws Exception {
    // There is no public key generated for this chain yet. So it returns a null response.
    CrossBlockchainPublicKeyResponse pubKey =
        this.nodeOnBlockchain1.execute(crossTransactions.getBlockchainPublicKey(1));
    assertThat(pubKey.getRawResponse()).isNull();

    // Because there are no keys, activating the key with version 1 should be ignored.
    // 0 indicates no valid key present
    this.nodeOnBlockchain1.execute(crossTransactions.activateKey(1));
    BigInteger keyVersion = this.nodeOnBlockchain1.execute(crossTransactions.getActiveKeyVersion());
    assertThat(keyVersion.longValue()).isEqualTo(0);

    // Start the key generation process
    keyVersion =
        this.nodeOnBlockchain1.execute(
            crossTransactions.startThresholdKeyGeneration(
                1, BlsThresholdCryptoSystem.ALT_BN_128_WITH_KECCAK256));
    assertThat(keyVersion.longValue()).isEqualTo(1);

    KeyGenFailureToCompleteReason reason =
        this.nodeOnBlockchain1.execute(
            crossTransactions.getKeyGenFailureReason(keyVersion.longValue()));
    assertThat(reason.value).isEqualTo(KeyGenFailureToCompleteReason.SUCCESS.value);

    // Get the key version from the API and check
    KeyStatus keyStatus =
        this.nodeOnBlockchain1.execute(crossTransactions.getKeyStatus(keyVersion.longValue()));
    assertThat(keyStatus.value).isEqualTo(KeyStatus.KEY_GEN_COMPLETE.value);

    // Activate the key of version 1 and check the APIs crossGetActiveKeyVersion and
    // crossGetKeyStatus
    this.nodeOnBlockchain1.execute(crossTransactions.activateKey(keyVersion.longValue()));
    BigInteger keyVersionFromApi =
        this.nodeOnBlockchain1.execute(crossTransactions.getActiveKeyVersion());
    assertThat(keyVersionFromApi).isEqualTo(keyVersion);

    // Check the API crossGetKeyActiveNodes
    List<BigInteger> activeKeyNodes =
        this.nodeOnBlockchain1.execute(crossTransactions.getKeyActiveNodes(keyVersion.longValue()));
    assertThat(activeKeyNodes.size()).isEqualTo(1);
    LOG.info("Active Key Nodes are: ");
    for (BigInteger n : activeKeyNodes) {
      LOG.info("{} ", n.longValue());
    }

    // Check the API crossGetKeyGenNodesDroppedOutOfKeyGeneration
    Map<BigInteger, KeyGenFailureToCompleteReason> nodesReasons =
        this.nodeOnBlockchain1.execute(
            crossTransactions.getKeyGenNodesDroppedOutOfKeyGeneration(keyVersion.longValue()));
    assertThat(nodesReasons.size()).isEqualTo(0);

    keyStatus =
        this.nodeOnBlockchain1.execute(crossTransactions.getKeyStatus(keyVersion.longValue()));
    assertThat(keyStatus.value).isEqualTo(KeyStatus.ACTIVE_KEY.value);

    // Generate the key once again
    keyVersion =
        this.nodeOnBlockchain1.execute(
            crossTransactions.startThresholdKeyGeneration(
                1, BlsThresholdCryptoSystem.ALT_BN_128_WITH_KECCAK256));
    assertThat(keyVersion.longValue()).isEqualTo(2);

    // Get the key version again from the API and check
    this.nodeOnBlockchain1.execute(crossTransactions.activateKey(keyVersion.longValue()));
    keyVersionFromApi = this.nodeOnBlockchain1.execute(crossTransactions.getActiveKeyVersion());
    assertThat(keyVersionFromApi).isEqualTo(keyVersion);

    // Activate the old version and check the key version from the API
    this.nodeOnBlockchain1.execute(crossTransactions.activateKey(1));
    keyVersion = this.nodeOnBlockchain1.execute(crossTransactions.getActiveKeyVersion());
    assertThat(keyVersion.longValue()).isEqualTo(1);
  }

  @Test
  /*
   * Tests the APIs that are related to coordination contracts
   */
  public void coordTest() throws Exception {

    List<CoordinationContractInformation> coordCtrtList =
        this.nodeOnBlockchain1.execute(crossTransactions.listCoordinationContracts());
    assertThat(coordCtrtList.size()).isEqualTo(1);

    // There is one coordination contract on a separate chain called coordination chain
    coordCtrtList =
        this.nodeOnCoordinationBlockchain.execute(crossTransactions.listCoordinationContracts());
    // I thought there is one. Surprised to learn there isn't any. Need to explicitly add.
    assertThat(coordCtrtList.size()).isEqualTo(1);

    // Removing a coordination contract
    this.nodeOnBlockchain1.execute(
        crossTransactions.removeCoordinationContract(
            this.nodeOnCoordinationBlockchain.getChainId(),
            this.coordContract.getContractAddress()));
    coordCtrtList = this.nodeOnBlockchain1.execute(crossTransactions.listCoordinationContracts());
    assertThat(coordCtrtList.size()).isEqualTo(0);

    this.nodeOnCoordinationBlockchain.execute(
        crossTransactions.removeCoordinationContract(
            this.nodeOnCoordinationBlockchain.getChainId(),
            this.coordContract.getContractAddress()));
    coordCtrtList = this.nodeOnBlockchain1.execute(crossTransactions.listCoordinationContracts());
    assertThat(coordCtrtList.size()).isEqualTo(0);

    // Adding a coordination contract
    String ipAddress = this.nodeOnCoordinationBlockchain.jsonRpcListenHost1();
    int port = this.nodeOnCoordinationBlockchain.getJsonRpcSocketPort1().intValue();
    String ipAddressAndPort = ipAddress + ":" + port;
    this.nodeOnCoordinationBlockchain.execute(
        crossTransactions.addCoordinationContract(
            this.nodeOnCoordinationBlockchain.getChainId(),
            this.coordContract.getContractAddress(),
            ipAddressAndPort));
    coordCtrtList =
        this.nodeOnCoordinationBlockchain.execute(crossTransactions.listCoordinationContracts());
    assertThat(coordCtrtList.size()).isEqualTo(1);
    LOG.info("Coordination Contracts are ...");
    for (CoordinationContractInformation coordCtrt : coordCtrtList) {
      LOG.info(
          "{} {} {}\n",
          coordCtrt.coodinationContract,
          coordCtrt.coordinationBlockchainId,
          coordCtrt.ipAddressAndPort);
    }

    // Adding a coordination contract using another chain
    this.nodeOnBlockchain1.execute(
        crossTransactions.addCoordinationContract(
            this.nodeOnCoordinationBlockchain.getChainId(),
            this.coordContract.getContractAddress(),
            ipAddressAndPort));
    coordCtrtList = this.nodeOnBlockchain1.execute(crossTransactions.listCoordinationContracts());
    assertThat(coordCtrtList.size()).isEqualTo(1);
    LOG.info("Coordination Contracts are ...");
    for (CoordinationContractInformation coordCtrt : coordCtrtList) {
      LOG.info(
          "{} {} {}\n",
          coordCtrt.coodinationContract,
          coordCtrt.coordinationBlockchainId,
          coordCtrt.ipAddressAndPort);
    }
  }

  @After
  public void closeDown() throws Exception {
    this.cluster.close();
    this.clusterBc1.close();
  }
}
