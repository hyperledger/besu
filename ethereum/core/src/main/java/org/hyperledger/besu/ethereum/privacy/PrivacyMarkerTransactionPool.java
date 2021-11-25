/*
 * Copyright contributors to Hyperledger Besu.
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

package org.hyperledger.besu.ethereum.privacy;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.chain.BlockAddedEvent;
import org.hyperledger.besu.ethereum.chain.BlockAddedObserver;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.plugin.data.Transaction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.google.common.annotations.VisibleForTesting;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class PrivacyMarkerTransactionPool implements BlockAddedObserver {
  private static final Logger LOG = LogManager.getLogger();
  private final Map<Hash, PrivacyMarkerTransactionTracker> pmtTrackersByHash;

  public PrivacyMarkerTransactionPool(final Blockchain blockchain) {
    this.pmtTrackersByHash = new HashMap<>();
    blockchain.observeBlockAdded(this);
  }

  @Override
  public void onBlockAdded(final BlockAddedEvent event) {
    final long blockNumber = event.getBlock().getHeader().getNumber();
    event.getAddedTransactions().forEach(tx -> transactionAddedToBlock(tx, blockNumber));
    event.getRemovedTransactions().forEach(this::transactionRemovedFromBlockByReorg);
    // cleanup periodically
    if (blockNumber % 10 == 0) {
      cleanupPmtPool(blockNumber, 6);
    }
  }

  private void transactionAddedToBlock(final Transaction tx, final long blockNumber) {
    final PrivacyMarkerTransactionTracker tracker = pmtTrackersByHash.get(tx.getHash());
    if (tracker != null) {
      tracker.setActive(false);
      tracker.setBlockNumber(blockNumber);
    }
  }

  private void transactionRemovedFromBlockByReorg(final Transaction tx) {
    final PrivacyMarkerTransactionTracker tracker = pmtTrackersByHash.get(tx.getHash());
    if (tracker != null) {
      tracker.setActive(true);
    }
  }

  @VisibleForTesting
  public Optional<PrivacyMarkerTransactionTracker> getTransactionByHash(
      final Hash transactionHash, final boolean onlyActive) {
    if (pmtTrackersByHash.containsKey(transactionHash)) {
      PrivacyMarkerTransactionTracker tracker = pmtTrackersByHash.get(transactionHash);
      if (!onlyActive || tracker.isActive) {
        return Optional.of(tracker);
      }
    }
    return Optional.empty();
  }

  public Optional<Long> getMaxMatchingNonce(final String sender, final String privacyGroupId) {
    return pmtTrackersByHash.values().stream()
        .filter(
            tracker ->
                tracker.isActive
                    && tracker.getSender().equals(sender)
                    && tracker.getPrivacyGroupIdBase64().equals(privacyGroupId))
        .map(tracker -> tracker.getPrivateNonce())
        .max(Long::compare);
  }

  @VisibleForTesting
  protected void cleanupPmtPool(final long blockNumber, final int numberOfBlocksClearance) {
    // tx that were added more than numberOfBlocksClearance blocks ago can be totally removed
    final List<Hash> hashesToRemove = new ArrayList<>();
    pmtTrackersByHash.values().stream()
        .filter(
            tracker ->
                tracker.getBlockNumber().isPresent()
                    && tracker.getBlockNumber().get() + numberOfBlocksClearance <= blockNumber)
        .forEach(
            tx -> {
              hashesToRemove.add(tx.getHash());
            });
    hashesToRemove.forEach(hash -> pmtTrackersByHash.remove(hash));
  }

  public Hash addPmtTransactionTracker(
      final Hash pmtHash,
      final PrivateTransaction privateTx,
      final String privacyGroupId,
      final long publicNonce,
      final Optional<Wei> gasPrice) {
    return addPmtTransactionTracker(
        pmtHash,
        privateTx.sender.toHexString(),
        privacyGroupId,
        privateTx.getNonce(),
        publicNonce,
        gasPrice);
  }

  public Hash addPmtTransactionTracker(
      final Hash pmtHash,
      final String sender,
      final String privacyGroupId,
      final long privateNonce,
      final long publicNonce,
      final Optional<Wei> gasPrice) {

    final PrivacyMarkerTransactionTracker pmtTracker =
        new PrivacyMarkerTransactionTracker(
            pmtHash, sender, privacyGroupId, privateNonce, publicNonce, gasPrice);
    return addPmtTransactionTracker(pmtTracker);
  }

  @VisibleForTesting
  protected Hash addPmtTransactionTracker(final PrivacyMarkerTransactionTracker pmtTracker) {

    pmtTrackersByHash.put(pmtTracker.getHash(), pmtTracker);
    LOG.debug("adding: {} pmtHash: {} ", pmtTracker, pmtTracker.getHash());
    return pmtTracker.getHash();
  }

  public long getTotalCount() {
    return pmtTrackersByHash.values().stream().count();
  }

  public long getActiveCount() {
    return pmtTrackersByHash.values().stream().filter(tx -> tx.isActive).count();
  }

  protected static class PrivacyMarkerTransactionTracker {
    private final Hash hash;
    private final String sender;
    private final String privacyGroupIdBase64;
    private final long privateNonce;
    private final long publicNonce;
    private final Optional<Wei> gasPrice;
    // whether the tracker should be considered when calculating nonce. Set to false when tx is
    // added to a block, and set to true if it's removed by a reorg.
    private boolean isActive = true;
    private Optional<Long> blockNumber;

    protected PrivacyMarkerTransactionTracker(
        final Hash hash,
        final String sender,
        final String privacyGroupIdBase64,
        final long privateNonce,
        final long publicNonce,
        final Optional<Wei> gasPrice) {
      this.hash = hash;
      this.sender = sender;
      this.privacyGroupIdBase64 = privacyGroupIdBase64;
      this.privateNonce = privateNonce;
      this.publicNonce = publicNonce;
      this.gasPrice = gasPrice;
    }

    public Hash getHash() {
      return hash;
    }

    public String getSender() {
      return sender;
    }

    public String getPrivacyGroupIdBase64() {
      return privacyGroupIdBase64;
    }

    public long getPrivateNonce() {
      return privateNonce;
    }

    public long getPublicNonce() {
      return publicNonce;
    }

    public Optional<Wei> getGasPrice() {
      return gasPrice;
    }

    public boolean isActive() {
      return isActive;
    }

    private void setActive(final boolean isActive) {
      this.isActive = isActive;
    }

    private void setBlockNumber(final long blockNumber) {
      this.blockNumber = Optional.of(blockNumber);
    }

    private Optional<Long> getBlockNumber() {
      return blockNumber;
    }

    @Override
    public String toString() {
      final StringBuilder sb = new StringBuilder();
      sb.append("PrivateMarkerTransactionTracker ").append("{");
      sb.append("private nonce=").append(getPrivateNonce()).append(", ");
      sb.append("public nonce=").append(getPublicNonce()).append(", ");
      sb.append("hash=").append(getHash()).append(", ");
      sb.append("sender=").append(getSender()).append(", ");
      sb.append("privacyGroupId=").append(getPrivacyGroupIdBase64()).append(", ");
      sb.append("gasPrice=").append(getGasPrice());
      return sb.append("}").toString();
    }
  }
}
