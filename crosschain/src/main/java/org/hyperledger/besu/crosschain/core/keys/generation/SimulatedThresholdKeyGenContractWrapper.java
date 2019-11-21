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
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.util.bytes.Bytes32;

import java.math.BigInteger;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Wraps the test version of the Threshold Key Generation Contract. */
public class SimulatedThresholdKeyGenContractWrapper implements ThresholdKeyGenContractInterface {
  private static final int DEFAULT_ROUND_DURATION = 5;

  private SimulatedThresholdKeyGenContract keyGen = new SimulatedThresholdKeyGenContract();

  protected static final Logger LOG = LogManager.getLogger();

  // TODO: All of this will be needed when exexuting transactions and views on the key gen contract.
  //  TransactionSimulator transactionSimulator;
  //  TransactionPool transactionPool;
  //  SECP256K1.KeyPair nodeKeys;
  //  Blockchain blockchain;
  //  WorldStateArchive worldStateArchive;
  //  int sidechainId;

  private BigInteger msgSender;

  //  Vertx vertx;

  // TODO will need to take a parameter: the address of the deployed contract.
  public SimulatedThresholdKeyGenContractWrapper() {}

  @Override
  public void init(
      //      final TransactionSimulator transactionSimulator,
      //      final TransactionPool transactionPool,
      //      final BigInteger sidechainId,
      final SECP256K1.KeyPair nodeKeys
      //      final Blockchain blockchain,
      //      final WorldStateArchive worldStateArchive
      ) {
    //    this.transactionSimulator = transactionSimulator;
    //    this.transactionPool = transactionPool;
    //    this.sidechainId = sidechainId;
    //    this.nodeKeys = nodeKeys;
    //    this.blockchain = blockchain;
    //    this.worldStateArchive = worldStateArchive;

    // Just have something for the simulator based on the node public key.
    this.msgSender =
        new BigInteger(Address.extract(nodeKeys.getPublicKey()).toUnprefixedString(), 16);

    //    this.vertx = Vertx.vertx();
  }

  @Override
  public void startNewKeyGeneration(final long version, final int threshold) {
    startNewKeyGeneration(version, threshold, DEFAULT_ROUND_DURATION);
  }

  @Override
  public void startNewKeyGeneration(
      final long version, final int threshold, final int roundDurationInBlocks) {
    // TODO when this is implemented for real, submit http request via vertx.
    // TODO indicate a time-out for the overall key generation process
    this.keyGen.startNewKeyGeneration(version, this.msgSender, threshold, roundDurationInBlocks);
  }

  @Override
  public void setNodeId(final long version) {
    this.keyGen.setNodeId(version, this.msgSender);
  }

  @Override
  public void setNodeId(final long version, final BigInteger msgSender) {
    this.keyGen.setNodeId(version, msgSender);
  }

  @Override
  public void setNodeCoefficientsCommitments(
      final long version, final Bytes32[] coefPublicPointCommitments) {
    this.keyGen.setNodeCoefficientsCommitments(version, this.msgSender, coefPublicPointCommitments);
  }

  @Override
  public void setNodeCoefficientsCommitments(
      final long version, final BigInteger msgSender, final Bytes32[] coefPublicPointCommitments) {
    this.keyGen.setNodeCoefficientsCommitments(version, msgSender, coefPublicPointCommitments);
  }

  @Override
  public void setNodeCoefficientsPublicValues(
      final long version, final BlsPoint[] coefPublicPoints) {
    this.keyGen.setNodeCoefficientsPublicValues(version, this.msgSender, coefPublicPoints);
  }

  @Override
  public void setNodeCoefficientsPublicValues(
      final long version, final BigInteger msgSender, final BlsPoint[] coefPublicPoints) {
    this.keyGen.setNodeCoefficientsPublicValues(version, msgSender, coefPublicPoints);
  }

  @Override
  public long getExpectedKeyGenerationVersion() {
    return this.keyGen.getExpectedKeyGenerationVersion();
  }

  @Override
  public int getThreshold(final long version) {
    return this.keyGen.getThreshold(version);
  }

  @Override
  public int getNumberOfNodes(final long version) {
    return this.keyGen.getNumberOfNodes(version);
  }

  @Override
  public BigInteger getNodeAddress(final long version, final int index) {
    return this.keyGen.getNodeAddress(version, index);
  }

  @Override
  public BlsPoint getCoefficientPublicValue(
      final long version, final BigInteger fromAddress, final int coefNumber) {
    return this.keyGen.getCoefficientPublicValue(version, fromAddress, coefNumber);
  }
}
