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
package org.hyperledger.besu.crosschain.core;

import java.math.BigInteger;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Managers the relationship between nodes in this multichain node. */
public class MultichainManager {
  private static final Logger LOG = LogManager.getLogger();

  private Map<BigInteger, String> nodeMap = new TreeMap<>();

  /**
   * Add a node. If the node already exists, update the IP Address and Port information.
   *
   * @param blockchainId Blockchain to be added.
   * @param ipAddressAndPort Network address of JSON RPC of the node.
   */
  public void addNode(final BigInteger blockchainId, final String ipAddressAndPort) {
    this.nodeMap.put(blockchainId, ipAddressAndPort);
  }

  /**
   * Remove a node from the multichain node.
   *
   * @param blockchainId Blockchain to be removed.
   */
  public void removeNode(final BigInteger blockchainId) {
    this.nodeMap.remove(blockchainId);
  }

  /**
   * List all of the blockchains that form this Multichain Node.
   *
   * @return The set of all of the nodes.
   */
  public Set<BigInteger> listAllNodes() {
    return this.nodeMap.keySet();
  }

  /**
   * Return the IP address and port given a blockchain. An error is thrown if the blockhcain is not
   * part of the multichain node. The assumption is that this has been checked prior to this call,
   * and appropriate "bad parameter" style errors have already been returned.
   *
   * @param blockchainId Blockchain to get information for.
   * @return IP Address and Port associated with the blockchain ID.
   */
  public String getIpAddressAndPort(final BigInteger blockchainId) {
    String ipAndPort = this.nodeMap.get(blockchainId);
    if (ipAndPort == null) {
      String error = "Blockchain not part of Multichain Node: " + blockchainId;
      LOG.error(error);
      throw new RuntimeException(error);
    }
    return ipAndPort;
  }

  /**
   * Indicate if a blockchain is part of the multichain node.
   *
   * @param blockchainId Blockchain to check.
   * @return true if information exists for the blockchain.
   */
  public boolean isPartOfMultichainNode(final BigInteger blockchainId) {
    String ipAndPort = this.nodeMap.get(blockchainId);
    return ipAndPort != null;
  }
}
