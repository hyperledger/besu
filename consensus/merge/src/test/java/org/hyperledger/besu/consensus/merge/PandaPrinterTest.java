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

import org.hyperledger.besu.ethereum.core.Difficulty;

import org.junit.Test;

public class PandaPrinterTest {

  final MergeStateHandler fauxTransitionHandler =
      (isPoS, priorState, ttd) -> {
        if (isPoS && priorState.filter(prior -> !prior).isPresent())
          PandaPrinter.printOnFirstCrossing();
      };

  @Test
  public void printsPanda() {
    PandaPrinter.resetForTesting();
    assertThat(PandaPrinter.printOnFirstCrossing()).isTrue();
    assertThat(PandaPrinter.printOnFirstCrossing()).isFalse();
  }

  @Test
  public void doesNotPrintAtInit() {
    PandaPrinter.resetForTesting();
    var mergeContext = new PostMergeContext(Difficulty.ONE);
    mergeContext.observeNewIsPostMergeState(fauxTransitionHandler);

    assertThat(PandaPrinter.hasDisplayed()).isFalse();
    mergeContext.setIsPostMerge(Difficulty.ONE);
    assertThat(PandaPrinter.hasDisplayed()).isFalse();
  }

  @Test
  public void printsWhenCrossingOnly() {
    PandaPrinter.resetForTesting();
    var mergeContext = new PostMergeContext(Difficulty.ONE);
    mergeContext.observeNewIsPostMergeState(fauxTransitionHandler);

    assertThat(PandaPrinter.hasDisplayed()).isFalse();
    mergeContext.setIsPostMerge(Difficulty.ZERO);
    assertThat(PandaPrinter.hasDisplayed()).isFalse();
    mergeContext.setIsPostMerge(Difficulty.ONE);
    assertThat(PandaPrinter.hasDisplayed()).isTrue();
  }
}
