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
import org.hyperledger.besu.ethereum.chain.BlockAddedEvent;
import org.hyperledger.besu.ethereum.chain.BlockAddedObserver;
import org.hyperledger.besu.ethereum.chain.Blockchain;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class PrivateMarkerTransactionPool implements BlockAddedObserver {
  private static final Logger LOG = LogManager.getLogger();
  private final Map<Hash, PrivateMarkerTransactionTracker> pmtPool;

  public PrivateMarkerTransactionPool(final Blockchain blockchain) {
    this.pmtPool = new HashMap<>();
    blockchain.observeBlockAdded(this);
  }

  @Override
  public void onBlockAdded(final BlockAddedEvent event) {
    // remove from the map any transactions that went into this block
    // TODO take into account re-orgs
    event
        .getAddedTransactions()
        .forEach(
            tx -> {
              LOG.debug("removing " + pmtPool.containsKey(tx.getHash()));
              pmtPool.remove(tx.getHash());
            });
  }

  public Optional<Long> getMaxMatchingNonce(final String sender, final String privacyGroupId) {

    return pmtPool.values().stream()
        .filter(
            tracker ->
                tracker.getSender().equals(sender)
                    && tracker.getPrivacyGroupIdBase64().equals(privacyGroupId))
        .map(tracker -> tracker.getPrivateNonce())
        .max(Long::compare);
  }

  public Hash addPmtTransactionTracker(
      final Hash pmtHash,
      final PrivateTransaction privateTx,
      final String privacyGroupId,
      final long publicNonce) {
    return addPmtTransactionTracker(
        pmtHash, privateTx.sender.toHexString(), privacyGroupId, privateTx.getNonce(), publicNonce);
  }

  public Hash addPmtTransactionTracker(
      final Hash pmtHash,
      final String sender,
      final String privacyGroupId,
      final long privateNonce,
      final long publicNonce) {

    final PrivateMarkerTransactionTracker pmtTracker =
        new PrivateMarkerTransactionTracker(sender, privacyGroupId, privateNonce, publicNonce);
    pmtPool.put(pmtHash, pmtTracker);
    LOG.debug("adding tracker: {} pmtHash: {} ", pmtTracker, pmtHash);
    return pmtHash;
  }

  protected static class PrivateMarkerTransactionTracker {
    private final String sender;
    private final String privacyGroupIdBase64;
    private final long privateNonce;
    private final long publicNonce;

    protected PrivateMarkerTransactionTracker(
        final String sender,
        final String privacyGroupIdBase64,
        final long privateNonce,
        final long publicNonce) {
      this.sender = sender;
      this.privacyGroupIdBase64 = privacyGroupIdBase64;
      this.privateNonce = privateNonce;
      this.publicNonce = publicNonce;
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

    @Override
    public String toString() {
      final StringBuilder sb = new StringBuilder();
      sb.append("PrivateMarkerTransactionTracker ").append("{");
      sb.append("private nonce=").append(getPrivateNonce()).append(", ");
      sb.append("public nonce=").append(getPublicNonce()).append(", ");
      sb.append("sender=").append(getSender()).append(", ");
      sb.append("privacyGroupId=").append(getPrivacyGroupIdBase64()).append(", ");
      return sb.append("}").toString();
    }
  }
}
