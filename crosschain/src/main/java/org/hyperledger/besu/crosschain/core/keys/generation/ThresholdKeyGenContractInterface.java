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
package org.hyperledger.besu.crosschain.core.keys.generation;

import org.hyperledger.besu.crosschain.crypto.threshold.crypto.BlsPoint;
import org.hyperledger.besu.crypto.SECP256K1;
import org.hyperledger.besu.util.bytes.Bytes32;

import java.math.BigInteger;

/**
 * All implementations of the Threshold Key Generation Contract wrapper must implement this method.
 */
public interface ThresholdKeyGenContractInterface {
  void init(
      //      final TransactionSimulator transactionSimulator,
      //      final TransactionPool transactionPool,
      //      final BigInteger sidechainId,
      final SECP256K1.KeyPair nodeKeys
      //      final Blockchain blockchain,
      //      final WorldStateArchive worldStateArchive
      );

  void startNewKeyGeneration(final long version, final int threshold);

  void startNewKeyGeneration(
      final long version, final int threshold, final int roundDurationInBlocks);

  void setNodeId(final long version);

  void setNodeCoefficientsCommitments(
      final long version, final Bytes32[] coefPublicPointCommitments);

  void setNodeCoefficientsPublicValues(final long version, final BlsPoint[] coefPublicPoints);

  long getExpectedKeyGenerationVersion();

  int getThreshold(final long version);

  int getNumberOfNodes(final long version);

  BigInteger getNodeAddress(final long version, final int index);

  boolean nodeCoefficientsCommitmentsSet(final long version, final BigInteger address);

  BlsPoint getCoefficientPublicValue(
      final long version, final BigInteger fromAddress, final int coefNumber);

  // ****  Functions below here are needed for testing purposes only. ****
  void setNodeId(final long version, final BigInteger msgSender);

  void setNodeCoefficientsCommitments(
      final long version, final BigInteger msgSender, final Bytes32[] coefPublicPointCommitments);

  void setNodeCoefficientsPublicValues(
      final long version, final BigInteger msgSender, final BlsPoint[] coefPublicPoints);
}
