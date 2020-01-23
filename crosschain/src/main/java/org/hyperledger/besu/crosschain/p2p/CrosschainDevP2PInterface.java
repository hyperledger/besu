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

import org.hyperledger.besu.util.bytes.BytesValue;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface CrosschainDevP2PInterface {

  /**
   * Request the list of all peers connected with this peer. TODO: THIS ASSUMES ALL PEERS ARE
   * DIRECTLY CONNECTED WITH THIS PEER.
   *
   * @return node addresses of all peers.
   */
  Set<BigInteger> getAllPeers();

  /**
   * Request other nodes start the Threshold Key Generation process.
   *
   * <p>Send from this node to all other nodes.
   *
   * @param keyVersion The key version to be generated.
   */
  void requestStartNewKeyGeneration(final long keyVersion);

  /**
   * Send from this node to a specific node.
   *
   * @param myAddress The address of this node.
   * @param nodeAddresses The address of nodes to send the secret shares to.
   * @param mySecretShares The secret shares to send.
   */
  void sendPrivateValues(
      final BigInteger myAddress,
      final Set<BigInteger> nodeAddresses,
      final Map<BigInteger, BigInteger> mySecretShares);

  void setSecretShareCallback(final CrosschainPartSecretShareCallback implementation);
  // TODO void setSigningRequestCallback(final CrosschainPartSecretShareCallback implementation);
  // TODO void setSigningResponseCallback(final CrosschainPartSecretShareCallback implementation);

  /**
   * Send from this node to all nodes.
   *
   * @param myAddress The address of this node.
   * @param message The message to be signed.
   */
  void sendMessageSigningRequest(final BigInteger myAddress, final BytesValue message);

  //  /**
  //   * Send from this node to a specific node.
  //   *
  //   * @param myAddress
  //   */
  //  void sendMessageSigningResponse(
  //    final BigInteger myAddress,
  //    final Set<BigInteger> nodeAddresses,
  //    final BytesValue signedMessage);

  // TODO ****** Everything below here is only needed for test implementations.
  void setMyNodeAddress(final BigInteger myNodeAddress);

  void clearSimulatedNodes();

  void addSimulatedOtherNode(final BigInteger address, final SimulatedOtherNode node);

  void simulatedNodesSendPrivateValues(
      final BigInteger myAddress,
      final List<BigInteger> nodeAddresses,
      final Map<BigInteger, BigInteger> mySecretShares);

  void requestPostCommits(final long keyVersion);

  void requestPostPublicValues(final long keyVersion);

  void requestGetOtherNodeCoefs(final long keyVersion);

  void requestSendPrivateValues(final long keyVersion);

  void requestNodesCompleteKeyGen();
}
