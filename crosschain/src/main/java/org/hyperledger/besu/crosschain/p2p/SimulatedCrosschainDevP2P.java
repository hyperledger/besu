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
package org.hyperledger.besu.crosschain.p2p;

import org.hyperledger.besu.crosschain.core.keys.generation.ThresholdKeyGenContractInterface;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class SimulatedCrosschainDevP2P implements CrosschainDevP2PInterface {
  protected static final Logger LOG = LogManager.getLogger();
  BigInteger realNodesAddress;

  private static final int DEFAULT_NUMBER_OF_SIMULATED_NODES = 0;
  public Map<BigInteger, SimulatedOtherNode> otherNodes = new TreeMap<>();

  public SimulatedCrosschainDevP2P(final ThresholdKeyGenContractInterface keyGenContractInterface) {
    this(keyGenContractInterface, DEFAULT_NUMBER_OF_SIMULATED_NODES);
  }

  public SimulatedCrosschainDevP2P(
      final ThresholdKeyGenContractInterface keyGenContractInterface,
      final int numberOfSimulatedNodes) {
    otherNodes = new TreeMap<>();
    for (int i = 0; i < numberOfSimulatedNodes; i++) {
      SimulatedOtherNode other =
          new SimulatedOtherNode(BigInteger.valueOf(i + 1), keyGenContractInterface, this);
      other.init();
    }
  }

  @Override
  public void setMyNodeAddress(final BigInteger myNodeAddress) {
    this.realNodesAddress = myNodeAddress;
  }

  @Override
  public Set<BigInteger> getAllPeers() {
    return this.otherNodes.keySet();
  }

  /**
   * Request other nodes start the Threshold Key Generation process.
   *
   * @param keyVersion The key version to be generated.
   */
  @Override
  public void requestStartNewKeyGeneration(final long keyVersion) {
    for (SimulatedOtherNode node : this.otherNodes.values()) {
      node.requestStartNewKeyGeneration(keyVersion);
    }
  }

  @Override
  public void sendPrivateValues(
      final BigInteger myAddress,
      final Set<BigInteger> nodeAddresses,
      final Map<BigInteger, BigInteger> mySecretShares) {
    for (BigInteger nodeAddress : nodeAddresses) {
      if (!nodeAddress.equals(myAddress)) {
        this.otherNodes
            .get(nodeAddress)
            .receivePrivateValue(myAddress, mySecretShares.get(nodeAddress));
      }
    }
  }

  CrosschainPartSecretShareCallback cb;

  @Override
  public void setSecretShareCallback(final CrosschainPartSecretShareCallback implementation) {
    this.cb = implementation;
  }

  @Override
  public void clearSimulatedNodes() {}

  @Override
  public void addSimulatedOtherNode(final BigInteger address, final SimulatedOtherNode node) {
    otherNodes.put(address, node);
  }

  @Override
  public void simulatedNodesSendPrivateValues(
      final BigInteger myAddress,
      final List<BigInteger> nodeAddresses,
      final Map<BigInteger, BigInteger> mySecretShares) {
    LOG.info("my address: {}", myAddress);
    LOG.info("real address: {}", realNodesAddress);

    for (BigInteger nodeAddress : nodeAddresses) {
      LOG.info("node address: {}", nodeAddress);
      if (nodeAddress.equals(realNodesAddress)) {
        this.cb.storePrivateSecretShareCallback(myAddress, mySecretShares.get(nodeAddress));
      } else if (!nodeAddress.equals(myAddress)) {
        LOG.info("processing node address: {}", nodeAddress);
        LOG.info(" othernodes.get(): {}", this.otherNodes.get(nodeAddress));
        this.otherNodes
            .get(nodeAddress)
            .receivePrivateValue(myAddress, mySecretShares.get(nodeAddress));
      }
    }
  }

  @Override
  public void requestPostCommits(final long keyVersion) {
    for (SimulatedOtherNode node : this.otherNodes.values()) {
      node.requestPostCommits(keyVersion);
    }
  }

  @Override
  public void requestPostPublicValues(final long keyVersion) {
    for (SimulatedOtherNode node : this.otherNodes.values()) {
      node.requestPostPublicValues(keyVersion);
    }
  }

  @Override
  public void requestGetOtherNodeCoefs(final long keyVersion) {
    for (SimulatedOtherNode node : this.otherNodes.values()) {
      node.requestGetOtherNodeCoefs(keyVersion);
    }
  }

  @Override
  public void requestSendPrivateValues(final long keyVersion) {
    for (SimulatedOtherNode node : this.otherNodes.values()) {
      node.requestSendPrivateValues(keyVersion);
    }
  }

  @Override
  public void requestNodesCompleteKeyGen() {
    for (SimulatedOtherNode node : this.otherNodes.values()) {
      node.requestNodesCompleteKeyGen();
    }
  }
}
