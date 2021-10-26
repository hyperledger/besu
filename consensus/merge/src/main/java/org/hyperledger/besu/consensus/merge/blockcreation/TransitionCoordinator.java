/*
 * Copyright ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.consensus.merge.blockcreation;

import org.hyperledger.besu.consensus.merge.MergeBlockProcessor.CandidateBlock;
import org.hyperledger.besu.consensus.merge.TransitionUtils;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.PayloadIdentifier;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.blockcreation.MiningCoordinator;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Transaction;

import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

public class TransitionCoordinator extends TransitionUtils<MiningCoordinator>
    implements MergeMiningCoordinator {

  private final MiningCoordinator miningCoordinator;
  private final MergeMiningCoordinator mergeCoordinator;

  public TransitionCoordinator(
      final MiningCoordinator miningCoordinator, final MiningCoordinator mergeCoordinator) {
    super(miningCoordinator, mergeCoordinator);
    this.miningCoordinator = miningCoordinator;
    this.mergeCoordinator = (MergeMiningCoordinator) mergeCoordinator;
  }

  @Override
  public void start() {
    miningCoordinator.start();
  }

  @Override
  public void stop() {
    miningCoordinator.stop();
  }

  @Override
  public void awaitStop() throws InterruptedException {
    miningCoordinator.awaitStop();
  }

  @Override
  public boolean enable() {
    return dispatchFunctionAccordingToMergeState(MiningCoordinator::enable);
  }

  @Override
  public boolean disable() {
    return dispatchFunctionAccordingToMergeState(MiningCoordinator::disable);
  }

  @Override
  public boolean isMining() {
    return dispatchFunctionAccordingToMergeState(MiningCoordinator::isMining);
  }

  @Override
  public Wei getMinTransactionGasPrice() {
    return dispatchFunctionAccordingToMergeState(MiningCoordinator::getMinTransactionGasPrice);
  }

  @Override
  public void setExtraData(final Bytes extraData) {
    miningCoordinator.setExtraData(extraData);
    mergeCoordinator.setExtraData(extraData);
  }

  @Override
  public Optional<Address> getCoinbase() {
    return dispatchFunctionAccordingToMergeState(MiningCoordinator::getCoinbase);
  }

  @Override
  public Optional<Block> createBlock(
      final BlockHeader parentHeader,
      final List<Transaction> transactions,
      final List<BlockHeader> ommers) {
    return dispatchFunctionAccordingToMergeState(
        (MiningCoordinator coordinator) ->
            miningCoordinator.createBlock(parentHeader, transactions, ommers));
  }

  @Override
  public Optional<Block> createBlock(final BlockHeader parentHeader, final long timestamp) {
    return dispatchFunctionAccordingToMergeState(
        (MiningCoordinator coordinator) -> coordinator.createBlock(parentHeader, timestamp));
  }

  @Override
  public void changeTargetGasLimit(final Long targetGasLimit) {
    miningCoordinator.changeTargetGasLimit(targetGasLimit);
    mergeCoordinator.changeTargetGasLimit(targetGasLimit);
  }

  @Override
  public PayloadIdentifier preparePayload(
      final BlockHeader parentHeader,
      final Long timestamp,
      final Bytes32 random,
      final Address feeRecipient) {
    return mergeCoordinator.preparePayload(parentHeader, timestamp, random, feeRecipient);
  }

  @Override
  public boolean validateProcessAndSetAsCandidate(final Block block) {
    return mergeCoordinator.validateProcessAndSetAsCandidate(block);
  }

  @Override
  public CandidateBlock setExistingAsCandidate(final Block block) {
    return mergeCoordinator.setExistingAsCandidate(block);
  }
}
