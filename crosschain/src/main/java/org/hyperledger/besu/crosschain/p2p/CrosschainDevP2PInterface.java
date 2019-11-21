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

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

public interface CrosschainDevP2PInterface {

  /**
   * Request other nodes start the Threshold Key Generation process.
   *
   * @param keyVersion The key version to be generated.
   */
  void requestStartNewKeyGeneration(final long keyVersion);

  void sendPrivateValues(
      final BigInteger myAddress,
      final List<BigInteger> nodeAddresses,
      final Map<BigInteger, BigInteger> mySecretShares);

  void setSecretShareCallback(final CrosschainPartSecretShareCallback implementation);

  // ****** Everything below here is only needed for test implementations.
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
