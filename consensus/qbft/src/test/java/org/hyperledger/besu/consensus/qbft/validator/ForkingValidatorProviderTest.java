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

package org.hyperledger.besu.consensus.qbft.validator;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.ethereum.core.InMemoryKeyValueStorageProvider.createInMemoryBlockchain;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.config.JsonUtil;
import org.hyperledger.besu.config.QbftFork;
import org.hyperledger.besu.consensus.common.validator.ValidatorProvider;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.AddressHelpers;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.Hash;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.tuweni.bytes.Bytes;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ForkingValidatorProviderTest {
  @Mock private ValidatorProviderFactory validatorProviderFactory;
  @Mock private ValidatorProvider contractValidatorProvider;
  @Mock private ValidatorProvider blockValidatorProvider;

  protected MutableBlockchain blockChain;
  protected Block genesisBlock;
  protected Block block_1;
  protected Block block_2;

  private final BlockHeaderTestFixture headerBuilder = new BlockHeaderTestFixture();

  @Before
  public void setup() {
    headerBuilder.extraData(Bytes.wrap(new byte[32]));
    genesisBlock = createEmptyBlock(0, Hash.ZERO);
    block_1 = createEmptyBlock(1, genesisBlock.getHeader().getHash());
    block_2 = createEmptyBlock(2, block_1.getHeader().getHash());

    blockChain = createInMemoryBlockchain(genesisBlock);
    blockChain.appendBlock(block_1, emptyList());
    blockChain.appendBlock(block_2, emptyList());
  }

  private Block createEmptyBlock(final long blockNumber, final Hash parentHash) {
    headerBuilder.number(blockNumber).parentHash(parentHash).coinbase(AddressHelpers.ofValue(0));
    return new Block(headerBuilder.buildHeader(), new BlockBody(emptyList(), emptyList()));
  }

  @Test
  public void usesInitialValidatorProviderWhenNoForks() {
    final QbftForksSchedule forksSchedule = new QbftForksSchedule(Collections.emptyList());
    final ForkingValidatorProvider forkingValidatorProvider =
        new ForkingValidatorProvider(
            blockChain, forksSchedule, validatorProviderFactory, blockValidatorProvider);

    final List<Address> addresses = List.of(Address.fromHexString("1"), Address.fromHexString("2"));
    when(blockValidatorProvider.getValidatorsAtHead()).thenReturn(addresses);
    when(blockValidatorProvider.getValidatorsForBlock(block_1.getHeader())).thenReturn(addresses);
    when(blockValidatorProvider.getValidatorsAfterBlock(block_1.getHeader())).thenReturn(addresses);

    assertThat(forkingValidatorProvider.getValidatorsAtHead()).isEqualTo(addresses);
    assertThat(forkingValidatorProvider.getValidatorsForBlock(block_1.getHeader()))
        .isEqualTo(addresses);
    assertThat(forkingValidatorProvider.getValidatorsAfterBlock(block_1.getHeader()))
        .isEqualTo(addresses);
  }

  @Test
  public void migratesFromBlockToContractValidatorProvider() {
    final ObjectNode config =
        JsonUtil.objectNodeFromMap(
            Map.of("block", 1L, "validatorMode", "CONTRACT", "validatorContractAddress", "0x888"));
    final QbftForksSchedule forksSchedule = new QbftForksSchedule(List.of(new QbftFork(config)));

    final List<Address> blockAddresses =
        List.of(Address.fromHexString("1"), Address.fromHexString("2"));
    final List<Address> contractAddresses =
        List.of(Address.fromHexString("3"), Address.fromHexString("4"));
    when(validatorProviderFactory.createTransactionValidatorProvider(any()))
        .thenReturn(contractValidatorProvider);
    when(blockValidatorProvider.getValidatorsForBlock(any())).thenReturn(blockAddresses);
    when(contractValidatorProvider.getValidatorsForBlock(any())).thenReturn(contractAddresses);

    final ForkingValidatorProvider forkingValidatorProvider =
        new ForkingValidatorProvider(
            blockChain, forksSchedule, validatorProviderFactory, blockValidatorProvider);

    assertThat(forkingValidatorProvider.getValidatorsForBlock(genesisBlock.getHeader()))
        .isEqualTo(blockAddresses);
    assertThat(forkingValidatorProvider.getValidatorsForBlock(block_1.getHeader()))
        .isEqualTo(contractAddresses);
    assertThat(forkingValidatorProvider.getValidatorsForBlock(block_2.getHeader()))
        .isEqualTo(contractAddresses);
  }

  @Test
  public void migratesFromContractToBlockValidatorProvider() {
    final ObjectNode config =
        JsonUtil.objectNodeFromMap(Map.of("block", 1L, "validatorMode", "BLOCK"));
    final QbftForksSchedule forksSchedule = new QbftForksSchedule(List.of(new QbftFork(config)));

    final List<Address> blockAddresses =
        List.of(Address.fromHexString("1"), Address.fromHexString("2"));
    final List<Address> contractAddresses =
        List.of(Address.fromHexString("3"), Address.fromHexString("4"));

    when(validatorProviderFactory.createBlockValidatorProvider())
        .thenReturn(blockValidatorProvider);
    when(contractValidatorProvider.getValidatorsForBlock(any())).thenReturn(contractAddresses);
    when(blockValidatorProvider.getValidatorsForBlock(any())).thenReturn(blockAddresses);

    final ForkingValidatorProvider forkingValidatorProvider =
        new ForkingValidatorProvider(
            blockChain, forksSchedule, validatorProviderFactory, contractValidatorProvider);

    assertThat(forkingValidatorProvider.getValidatorsForBlock(genesisBlock.getHeader()))
        .isEqualTo(contractAddresses);
    assertThat(forkingValidatorProvider.getValidatorsForBlock(block_1.getHeader()))
        .isEqualTo(contractAddresses);
    assertThat(forkingValidatorProvider.getValidatorsForBlock(block_2.getHeader()))
        .isEqualTo(blockAddresses);
  }

  // non-validator to validator can migrate
  // test boundary changes
  // must initialise block with previous contract state
}
