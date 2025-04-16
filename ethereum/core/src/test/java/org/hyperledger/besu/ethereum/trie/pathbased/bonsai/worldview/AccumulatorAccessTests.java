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
package org.hyperledger.besu.ethereum.trie.pathbased.bonsai.worldview;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.hyperledger.besu.ethereum.trie.pathbased.common.provider.WorldStateQueryParams.withBlockHeaderAndNoUpdateNodeHead;

import org.hyperledger.besu.ethereum.core.BlockchainSetupUtil;
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.AbstractIsolationTests;
import org.hyperledger.besu.plugin.services.storage.DataStorageFormat;

import org.junit.jupiter.api.Test;

public class AccumulatorAccessTests extends AbstractIsolationTests {

  @Test
  public void assertBonsaiAccumulatorCastIsSafe() {
    var testChain = BlockchainSetupUtil.forTesting(DataStorageFormat.BONSAI);
    var headHeader = testChain.getBlockchain().getChainHeadHeader();
    var ws =
        testChain
            .getWorldArchive()
            .getWorldState(withBlockHeaderAndNoUpdateNodeHead(headHeader))
            .map(BonsaiWorldState.class::cast)
            .orElseThrow();
    var address = headHeader.getCoinbase();
    // get as mutable account via WorldUpdater interface
    var mutableAccount = ws.getAccumulator().getAccount(address);
    // get as accountValue via TrieLogAccumulator interface
    var accountVal = ws.getAccumulator().getAccountValue(address);
    assertThat(accountVal).isEqualTo(mutableAccount);
  }
}
