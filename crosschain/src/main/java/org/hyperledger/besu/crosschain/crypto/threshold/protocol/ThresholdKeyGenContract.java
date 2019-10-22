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
package org.hyperledger.besu.crosschain.crypto.threshold.protocol;

import org.hyperledger.besu.crypto.Hash;
import org.hyperledger.besu.crosschain.crypto.threshold.crypto.BlsPoint;
import org.hyperledger.besu.util.bytes.Bytes32;
import org.hyperledger.besu.util.bytes.BytesValue;

import java.math.BigInteger;

// Simulates a contract which sits on the sidechain.
public class ThresholdKeyGenContract {
  private BigInteger[] nodeIds;
  private BlsPoint[][] coefficientPublicValues;
  private Bytes32[][] coefPublicPointCommitments;
  private int threshold;

  public ThresholdKeyGenContract(final int threshold, final int numberOfNodes) {
    this.threshold = threshold;
    this.nodeIds = new BigInteger[numberOfNodes];
    this.coefficientPublicValues = new BlsPoint[numberOfNodes][];
    this.coefPublicPointCommitments = new Bytes32[numberOfNodes][];
  }

  // Use NodeId to simulate the signing of a transaction / tracing the source of the transaction.
  public void setNodeId(final int nodeNumber, final BigInteger nodeId) throws Exception {
    if (this.nodeIds[nodeNumber] != null) {
      throw new Exception("Attempting to over write a node id");
    }
    nodeIds[nodeNumber] = nodeId;
  }

  public BigInteger[] getAllNodeIds() {
    return this.nodeIds;
  }

  public void setNodeCoefficientsCommitments(
      final int nodeNumber, final Bytes32[] coefPublicPointCommitments) throws Exception {
    if (coefPublicPointCommitments.length != this.threshold) {
      throw new Exception(
          "Number of coefficient public value commitments did not match expected number of coefficients");
    }
    this.coefPublicPointCommitments[nodeNumber] = coefPublicPointCommitments;
  }

  public void setNodeCoefficientsPublicValues(
      final int nodeNumber, final BlsPoint[] coefPublicPoints) throws Exception {
    if (coefPublicPoints.length != this.threshold) {
      throw new Exception(
          "Number of coefficient public values did not match expected number of coefficients");
    }

    // Check that the coefficient public points match what was committed to.
    // Reject requests to upload points which don't match the commitment.
    for (int i = 0; i < coefPublicPoints.length; i++) {
      byte[] coefPubBytes = coefPublicPoints[i].store();
      Bytes32 commitment = Hash.keccak256(BytesValue.wrap(coefPubBytes));
      if (!this.coefPublicPointCommitments[nodeNumber][i].equals(commitment)) {
        throw new Exception("Public value did not match commitment");
      }
    }

    this.coefficientPublicValues[nodeNumber] = coefPublicPoints;
  }

  public BlsPoint getCoefficientPublicValue(final int nodeNumberFrom, final int coefNumber) {
    return this.coefficientPublicValues[nodeNumberFrom][coefNumber];
  }

  public BlsPoint[] getCoefficientPublicValues(final int nodeNumberFrom) {
    return this.coefficientPublicValues[nodeNumberFrom];
  }

  // TODO I don't think a getting is needed.
  public Bytes32[] getCoefficientPublicValueCommitments(final int nodeNumberFrom) {
    return this.coefPublicPointCommitments[nodeNumberFrom];
  }
}
