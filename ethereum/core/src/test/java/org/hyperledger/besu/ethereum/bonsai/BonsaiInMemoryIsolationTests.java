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

import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class BonsaiInMemoryIsolationTests extends AbstractIsolationTests {

  @Override
  protected boolean shouldUseSnapshots() {
    // override for layered worldstate
    return false;
  }

  @Test
  public void testInMemoryWorldStateConsistentAfterMutatingPersistedState() {
    Address testAddress = Address.fromHexString("0xdeadbeef");
    var persisted = archive.getMutable();

    var genesisBlock = genesisState.getBlock();

    var layered =
        archive
            .getMutable(genesisBlock.getHeader().getStateRoot(), genesisBlock.getHash(), false)
            .orElse(null);
    var inMemoryBeforeMutation = layered.copy();

    var firstBlock = forTransactions(List.of(burnTransaction(sender1, 0L, testAddress)));
    var res = executeBlock(persisted, firstBlock);
    assertThat(res.isSuccessful()).isTrue();

    assertThat(archive.getTrieLogManager().getBonsaiCachedWorldState(firstBlock.getHash()))
        .isNotEmpty();

    assertThat(res.isSuccessful()).isTrue();
    assertThat(persisted.get(testAddress)).isNotNull();
    assertThat(persisted.get(testAddress).getBalance())
        .isEqualTo(Wei.of(1_000_000_000_000_000_000L));
    assertThat(persisted.rootHash()).isEqualTo(firstBlock.getHeader().getStateRoot());

    var layered2 =
        archive
            .getMutable(genesisBlock.getHeader().getStateRoot(), genesisBlock.getHash(), false)
            .orElse(null);

    // assert layered before and after the block is what and where we expect it to be:
    assertThat(layered2).isInstanceOf(BonsaiSnapshotWorldState.class);
    assertThat(layered2.rootHash()).isEqualTo(genesisBlock.getHeader().getStateRoot());
    assertThat(layered2.get(testAddress)).isNull();
    assertThat(layered).isInstanceOf(BonsaiSnapshotWorldState.class);
    assertThat(layered.rootHash()).isEqualTo(genesisBlock.getHeader().getStateRoot());
    assertThat(layered.get(testAddress)).isNull();

    var inMemoryAfterMutation = layered2.copy();

    // assert we have not modified the head worldstate:
    assertThat(persisted.rootHash()).isEqualTo(firstBlock.getHeader().getStateRoot());

    // assert the inMemory copy of worldstate 0 from [before mutating persisted state to 1] is
    // consistent
    assertThat(inMemoryBeforeMutation).isInstanceOf(BonsaiInMemoryWorldState.class);
    assertThat(inMemoryBeforeMutation.rootHash())
        .isEqualTo(genesisBlock.getHeader().getStateRoot());
    assertThat(inMemoryBeforeMutation.get(testAddress)).isNull();

    // assert the inMemory copy of worldstate 0 from [after mutating persisted state to 1] is
    // consistent
    assertThat(inMemoryAfterMutation).isInstanceOf(BonsaiInMemoryWorldState.class);
    assertThat(inMemoryAfterMutation.rootHash()).isEqualTo(genesisBlock.getHeader().getStateRoot());
    assertThat(inMemoryAfterMutation.get(testAddress)).isNull();

    try {
      layered.close();
      layered2.close();
      inMemoryBeforeMutation.close();
      inMemoryAfterMutation.close();
    } catch (Exception ex) {
      throw new RuntimeException("failed to close worldstates");
    }
  }
}
