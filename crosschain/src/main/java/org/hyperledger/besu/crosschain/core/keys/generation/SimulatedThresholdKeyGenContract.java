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
import org.hyperledger.besu.util.bytes.Bytes32;

import java.math.BigInteger;
import java.util.Map;
import java.util.TreeMap;

// Simulates a contract which sits on the sidechain.
class SimulatedThresholdKeyGenContract {
  private long expectedNextVersion;

  private Map<Long, SimulatedThresholdKeyGenContractSingleKeyGen> keyGens = new TreeMap<>();

  public SimulatedThresholdKeyGenContract() {
    this(0);
  }

  /**
   * Typically, the key version will always start from 0. However, if a bug was found in the
   * contract, and a new version of the contract needed to be deployed, then the version should be
   * set to the key version the old contract was up to.
   *
   * @param version Key version to start from.
   */
  public SimulatedThresholdKeyGenContract(final long version) {
    this.expectedNextVersion = version;
  }

  void startNewKeyGeneration(
      final long version,
      final BigInteger msgSender,
      final int threshold,
      final int roundDurationInBlocks) {
    if (version != this.expectedNextVersion) {
      // Simulate a require() statement.
      throw new RuntimeException(
          "require: As a way of ensuring only one key generation as any version number");
    }
    this.keyGens.put(
        version,
        new SimulatedThresholdKeyGenContractSingleKeyGen(threshold, roundDurationInBlocks));
    this.expectedNextVersion++;

    setNodeId(version, msgSender);
  }

  void setNodeId(final long version, final BigInteger msgSender) {
    getKeyGenInstance(version).setNodeId(msgSender);
  }

  void setNodeCoefficientsCommitments(
      final long version, final BigInteger msgSender, final Bytes32[] coefPublicPointCommitments) {
    getKeyGenInstance(version)
        .setNodeCoefficientsCommitments(msgSender, coefPublicPointCommitments);
  }

  void setNodeCoefficientsPublicValues(
      final long version, final BigInteger msgSender, final BlsPoint[] coefPublicPoints) {
    getKeyGenInstance(version).setNodeCoefficientsPublicValues(msgSender, coefPublicPoints);
  }

  long getExpectedKeyGenerationVersion() {
    return this.expectedNextVersion;
  }

  int getThreshold(final long version) {
    return getKeyGenInstance(version).getThreshold();
  }

  int getNumberOfNodes(final long version) {
    return getKeyGenInstance(version).getNumberOfNodes();
  }

  BigInteger getNodeAddress(final long version, final int index) {
    return getKeyGenInstance(version).getNodeAddress(index);
  }

  boolean nodeCoefficientsCommitmentsSet(final long version, final BigInteger address) {
    return getKeyGenInstance(version).nodeCoefficientsCommitmentsSet(address);
  }

  BlsPoint getCoefficientPublicValue(
      final long version, final BigInteger fromAddress, final int coefNumber) {
    return getKeyGenInstance(version).getCoefficientPublicValue(fromAddress, coefNumber);
  }

  private SimulatedThresholdKeyGenContractSingleKeyGen getKeyGenInstance(final long version) {
    SimulatedThresholdKeyGenContractSingleKeyGen keyGen = this.keyGens.get(version);
    if (keyGen == null) {
      throw new RuntimeException(
          "require: As a way of ensuring only one key generation as any version number");
    }
    return keyGen;
  }
}
