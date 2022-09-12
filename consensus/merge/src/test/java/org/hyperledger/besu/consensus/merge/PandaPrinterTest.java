/*
 * Copyright Hyperledger Besu Contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 *  the License. You may obtain a copy of the License at
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

package org.hyperledger.besu.consensus.merge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.chain.BlockAddedEvent;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Difficulty;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Optional;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class PandaPrinterTest {

  final MergeStateHandler fauxTransitionHandler =
      (isPoS, priorState, ttd) -> {
        if (isPoS && priorState.filter(prior -> !prior).isPresent())
          PandaPrinter.getInstance().printOnFirstCrossing();
      };

  @Test
  public void printsPanda() {
    PandaPrinter p = new PandaPrinter(Optional.of(Difficulty.of(1)), Difficulty.of(BigInteger.TEN));
    p.resetForTesting();
    assertThat(p.ttdBeenDisplayed).isFalse();
    p.printOnFirstCrossing();
    assertThat(p.ttdBeenDisplayed).isTrue();
    assertThat(p.readyBeenDisplayed).isFalse();
    assertThat(p.finalizedBeenDisplayed).isFalse();
  }

  @Test
  public void doesNotPrintAtPreMergeInit() {
    PandaPrinter p = new PandaPrinter(Optional.of(Difficulty.of(1)), Difficulty.of(BigInteger.TEN));

    var mergeContext = new PostMergeContext(Difficulty.of(BigInteger.TEN));
    mergeContext.observeNewIsPostMergeState(fauxTransitionHandler);

    assertThat(p.ttdBeenDisplayed).isFalse();
    mergeContext.setIsPostMerge(Difficulty.ONE);
    assertThat(p.ttdBeenDisplayed).isFalse();
    assertThat(p.readyBeenDisplayed).isFalse();
    assertThat(p.finalizedBeenDisplayed).isFalse();
  }

  @Test
  public void printsWhenCrossingOnly() {
    PandaPrinter p = new PandaPrinter(Optional.of(Difficulty.of(1)), Difficulty.of(10));
    p.inSync();
    p.hasTTD();
    assertThat(p.ttdBeenDisplayed).isFalse();
    p.onBlockAdded(withDifficulty(Difficulty.of(11)));
    assertThat(p.ttdBeenDisplayed).isTrue();
    assertThat(p.readyBeenDisplayed).isFalse();
    assertThat(p.finalizedBeenDisplayed).isFalse();
  }

  @Test
  public void printsReadyOnStartupInSyncWithPoWTTD() {
    PandaPrinter p = new PandaPrinter(Optional.of(Difficulty.of(1)), Difficulty.of(10));
    p.inSync();
    p.hasTTD();
    p.onBlockAdded(withDifficulty(Difficulty.of(2)));
    assertThat(p.readyBeenDisplayed).isTrue();
    assertThat(p.ttdBeenDisplayed).isFalse();
    assertThat(p.finalizedBeenDisplayed).isFalse();
  }

  @Test
  public void noPandasPostTTD() {
    PandaPrinter p =
        new PandaPrinter(Optional.of(Difficulty.of(11)), Difficulty.of(BigInteger.TEN));
    p.inSync();
    p.hasTTD();

    assertThat(p.readyBeenDisplayed).isTrue();
    assertThat(p.ttdBeenDisplayed).isTrue();
    assertThat(p.finalizedBeenDisplayed).isTrue();
    p.onBlockAdded(withDifficulty(Difficulty.of(11)));
    assertThat(p.readyBeenDisplayed).isTrue();
    assertThat(p.ttdBeenDisplayed).isTrue();
    assertThat(p.finalizedBeenDisplayed).isTrue();
  }

  @Test
  public void printsFinalized() {
    PandaPrinter p = new PandaPrinter(Optional.of(Difficulty.of(9)), Difficulty.of(BigInteger.TEN));
    assertThat(p.finalizedBeenDisplayed).isFalse();
    MergeContext mergeContext = new PostMergeContext(Difficulty.ZERO);
    mergeContext.addNewForkchoiceMessageListener(p);
    mergeContext.fireNewUnverifiedForkchoiceMessageEvent(
        Hash.ZERO, Optional.of(Hash.ZERO), Hash.ZERO);
    mergeContext.fireNewUnverifiedForkchoiceMessageEvent(
        Hash.ZERO, Optional.of(Hash.fromHexStringLenient("0x1337")), Hash.ZERO);
    assertThat(p.finalizedBeenDisplayed).isTrue();
  }

  private BlockAddedEvent withDifficulty(final Difficulty diff) {
    BlockBody mockBody = mock(BlockBody.class);
    when(mockBody.getTransactions()).thenReturn(new ArrayList<>());
    BlockHeader mockHeader = mock(BlockHeader.class);
    when(mockHeader.getDifficulty()).thenReturn(diff);
    when(mockHeader.getParentHash()).thenReturn(Hash.ZERO);
    Block mockBlock = mock(Block.class);
    when(mockBlock.getHeader()).thenReturn(mockHeader);
    when(mockBlock.getBody()).thenReturn(mockBody);
    BlockAddedEvent powArrived =
        BlockAddedEvent.createForHeadAdvancement(mockBlock, new ArrayList<>(), new ArrayList<>());
    return powArrived;
  }
}
