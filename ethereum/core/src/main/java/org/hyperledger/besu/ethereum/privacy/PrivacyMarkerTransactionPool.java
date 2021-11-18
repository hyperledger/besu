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

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.google.common.annotations.VisibleForTesting;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class PrivacyMarkerTransactionPool implements BlockAddedObserver {
  private static final Logger LOG = LogManager.getLogger();
  private final Map<Hash, PrivacyMarkerTransactionTracker> pmtPool;

  public PrivacyMarkerTransactionPool(final Blockchain blockchain) {
    this.pmtPool = new HashMap<>();
    blockchain.observeBlockAdded(this);
  }

  @Override
  public void onBlockAdded(final BlockAddedEvent event) {
    // remove from the map any transactions that went into this block
    // TODO take into account re-orgs
    LOG.info("onBlockAdded {}", event.getAddedTransactions());
    LOG.info("tx0.hash {}", event.getAddedTransactions().get(0).getHash());
    event
        .getAddedTransactions()
        .forEach(
            tx -> {
              LOG.debug("block has added tx " + tx.getHash());
              LOG.debug("setting isActive=false " + pmtPool.containsKey(tx.getHash()));
              PrivacyMarkerTransactionTracker tracker = pmtPool.get(tx.getHash());
              if (tracker != null) {
                tracker.setActive(false);
              }
            });
  }

  @VisibleForTesting
  public Optional<PrivacyMarkerTransactionTracker> getTransactionByHash(
      final Hash transactionHash, final boolean onlyActive) {
    if (pmtPool.containsKey(transactionHash)) {
      PrivacyMarkerTransactionTracker tracker = pmtPool.get(transactionHash);
      if (!onlyActive || tracker.isActive) {
        return Optional.of(tracker);
      }
    }
    return Optional.empty();
  }

  public Optional<Long> getMaxMatchingNonce(final String sender, final String privacyGroupId) {

    return pmtPool.values().stream()
        .filter(
            tracker ->
                tracker.getSender().equals(sender)
                    && tracker.getPrivacyGroupIdBase64().equals(privacyGroupId)
                    && tracker.isActive)
        .map(tracker -> tracker.getPrivateNonce())
        .max(Long::compare);
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
            sender, privacyGroupId, privateNonce, publicNonce, gasPrice);
    pmtPool.put(pmtHash, pmtTracker);
    LOG.debug("adding: {} pmtHash: {} ", pmtTracker, pmtHash);
    return pmtHash;
  }

  @VisibleForTesting
  protected Hash addPmtTransactionTracker(
      final Hash pmtHash, final PrivacyMarkerTransactionTracker pmtTracker) {

    pmtPool.put(pmtHash, pmtTracker);
    LOG.debug("adding: {} pmtHash: {} ", pmtTracker, pmtHash);
    return pmtHash;
  }

  public long getActiveCount() {
    return pmtPool.values().stream().filter(tx -> tx.isActive).count();
  }

  protected static class PrivacyMarkerTransactionTracker {
    private final String sender;
    private final String privacyGroupIdBase64;
    private final long privateNonce;
    private final long publicNonce;
    private final Optional<Wei> gasPrice;
    // whether the tracker should be considered when calculating nonce. Set to false when tx is
    // added to a block.
    private boolean isActive = true;

    protected PrivacyMarkerTransactionTracker(
        final String sender,
        final String privacyGroupIdBase64,
        final long privateNonce,
        final long publicNonce,
        final Optional<Wei> gasPrice) {
      this.sender = sender;
      this.privacyGroupIdBase64 = privacyGroupIdBase64;
      this.privateNonce = privateNonce;
      this.publicNonce = publicNonce;
      this.gasPrice = gasPrice;
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

    @Override
    public String toString() {
      final StringBuilder sb = new StringBuilder();
      sb.append("PrivateMarkerTransactionTracker ").append("{");
      sb.append("private nonce=").append(getPrivateNonce()).append(", ");
      sb.append("public nonce=").append(getPublicNonce()).append(", ");
      sb.append("sender=").append(getSender()).append(", ");
      sb.append("privacyGroupId=").append(getPrivacyGroupIdBase64()).append(", ");
      sb.append("gasPrice=").append(getGasPrice()).append(", "); // TODO optional
      return sb.append("}").toString();
    }
  }
}
