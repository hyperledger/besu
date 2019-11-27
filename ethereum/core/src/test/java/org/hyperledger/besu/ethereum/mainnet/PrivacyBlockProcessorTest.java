/*
 * Copyright ConsenSys AG.
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
package org.hyperledger.besu.ethereum.mainnet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockDataGenerator;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.privacy.storage.PrivacyGroupHeadBlockMap;
import org.hyperledger.besu.ethereum.privacy.storage.PrivateStateKeyValueStorage;
import org.hyperledger.besu.ethereum.privacy.storage.PrivateStateStorage;
import org.hyperledger.besu.services.kvstore.InMemoryKeyValueStorage;
import org.hyperledger.besu.util.bytes.Bytes32;

import java.util.Collections;

import org.junit.Before;
import org.junit.Test;

public class PrivacyBlockProcessorTest {

  private PrivacyParameters privacyParameters;
  private PrivacyBlockProcessor privacyBlockProcessor;

  @Before
  public void setUp() {
    final AbstractBlockProcessor blockProcessor = mock(AbstractBlockProcessor.class);
    this.privacyParameters = mock(PrivacyParameters.class);
    this.privacyBlockProcessor = new PrivacyBlockProcessor(blockProcessor, privacyParameters);
  }

  @Test
  public void mustCopyPreviousPrivacyGroupBlockHeadMap() {
    final BlockDataGenerator blockDataGenerator = new BlockDataGenerator();
    final PrivateStateStorage privateStateStorage =
        new PrivateStateKeyValueStorage(new InMemoryKeyValueStorage());
    when(privacyParameters.getPrivateStateStorage()).thenReturn(privateStateStorage);
    final Blockchain blockchain = mock(Blockchain.class);
    final MutableWorldState mutableWorldState = mock(MutableWorldState.class);
    final PrivacyGroupHeadBlockMap expected =
        new PrivacyGroupHeadBlockMap(Collections.singletonMap(Bytes32.ZERO, Hash.EMPTY));
    final Block firstBlock = blockDataGenerator.block();
    final Block secondBlock =
        blockDataGenerator.block(
            BlockDataGenerator.BlockOptions.create().setParentHash(firstBlock.getHash()));
    privacyBlockProcessor.processBlock(blockchain, mutableWorldState, firstBlock);
    privateStateStorage
        .updater()
        .putPrivacyGroupHeadBlockMap(firstBlock.getHash(), expected)
        .commit();
    privacyBlockProcessor.processBlock(blockchain, mutableWorldState, secondBlock);
    assertThat(privateStateStorage.getPrivacyGroupHeadBlockMap(secondBlock.getHash()))
        .contains(expected);
  }
}
