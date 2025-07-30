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
package org.hyperledger.besu.consensus.common.validator.blockbased;

import static org.hyperledger.besu.ethereum.core.InMemoryKeyValueStorageProvider.createInMemoryBlockchain;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.consensus.common.BlockInterface;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.AddressHelpers;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;

import java.util.Collections;
import java.util.List;

import org.apache.tuweni.bytes.Bytes;
import org.assertj.core.util.Lists;
import org.junit.jupiter.api.BeforeEach;

public class VoteTallyCacheTestBase {

  protected final BlockHeaderTestFixture headerBuilder = new BlockHeaderTestFixture();

  protected Block createEmptyBlock(final long blockNumber, final Hash parentHash) {
    headerBuilder.number(blockNumber).parentHash(parentHash).coinbase(AddressHelpers.ofValue(0));
    return new Block(
        headerBuilder.buildHeader(),
        new BlockBody(Collections.emptyList(), Collections.emptyList()));
  }

  protected MutableBlockchain blockChain;
  protected Block genesisBlock;
  protected Block block_1;
  protected Block block_2;

  protected final List<Address> validators = Lists.newArrayList();

  protected final BlockInterface blockInterface = mock(BlockInterface.class);

  @BeforeEach
  public void constructThreeBlockChain() {
    for (int i = 0; i < 3; i++) {
      validators.add(AddressHelpers.ofValue(i));
    }
    headerBuilder.extraData(Bytes.wrap(new byte[32]));

    genesisBlock = createEmptyBlock(0, Hash.ZERO);

    blockChain = createInMemoryBlockchain(genesisBlock);

    block_1 = createEmptyBlock(1, genesisBlock.getHeader().getHash());
    block_2 = createEmptyBlock(2, block_1.getHeader().getHash());

    blockChain.appendBlock(block_1, Collections.emptyList());
    blockChain.appendBlock(block_2, Collections.emptyList());

    when(blockInterface.validatorsInBlock(any())).thenReturn(validators);
  }
}
