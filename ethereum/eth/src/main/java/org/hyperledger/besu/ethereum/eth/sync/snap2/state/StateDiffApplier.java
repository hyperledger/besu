/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.ethereum.eth.sync.snap2.state;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.eth.sync.snap2.state.StateDiff.AccountChange;
import org.hyperledger.besu.ethereum.eth.sync.snap2.storage.Accounts;
import org.hyperledger.besu.ethereum.eth.sync.snap2.storage.SyncStateStorage;
import org.hyperledger.besu.ethereum.trie.common.PatriciaTrieAccountValue;

import java.util.Map;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;

/**
 * Applies a {@link StateDiff} to the flat state, restricted to what the flat state holds: accounts
 * outside the persisted ranges are skipped, as are slots outside the persisted slot ranges of
 * accounts whose storage is still downloading. Those will be downloaded at a newer pivot.
 */
public final class StateDiffApplier {

  private static final int WRITES_PER_COMMIT = 50_000;

  private StateDiffApplier() {}

  /**
   * Counters of an application.
   *
   * @param applied accounts written
   * @param skipped accounts outside the persisted ranges
   * @param deleted accounts removed because they ended up empty
   * @param slots slots written or removed
   * @param staleStorage pending storages whose root changed
   */
  public record Outcome(int applied, int skipped, int deleted, long slots, int staleStorage) {}

  /** Receives the slots a move changed in a downloaded storage, with the root before. */
  @FunctionalInterface
  public interface StorageChanges {
    void changed(Bytes32 accountHash, Hash previousRoot, Map<Bytes32, UInt256> slots);
  }

  /** Applies the diff and commits it. */
  public static Outcome apply(
      final StateDiff diff,
      final SyncStateStorage storage,
      final StateCoverage coverage,
      final StorageChanges storageChanges) {
    SyncStateStorage.Batch batch = storage.batch();
    long writes = 0;
    int applied = 0;
    int skipped = 0;
    int deleted = 0;
    long slots = 0;
    int stale = 0;
    for (final AccountChange change : diff.accounts().values()) {
      if (writes >= WRITES_PER_COMMIT) {
        batch.commit();
        batch = storage.batch();
        writes = 0;
      }
      final Bytes32 accountHash = change.accountHash();
      if (!coverage.accounts().contains(accountHash)) {
        skipped++;
        continue;
      }
      final Optional<PatriciaTrieAccountValue> existing =
          storage.getAccount(accountHash).map(Accounts::decode);
      final StorageTask task = coverage.storageTask(accountHash);
      PatriciaTrieAccountValue updated = updated(existing, change);
      final boolean downloadedStorage = !change.storage().isEmpty() && task == null;
      if (downloadedStorage) {
        // unknown until its trie gets the change, or the final build makes it
        updated = Accounts.withStorageRoot(updated, Accounts.UNKNOWN_STORAGE_ROOT);
      }

      if (change.code().isPresent() && !change.code().get().isEmpty()) {
        batch.putCode(Bytes32.wrap(updated.getCodeHash().getBytes()), change.code().get());
      }
      if (Accounts.isEmpty(updated)) {
        // EIP-161 and EIP-7523: an empty account does not exist
        if (existing.isPresent()) {
          batch.removeAccount(accountHash);
          storage.deleteStorage(accountHash);
          deleted++;
          writes++;
        }
        coverage.removeStorageTask(accountHash);
        continue;
      }
      batch.putAccount(accountHash, Accounts.encode(updated));
      applied++;
      writes++;
      if (downloadedStorage) {
        storageChanges.changed(
            accountHash,
            existing.map(PatriciaTrieAccountValue::getStorageRoot).orElse(Hash.EMPTY_TRIE_HASH),
            change.storage());
      }

      if (!change.storage().isEmpty()) {
        if (task != null) {
          task.markRootStale();
          stale++;
        }
        for (final Map.Entry<Bytes32, UInt256> slot : change.storage().entrySet()) {
          // a new account or a fully downloaded storage holds every slot
          if (existing.isPresent() && task != null && !task.covers(slot.getKey())) {
            continue;
          }
          if (slot.getValue().isZero()) {
            batch.removeStorage(accountHash, slot.getKey());
          } else {
            batch.putStorage(accountHash, slot.getKey(), slot.getValue());
          }
          slots++;
          writes++;
        }
      }
    }
    batch.commit();
    return new Outcome(applied, skipped, deleted, slots, stale);
  }

  // the changed fields of an account; the storage root is left as it was
  private static PatriciaTrieAccountValue updated(
      final Optional<PatriciaTrieAccountValue> existing, final AccountChange change) {
    final long nonce =
        change.nonce().orElse(existing.map(PatriciaTrieAccountValue::getNonce).orElse(0L));
    final Wei balance =
        change
            .balance()
            .orElse(existing.map(PatriciaTrieAccountValue::getBalance).orElse(Wei.ZERO));
    final Hash codeHash =
        change
            .code()
            .map(code -> code.isEmpty() ? Hash.EMPTY : Hash.hash(code))
            .orElse(existing.map(PatriciaTrieAccountValue::getCodeHash).orElse(Hash.EMPTY));
    final Hash storageRoot =
        existing.map(PatriciaTrieAccountValue::getStorageRoot).orElse(Hash.EMPTY_TRIE_HASH);
    return new PatriciaTrieAccountValue(nonce, balance, storageRoot, codeHash);
  }
}
