/*
 * Copyright Besu contributors.
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
package org.hyperledger.besu.ethereum.eth.transactions.sorter;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.core.Transaction;

import java.util.HashMap;
import java.util.Map;
import java.util.NavigableSet;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class LowestInvalidNonceCache {
  private static final Logger LOG = LoggerFactory.getLogger(LowestInvalidNonceCache.class);

  private final int maxSize;
  private final Map<Address, InvalidNonceStatus> lowestInvalidKnownNonceBySender;
  private final NavigableSet<InvalidNonceStatus> evictionOrder = new TreeSet<>();

  public LowestInvalidNonceCache(final int maxSize) {
    this.maxSize = maxSize;
    this.lowestInvalidKnownNonceBySender = new HashMap<>(maxSize);
  }

  synchronized long registerInvalidTransaction(final Transaction transaction) {
    final Address sender = transaction.getSender();
    final long invalidNonce = transaction.getNonce();
    final InvalidNonceStatus currStatus = lowestInvalidKnownNonceBySender.get(sender);
    if (currStatus == null) {
      final InvalidNonceStatus newStatus = new InvalidNonceStatus(sender, invalidNonce);
      addInvalidNonceStatus(newStatus);
      LOG.trace("Added invalid nonce status {}, cache status {}", newStatus, this);
      return invalidNonce;
    }

    updateInvalidNonceStatus(
        currStatus,
        status -> {
          if (invalidNonce < currStatus.nonce) {
            currStatus.updateNonce(invalidNonce);
          } else {
            currStatus.newHit();
          }
        });
    LOG.trace("Updated invalid nonce status {}, cache status {}", currStatus, this);

    return currStatus.nonce;
  }

  synchronized void registerValidTransaction(final Transaction transaction) {
    final InvalidNonceStatus currStatus =
        lowestInvalidKnownNonceBySender.get(transaction.getSender());
    if (currStatus != null) {
      evictionOrder.remove(currStatus);
      lowestInvalidKnownNonceBySender.remove(transaction.getSender());
      LOG.trace(
          "Valid transaction, removed invalid nonce status {}, cache status {}", currStatus, this);
    }
  }

  synchronized boolean hasInvalidLowerNonce(final Transaction transaction) {
    final InvalidNonceStatus currStatus =
        lowestInvalidKnownNonceBySender.get(transaction.getSender());
    if (currStatus != null && transaction.getNonce() > currStatus.nonce) {
      updateInvalidNonceStatus(currStatus, status -> status.newHit());
      LOG.trace("New hit for invalid nonce status {}, cache status {}", currStatus, this);
      return true;
    }
    return false;
  }

  private void updateInvalidNonceStatus(
      final InvalidNonceStatus status, final Consumer<InvalidNonceStatus> updateAction) {
    evictionOrder.remove(status);
    updateAction.accept(status);
    evictionOrder.add(status);
  }

  private void addInvalidNonceStatus(final InvalidNonceStatus newStatus) {
    if (lowestInvalidKnownNonceBySender.size() >= maxSize) {
      final InvalidNonceStatus statusToEvict = evictionOrder.pollFirst();
      lowestInvalidKnownNonceBySender.remove(statusToEvict.address);
      LOG.trace("Evicted invalid nonce status {}, cache status {}", statusToEvict, this);
    }
    lowestInvalidKnownNonceBySender.put(newStatus.address, newStatus);
    evictionOrder.add(newStatus);
  }

  synchronized String toTraceLog() {
    return "by eviction order "
        + StreamSupport.stream(evictionOrder.spliterator(), false)
            .map(InvalidNonceStatus::toString)
            .collect(Collectors.joining("; "));
  }

  @Override
  public String toString() {
    return "LowestInvalidNonceCache{"
        + "maxSize: "
        + maxSize
        + ", currentSize: "
        + lowestInvalidKnownNonceBySender.size()
        + ", evictionOrder: [size: "
        + evictionOrder.size()
        + ", first evictable: "
        + evictionOrder.first()
        + "]"
        + '}';
  }

  public void reset() {
    lowestInvalidKnownNonceBySender.clear();
    evictionOrder.clear();
  }

  private static class InvalidNonceStatus implements Comparable<InvalidNonceStatus> {

    final Address address;
    long nonce;
    long hits;
    long lastUpdate;

    InvalidNonceStatus(final Address address, final long nonce) {
      this.address = address;
      this.nonce = nonce;
      this.hits = 1L;
      this.lastUpdate = System.currentTimeMillis();
    }

    void updateNonce(final long nonce) {
      this.nonce = nonce;
      newHit();
    }

    void newHit() {
      this.hits++;
      this.lastUpdate = System.currentTimeMillis();
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }

      InvalidNonceStatus that = (InvalidNonceStatus) o;

      return address.equals(that.address);
    }

    @Override
    public int hashCode() {
      return address.hashCode();
    }

    /**
     * An InvalidNonceStatus is smaller than another when it has fewer hits and was last access
     * earlier, the address is the last tiebreaker
     *
     * @param o the object to be compared.
     * @return 0 if they are equal, negative if this is smaller, positive if this is greater
     */
    @Override
    public int compareTo(final InvalidNonceStatus o) {
      final int cmpHits = Long.compare(this.hits, o.hits);
      if (cmpHits != 0) {
        return cmpHits;
      }
      final int cmpLastUpdate = Long.compare(this.lastUpdate, o.lastUpdate);
      if (cmpLastUpdate != 0) {
        return cmpLastUpdate;
      }
      return this.address.compareTo(o.address);
    }

    @Override
    public String toString() {
      return "{"
          + "address="
          + address
          + ", nonce="
          + nonce
          + ", hits="
          + hits
          + ", lastUpdate="
          + lastUpdate
          + '}';
    }
  }
}
