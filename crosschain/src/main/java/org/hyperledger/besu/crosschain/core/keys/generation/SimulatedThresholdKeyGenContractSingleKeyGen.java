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
import org.hyperledger.besu.crypto.Hash;
import org.hyperledger.besu.util.bytes.Bytes32;
import org.hyperledger.besu.util.bytes.BytesValue;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Map;
import java.util.TreeMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

// Simulates a contract which sits on the sidechain.
class SimulatedThresholdKeyGenContractSingleKeyGen {
  protected static final Logger LOG = LogManager.getLogger();
  private ArrayList<BigInteger> nodeIdArray = new ArrayList<>();
  private Map<BigInteger, BlsPoint[]> coefficientPublicValues = new TreeMap<>();
  private Map<BigInteger, Bytes32[]> coefPublicPointCommitments = new TreeMap<>();
  private int threshold;

  // TODO use this to change when values can be posted.
  //  private int roundDurationInBlocks;

  SimulatedThresholdKeyGenContractSingleKeyGen(
      final int threshold, final int roundDurationInBlocks) {
    this.threshold = threshold;
    //  this.roundDurationInBlocks = roundDurationInBlocks;
  }

  void setNodeId(final BigInteger msgSender) {
    this.nodeIdArray.add(msgSender);
  }

  void setNodeCoefficientsCommitments(
      final BigInteger msgSender, final Bytes32[] coefPublicPointCommitments) {
    if (coefPublicPointCommitments.length != this.threshold) {
      throw new RuntimeException(
          "Number of coefficient public value commitments did not match expected number of coefficients");
    }
    LOG.info("msgSender: {}", msgSender);
    LOG.info("set commitments: {}", coefPublicPointCommitments[0]);
    this.coefPublicPointCommitments.put(msgSender, coefPublicPointCommitments);
  }

  void setNodeCoefficientsPublicValues(
      final BigInteger msgSender, final BlsPoint[] coefPublicPoints) {
    if (coefPublicPoints.length != this.threshold) {
      throw new RuntimeException(
          "Number of coefficient public values did not match expected number of coefficients");
    }

    // Check that the coefficient public points match what was committed to.
    // Reject requests to upload points which don't match the commitment.
    Bytes32[] commitments = this.coefPublicPointCommitments.get(msgSender);
    LOG.info("msgSender: {}", msgSender);
    LOG.info("commitments length: {}", commitments.length);
    LOG.info("Coeff pub point {}", coefPublicPoints[0]);
    for (int i = 0; i < coefPublicPoints.length; i++) {
      byte[] coefPubBytes = coefPublicPoints[i].store();
      Bytes32 commitment = Hash.keccak256(BytesValue.wrap(coefPubBytes));
      LOG.info("commitments[{}]: {}, and commitment is {}", i, commitments[i], commitment);

      if (!commitments[i].equals(commitment)) {
        throw new RuntimeException("Public value did not match commitment");
      }
    }

    this.coefficientPublicValues.put(msgSender, coefPublicPoints);
  }

  int getThreshold() {
    return this.threshold;
  }

  int getNumberOfNodes() {
    return this.nodeIdArray.size();
  }

  BigInteger getNodeAddress(final int index) {
    return this.nodeIdArray.get(index);
  }

  boolean nodeCoefficientsCommitmentsSet(final BigInteger address) {
    return this.coefPublicPointCommitments.get(address) != null;
  }

  public BlsPoint getCoefficientPublicValue(final BigInteger fromAddress, final int coefNumber) {
    if (this.coefficientPublicValues.get(fromAddress) == null) {
      return null;
    }
    return this.coefficientPublicValues.get(fromAddress)[coefNumber];
  }
}
