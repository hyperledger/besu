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
package org.hyperledger.besu.ethereum.trie.diffbased.bonsai;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.Block;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class BonsaiSnapshotIsolationTests extends AbstractIsolationTests {

  @Test
  public void ensureTruncateDoesNotCauseSegfault() {

    var preTruncatedWorldState = archive.getMutable(genesisState.getBlock().getHeader(), false);
    assertThat(preTruncatedWorldState)
        .isPresent(); // really just assert that we have not segfaulted after truncating
    worldStateKeyValueStorage.clear();
    var postTruncatedWorldState = archive.getMutable(genesisState.getBlock().getHeader(), false);
    assertThat(postTruncatedWorldState).isEmpty();
    // assert that trying to access pre-worldstate does not segfault after truncating
    preTruncatedWorldState.get().get(accounts.get(0).address());
    assertThat(true).isTrue();
  }

  @Test
  public void testIsolatedFromHead_behindHead() {
    Address testAddress = Address.fromHexString("0xdeadbeef");
    // assert we can mutate head without mutating the isolated snapshot
    var isolated = archive.getMutable(genesisState.getBlock().getHeader(), false);

    var firstBlock = forTransactions(List.of(burnTransaction(sender1, 0L, testAddress)));
    var res = executeBlock(archive.getMutable(), firstBlock);

    var isolated2 = archive.getMutable(firstBlock.getHeader(), false);
    var secondBlock = forTransactions(List.of(burnTransaction(sender1, 1L, testAddress)));
    var res2 = executeBlock(archive.getMutable(), secondBlock);

    assertThat(res.isSuccessful()).isTrue();
    assertThat(res2.isSuccessful()).isTrue();

    assertThat(archive.getCachedWorldStorageManager().contains(firstBlock.getHash())).isTrue();
    assertThat(archive.getCachedWorldStorageManager().contains(secondBlock.getHash())).isTrue();

    assertThat(archive.getMutable().get(testAddress)).isNotNull();
    assertThat(archive.getMutable().get(testAddress).getBalance())
        .isEqualTo(Wei.of(2_000_000_000_000_000_000L));

    assertThat(isolated.get().get(testAddress)).isNull();
    assertThat(isolated.get().rootHash())
        .isEqualTo(genesisState.getBlock().getHeader().getStateRoot());

    assertThat(isolated2.get().get(testAddress)).isNotNull();
    assertThat(isolated2.get().get(testAddress).getBalance())
        .isEqualTo(Wei.of(1_000_000_000_000_000_000L));
    assertThat(isolated2.get().rootHash()).isEqualTo(firstBlock.getHeader().getStateRoot());

    try {
      isolated.get().close();
      isolated2.get().close();
    } catch (Exception ex) {
      throw new RuntimeException("failed to close isolated worldstates");
    }
  }

  @Test
  public void testSnapshotRollToTrieLogBlockHash() {
    // assert we can roll a snapshot to a specific worldstate without mutating head
    Address testAddress = Address.fromHexString("0xdeadbeef");

    var block1 = forTransactions(List.of(burnTransaction(sender1, 0L, testAddress)));
    var res = executeBlock(archive.getMutable(), block1);

    var block2 = forTransactions(List.of(burnTransaction(sender1, 1L, testAddress)));
    var res2 = executeBlock(archive.getMutable(), block2);

    var block3 = forTransactions(List.of(burnTransaction(sender1, 2L, testAddress)));
    var res3 = executeBlock(archive.getMutable(), block3);

    assertThat(res.isSuccessful()).isTrue();
    assertThat(res2.isSuccessful()).isTrue();
    assertThat(res3.isSuccessful()).isTrue();

    // roll chain and worldstate to block 2
    blockchain.rewindToBlock(2L);
    var block1State = archive.getMutable(null, block2.getHash());

    // BonsaiWorldState should be at block 2
    assertThat(block1State.get().get(testAddress)).isNotNull();
    assertThat(block1State.get().get(testAddress).getBalance())
        .isEqualTo(Wei.of(2_000_000_000_000_000_000L));
    assertThat(block1State.get().rootHash()).isEqualTo(block2.getHeader().getStateRoot());

    var isolatedRollForward = archive.getMutable(block3.getHeader(), false);

    // we should be at block 3, one block ahead of BonsaiPersistatedWorldState
    assertThat(isolatedRollForward.get().get(testAddress)).isNotNull();
    assertThat(isolatedRollForward.get().get(testAddress).getBalance())
        .isEqualTo(Wei.of(3_000_000_000_000_000_000L));
    assertThat(isolatedRollForward.get().rootHash()).isEqualTo(block3.getHeader().getStateRoot());

    // we should be at block 1, one block behind BonsaiPersistatedWorldState
    var isolatedRollBack = archive.getMutable(block1.getHeader(), false);
    assertThat(isolatedRollBack.get().get(testAddress)).isNotNull();
    assertThat(isolatedRollBack.get().get(testAddress).getBalance())
        .isEqualTo(Wei.of(1_000_000_000_000_000_000L));
    assertThat(isolatedRollBack.get().rootHash()).isEqualTo(block1.getHeader().getStateRoot());

    try {
      isolatedRollForward.get().close();
      isolatedRollBack.get().close();
    } catch (Exception ex) {
      throw new RuntimeException("failed to close isolated worldstates");
    }
  }

  @Test
  public void assertCloseDisposesOfStateWithoutCommitting() {
    Address testAddress = Address.fromHexString("0xdeadbeef");

    var head = archive.getMutable();

    try (var shouldCloseSnapshot =
        archive.getMutable(genesisState.getBlock().getHeader(), false).get()) {

      var tx1 = burnTransaction(sender1, 0L, testAddress);
      Block oneTx = forTransactions(List.of(tx1));

      var res = executeBlock(shouldCloseSnapshot, oneTx);
      assertThat(res.isSuccessful()).isTrue();
      shouldCloseSnapshot.persist(oneTx.getHeader());

      assertThat(shouldCloseSnapshot.get(testAddress)).isNotNull();
      assertThat(shouldCloseSnapshot.get(testAddress).getBalance())
          .isEqualTo(Wei.of(1_000_000_000_000_000_000L));

    } catch (Exception e) {
      // just a cheap way to close the snapshot worldstate and transactions
    }

    assertThat(head.get(testAddress)).isNull();
  }
}
