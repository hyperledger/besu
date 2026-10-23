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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.hyperledger.besu.ethereum.eth.sync.snap2.storage.SyncStorageFixture.slots;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.StorageSlotKey;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.eth.sync.snap2.storage.Accounts;
import org.hyperledger.besu.ethereum.eth.sync.snap2.storage.SyncStateStorage;
import org.hyperledger.besu.ethereum.eth.sync.snap2.storage.SyncStorageFixture;
import org.hyperledger.besu.ethereum.eth.sync.snap2.storage.SyncStorageFixture.Format;
import org.hyperledger.besu.ethereum.mainnet.block.access.list.BlockAccessList;
import org.hyperledger.besu.ethereum.mainnet.block.access.list.BlockAccessList.AccountChanges;
import org.hyperledger.besu.ethereum.mainnet.block.access.list.BlockAccessList.BalanceChange;
import org.hyperledger.besu.ethereum.mainnet.block.access.list.BlockAccessList.CodeChange;
import org.hyperledger.besu.ethereum.mainnet.block.access.list.BlockAccessList.NonceChange;
import org.hyperledger.besu.ethereum.mainnet.block.access.list.BlockAccessList.SlotChanges;
import org.hyperledger.besu.ethereum.mainnet.block.access.list.BlockAccessList.StorageChange;
import org.hyperledger.besu.ethereum.trie.RangeManager;
import org.hyperledger.besu.ethereum.trie.common.PatriciaTrieAccountValue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class StateDiffApplierTest {

  private static final Address A = Address.fromHexString("0xaa");
  private static final Address B = Address.fromHexString("0xbb");
  private static final Address C = Address.fromHexString("0xcc");
  private static final Address D = Address.fromHexString("0xdd");
  private static final Address E = Address.fromHexString("0xe0");
  private static final Address N = Address.fromHexString("0xee");
  private static final UInt256 S1 = UInt256.valueOf(1);
  private static final UInt256 S2 = UInt256.valueOf(2);
  private static final Bytes CODE = Bytes.fromHexString("0x6001600155");

  @ParameterizedTest
  @EnumSource(Format.class)
  void appliesOnlyWhatTheFlatStateHolds(final Format format) {
    final SyncStateStorage storage = SyncStorageFixture.create(format).sync();
    final StateCoverage coverage = new StateCoverage();
    final SyncStateStorage.Batch batch = storage.batch();
    batch.putAccount(hash(A), account(1, 100, Hash.EMPTY));
    batch.putAccount(hash(B), account(1, 100, Hash.hash(CODE)));
    batch.putAccount(hash(D), account(0, 5, Hash.EMPTY));
    batch.commit();
    for (Address covered : List.of(A, B, D, N)) {
      coverage.accounts().add(hash(covered), hash(covered));
    }
    // B's storage is half downloaded: only the range holding S1 is persisted
    final StorageTask task = new StorageTask(hash(B), Bytes32.ZERO);
    final Bytes32 s1 = slotHash(S1);
    final Bytes32 s2 = slotHash(S2);
    if (s1.compareTo(s2) < 0) {
      task.cover(RangeManager.MIN_RANGE, s1, 0);
    } else {
      task.cover(s1, RangeManager.MAX_RANGE, 0);
    }
    coverage.addStorageTask(task);

    final StateDiff diff = new StateDiff();
    diff.add(
        new BlockAccessList(
            List.of(
                changes(
                    A, List.of(), List.of(new BalanceChange(1, Wei.of(50))), List.of(), List.of()),
                changes(B, List.of(slot(S1, 7), slot(S2, 8)), List.of(), List.of(), List.of()),
                changes(C, List.of(), List.of(), List.of(new NonceChange(1, 9)), List.of()),
                changes(
                    D, List.of(), List.of(new BalanceChange(1, Wei.ZERO)), List.of(), List.of()),
                // read but not written, so not part of the diff
                changes(E, List.of(), List.of(), List.of(), List.of()),
                changes(
                    N,
                    List.of(slot(S1, 3)),
                    List.of(new BalanceChange(1, Wei.of(1))),
                    List.of(new NonceChange(1, 1)),
                    List.of(new CodeChange(1, CODE))))));
    diff.add(
        new BlockAccessList(
            List.of(
                changes(
                    A,
                    List.of(),
                    List.of(new BalanceChange(2, Wei.of(42))),
                    List.of(),
                    List.of()))));

    final StateDiffApplier.Outcome outcome =
        StateDiffApplier.apply(diff, storage, coverage, (account, root, slots) -> {});

    // A, B and N written, C outside the ranges, D emptied, S1 of B and N, B's storage stale
    assertThat(outcome).isEqualTo(new StateDiffApplier.Outcome(3, 1, 1, 2, 1));
    assertThat(read(storage, A).getBalance()).isEqualTo(Wei.of(42));
    assertThat(read(storage, A).getNonce()).isEqualTo(1);
    assertThat(slots(storage, hash(B))).containsExactly(entry(s1, UInt256.valueOf(7)));
    assertThat(task.isRootStale()).isTrue();
    assertThat(storage.getAccount(hash(C))).isEmpty();
    assertThat(storage.getAccount(hash(D))).isEmpty();
    assertThat(read(storage, N).getCodeHash()).isEqualTo(Hash.hash(CODE));
    assertThat(slots(storage, hash(N))).containsExactly(entry(s1, UInt256.valueOf(3)));
    assertThat(storage.hasCode(Bytes32.wrap(Hash.hash(CODE).getBytes()))).isTrue();
  }

  @ParameterizedTest
  @EnumSource(Format.class)
  void handsTheChangedSlotsOfACompleteStorageToItsTrie(final Format format) {
    final SyncStateStorage storage = SyncStorageFixture.create(format).sync();
    final StateCoverage coverage = new StateCoverage();
    final Hash builtRoot = Hash.hash(Bytes.of(1));
    final SyncStateStorage.Batch batch = storage.batch();
    batch.putAccount(hash(A), account(1, 100, builtRoot, Hash.EMPTY));
    batch.putAccount(hash(B), account(1, 100, builtRoot, Hash.EMPTY));
    batch.commit();
    coverage.accounts().add(hash(A), hash(A));
    coverage.accounts().add(hash(B), hash(B));
    // A's storage is complete, its trie possibly built; B's is still downloading
    coverage.addStorageTask(new StorageTask(hash(B), Bytes32.wrap(builtRoot.getBytes())));

    final StateDiff diff = new StateDiff();
    diff.add(
        new BlockAccessList(
            List.of(
                changes(A, List.of(slot(S1, 7)), List.of(), List.of(), List.of()),
                changes(B, List.of(slot(S1, 7)), List.of(), List.of(), List.of()))));
    final Map<Bytes32, Hash> previousRoots = new HashMap<>();
    final Map<Bytes32, Map<Bytes32, UInt256>> changedSlots = new HashMap<>();
    StateDiffApplier.apply(
        diff,
        storage,
        coverage,
        (account, root, slots) -> {
          previousRoots.put(account, root);
          changedSlots.put(account, slots);
        });

    // A's trie gets the changed slots, its root being unknown until then
    assertThat(read(storage, A).getStorageRoot()).isEqualTo(Accounts.UNKNOWN_STORAGE_ROOT);
    assertThat(previousRoots).containsOnlyKeys(hash(A)).containsEntry(hash(A), builtRoot);
    assertThat(changedSlots.get(hash(A))).containsExactly(entry(slotHash(S1), UInt256.valueOf(7)));
    // the download learns B's new root instead
    assertThat(read(storage, B).getStorageRoot()).isEqualTo(builtRoot);
  }

  private static PatriciaTrieAccountValue read(final SyncStateStorage storage, final Address a) {
    return Accounts.decode(storage.getAccount(hash(a)).orElseThrow());
  }

  private static Bytes account(final long nonce, final long balance, final Hash codeHash) {
    return account(nonce, balance, Hash.EMPTY_TRIE_HASH, codeHash);
  }

  private static Bytes account(
      final long nonce, final long balance, final Hash storageRoot, final Hash codeHash) {
    return Accounts.encode(
        new PatriciaTrieAccountValue(nonce, Wei.of(balance), storageRoot, codeHash));
  }

  private static AccountChanges changes(
      final Address address,
      final List<SlotChanges> slots,
      final List<BalanceChange> balances,
      final List<NonceChange> nonces,
      final List<CodeChange> codes) {
    return new AccountChanges(address, slots, List.of(), balances, nonces, codes);
  }

  private static SlotChanges slot(final UInt256 key, final long value) {
    return new SlotChanges(
        new StorageSlotKey(key), List.of(new StorageChange(1, UInt256.valueOf(value))));
  }

  private static Bytes32 hash(final Address address) {
    return Bytes32.wrap(address.addressHash().getBytes());
  }

  private static Bytes32 slotHash(final UInt256 key) {
    return Bytes32.wrap(new StorageSlotKey(key).getSlotHash().getBytes());
  }
}
