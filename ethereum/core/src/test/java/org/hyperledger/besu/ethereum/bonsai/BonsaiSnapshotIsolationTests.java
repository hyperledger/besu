/*
 * Copyright Hyperledger Besu contributors.
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
 *
 */

package org.hyperledger.besu.ethereum.bonsai;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.MutableWorldState;

import java.util.List;
import java.util.function.Consumer;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class BonsaiSnapshotIsolationTests extends AbstractIsolationTests {

  @Test
  public void ensureTruncateDoesNotCauseSegfault() {

    var preTruncatedWorldState = archive.getMutable(null, genesisState.getBlock().getHash(), false);
    assertThat(preTruncatedWorldState)
        .isPresent(); // really just assert that we have not segfaulted after truncating
    bonsaiWorldStateStorage.clear();
    var postTruncatedWorldState =
        archive.getMutable(null, genesisState.getBlock().getHash(), false);
    assertThat(postTruncatedWorldState).isEmpty();
    // assert that trying to access pre-worldstate does not segfault after truncating
    preTruncatedWorldState.get().get(Address.fromHexString(accounts.get(0).getAddress()));
    assertThat(true).isTrue();
  }

  @Test
  public void testIsolatedFromHead_behindHead() {
    Address testAddress = Address.fromHexString("0xdeadbeef");
    // assert we can mutate head without mutating the isolated snapshot
    var isolated = archive.getMutableSnapshot(genesisState.getBlock().getHash());

    var firstBlock = forTransactions(List.of(burnTransaction(sender1, 0L, testAddress)));
    var res = executeBlock(archive.getMutable(), firstBlock);

    var isolated2 = archive.getMutableSnapshot(firstBlock.getHash());
    var secondBlock = forTransactions(List.of(burnTransaction(sender1, 1L, testAddress)));
    var res2 = executeBlock(archive.getMutable(), secondBlock);

    assertThat(res.isSuccessful()).isTrue();
    assertThat(res2.isSuccessful()).isTrue();

    assertThat(archive.getTrieLogManager().getBonsaiCachedWorldState(firstBlock.getHash()))
        .isNotEmpty();
    assertThat(archive.getTrieLogManager().getBonsaiCachedWorldState(secondBlock.getHash()))
        .isNotEmpty();

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
  public void testIsolatedSnapshotMutation() {
    Address testAddress = Address.fromHexString("0xdeadbeef");
    // assert we can correctly execute a block on a mutable snapshot without mutating head
    var isolated = archive.getMutableSnapshot(genesisState.getBlock().getHash());

    var firstBlock = forTransactions(List.of(burnTransaction(sender1, 0L, testAddress)));
    var res = executeBlock(isolated.get(), firstBlock);

    assertThat(archive.getTrieLogManager().getBonsaiCachedWorldState(firstBlock.getHash()))
        .isNotEmpty();

    assertThat(res.isSuccessful()).isTrue();
    assertThat(isolated.get().get(testAddress)).isNotNull();
    assertThat(isolated.get().get(testAddress).getBalance())
        .isEqualTo(Wei.of(1_000_000_000_000_000_000L));
    assertThat(isolated.get().rootHash()).isEqualTo(firstBlock.getHeader().getStateRoot());

    // persist the isolated worldstate as trielog only:
    isolated.get().persist(firstBlock.getHeader());

    // assert we have not modified the head worldstate:
    assertThat(archive.getMutable().get(testAddress)).isNull();

    // roll the persisted world state to the new trie log from the persisted snapshot
    var ws = archive.getMutable(null, firstBlock.getHash());
    assertThat(ws).isPresent();
    assertThat(ws.get().get(testAddress)).isNotNull();
    assertThat(ws.get().get(testAddress).getBalance())
        .isEqualTo(Wei.of(1_000_000_000_000_000_000L));
    assertThat(ws.get().rootHash()).isEqualTo(firstBlock.getHeader().getStateRoot());
    try {
      isolated.get().close();
    } catch (Exception ex) {
      throw new RuntimeException("failed to close isolated worldstates");
    }
  }

  @Test
  public void testSnapshotCloneIsolation() {
    Address testAddress = Address.fromHexString("0xdeadbeef");
    Address altTestAddress = Address.fromHexString("0xd1ffbeef");

    // create a snapshot worldstate, and then clone it:
    var isolated = archive.getMutableSnapshot(genesisState.getBlock().getHash()).get();
    var isolatedClone = isolated.copy();

    // execute a block with a single transaction on the first snapshot:
    var firstBlock = forTransactions(List.of(burnTransaction(sender1, 0L, testAddress)));
    var res = executeBlock(isolated, firstBlock);

    assertThat(res.isSuccessful()).isTrue();
    Runnable checkIsolatedState =
        () -> {
          assertThat(isolated.rootHash()).isEqualTo(firstBlock.getHeader().getStateRoot());
          assertThat(isolated.get(testAddress)).isNotNull();
          assertThat(isolated.get(altTestAddress)).isNull();
          assertThat(isolated.get(testAddress).getBalance())
              .isEqualTo(Wei.of(1_000_000_000_000_000_000L));
        };
    checkIsolatedState.run();

    // assert clone is isolated and unmodified:
    assertThat(isolatedClone.get(testAddress)).isNull();
    assertThat(isolatedClone.rootHash())
        .isEqualTo(genesisState.getBlock().getHeader().getStateRoot());

    // assert clone isolated block execution
    var cloneForkBlock =
        forTransactions(
            List.of(burnTransaction(sender1, 0L, altTestAddress)),
            genesisState.getBlock().getHeader());
    var altRes = executeBlock(isolatedClone, cloneForkBlock);

    assertThat(altRes.isSuccessful()).isTrue();
    assertThat(isolatedClone.rootHash()).isEqualTo(cloneForkBlock.getHeader().getStateRoot());
    assertThat(isolatedClone.get(altTestAddress)).isNotNull();
    assertThat(isolatedClone.get(testAddress)).isNull();
    assertThat(isolatedClone.get(altTestAddress).getBalance())
        .isEqualTo(Wei.of(1_000_000_000_000_000_000L));
    assertThat(isolatedClone.rootHash()).isEqualTo(cloneForkBlock.getHeader().getStateRoot());

    // re-check isolated state remains unchanged:
    checkIsolatedState.run();

    // assert that the actual persisted worldstate remains unchanged:
    var persistedWorldState = archive.getMutable();
    assertThat(persistedWorldState.rootHash())
        .isEqualTo(genesisState.getBlock().getHeader().getStateRoot());
    assertThat(persistedWorldState.get(testAddress)).isNull();
    assertThat(persistedWorldState.get(altTestAddress)).isNull();

    // assert that trieloglayers exist for both of the isolated states:
    var firstBlockTrieLog = archive.getTrieLogManager().getTrieLogLayer(firstBlock.getHash());
    assertThat(firstBlockTrieLog).isNotEmpty();
    assertThat(firstBlockTrieLog.get().getAccount(testAddress)).isNotEmpty();
    assertThat(firstBlockTrieLog.get().getAccount(altTestAddress)).isEmpty();
    assertThat(archive.getTrieLogManager().getBonsaiCachedWorldState(firstBlock.getHash()))
        .isNotEmpty();

    var cloneForkTrieLog = archive.getTrieLogManager().getTrieLogLayer(cloneForkBlock.getHash());
    assertThat(cloneForkTrieLog.get().getAccount(testAddress)).isEmpty();
    assertThat(cloneForkTrieLog.get().getAccount(altTestAddress)).isNotEmpty();

    try {
      isolated.close();
      isolatedClone.close();
    } catch (Exception ex) {
      throw new RuntimeException("failed to close isolated worldstates");
    }
  }

  @Test
  public void assertSnapshotDoesNotClose() {
    Address testAddress = Address.fromHexString("0xdeadbeef");

    // create a snapshot worldstate, and then clone it:
    var isolated = archive.getMutableSnapshot(genesisState.getBlock().getHash()).get();

    // execute a block with a single transaction on the first snapshot:
    var firstBlock = forTransactions(List.of(burnTransaction(sender1, 0L, testAddress)));
    var res = executeBlock(isolated, firstBlock);
    assertThat(archive.getTrieLogManager().getBonsaiCachedWorldState(firstBlock.getHash()))
        .isNotEmpty();

    assertThat(res.isSuccessful()).isTrue();
    Consumer<MutableWorldState> checkIsolatedState =
        (ws) -> {
          assertThat(ws.rootHash()).isEqualTo(firstBlock.getHeader().getStateRoot());
          assertThat(ws.get(testAddress)).isNotNull();
          assertThat(ws.get(testAddress).getBalance())
              .isEqualTo(Wei.of(1_000_000_000_000_000_000L));
        };
    checkIsolatedState.accept(isolated);

    var isolatedClone = isolated.copy();
    checkIsolatedState.accept(isolatedClone);

    try {
      // close the first snapshot worldstate.  The second worldstate should still be able to read
      // through its snapshot
      isolated.close();
    } catch (Exception ex) {
      // meh
    }

    // copy of closed isolated worldstate should still pass check
    checkIsolatedState.accept(isolatedClone);

    try {
      isolatedClone.close();
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

    // BonsaiPersistedWorldState should be at block 2
    assertThat(block1State.get().get(testAddress)).isNotNull();
    assertThat(block1State.get().get(testAddress).getBalance())
        .isEqualTo(Wei.of(2_000_000_000_000_000_000L));
    assertThat(block1State.get().rootHash()).isEqualTo(block2.getHeader().getStateRoot());

    var isolatedRollForward = archive.getMutableSnapshot(block3.getHash());

    // we should be at block 3, one block ahead of BonsaiPersistatedWorldState
    assertThat(isolatedRollForward.get().get(testAddress)).isNotNull();
    assertThat(isolatedRollForward.get().get(testAddress).getBalance())
        .isEqualTo(Wei.of(3_000_000_000_000_000_000L));
    assertThat(isolatedRollForward.get().rootHash()).isEqualTo(block3.getHeader().getStateRoot());

    // we should be at block 1, one block behind BonsaiPersistatedWorldState
    var isolatedRollBack = archive.getMutableSnapshot(block1.getHash());
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
        archive.getMutableSnapshot(genesisState.getBlock().getHash()).get()) {

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
