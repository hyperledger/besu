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
import org.hyperledger.besu.consensus.common.validator.VoteProvider;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.AddressHelpers;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.Hash;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.tuweni.bytes.Bytes;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ForkingValidatorProviderTest {

  private static final Address CONTRACT_ADDRESS_1 = Address.fromHexString("0x888");
  private static final Address CONTRACT_ADDRESS_2 = Address.fromHexString("0x999");
  private static final List<Address> BLOCK_ADDRESSES =
      List.of(Address.fromHexString("1"), Address.fromHexString("2"));
  private static final List<Address> CONTRACT_ADDRESSES_1 =
      List.of(Address.fromHexString("3"), Address.fromHexString("4"));
  private static final List<Address> CONTRACT_ADDRESSES_2 =
      List.of(Address.fromHexString("5"), Address.fromHexString("6"), Address.fromHexString("7"));

  @Mock private ValidatorProviderFactory validatorProviderFactory;
  @Mock private ValidatorProvider blockValidatorProvider;
  @Mock private ValidatorProvider contractValidatorProvider1;
  @Mock private ValidatorProvider contractValidatorProvider2;

  private MutableBlockchain blockChain;
  private BlockHeader genesisHeader;
  private BlockHeader header1;
  private BlockHeader header2;
  private final BlockHeaderTestFixture headerBuilder = new BlockHeaderTestFixture();

  @Before
  public void setup() {
    headerBuilder.extraData(Bytes.wrap(new byte[32]));
    Block genesisBlock = createEmptyBlock(0, Hash.ZERO);
    Block block_1 = createEmptyBlock(1, genesisBlock.getHeader().getHash());
    Block block_2 = createEmptyBlock(2, block_1.getHeader().getHash());
    genesisHeader = genesisBlock.getHeader();
    header1 = block_1.getHeader();
    header2 = block_2.getHeader();

    blockChain = createInMemoryBlockchain(genesisBlock);
    blockChain.appendBlock(block_1, emptyList());
    blockChain.appendBlock(block_2, emptyList());

    when(blockValidatorProvider.getValidatorsForBlock(any())).thenReturn(BLOCK_ADDRESSES);
    when(contractValidatorProvider1.getValidatorsForBlock(any())).thenReturn(CONTRACT_ADDRESSES_1);
    when(contractValidatorProvider2.getValidatorsForBlock(any())).thenReturn(CONTRACT_ADDRESSES_2);
    when(validatorProviderFactory.createTransactionValidatorProvider(CONTRACT_ADDRESS_1))
        .thenReturn(contractValidatorProvider1);
    when(validatorProviderFactory.createTransactionValidatorProvider(CONTRACT_ADDRESS_2))
        .thenReturn(contractValidatorProvider2);
    when(validatorProviderFactory.createBlockValidatorProvider())
        .thenReturn(blockValidatorProvider);
  }

  private Block createEmptyBlock(final long blockNumber, final Hash parentHash) {
    headerBuilder.number(blockNumber).parentHash(parentHash).coinbase(AddressHelpers.ofValue(0));
    return new Block(headerBuilder.buildHeader(), new BlockBody(emptyList(), emptyList()));
  }

  @Test
  public void usesInitialValidatorProviderWhenNoForks() {
    final QbftForksSchedule forksSchedule = new QbftForksSchedule(Collections.emptyList());
    final ForkingValidatorProvider validatorProvider =
        new ForkingValidatorProvider(
            blockChain, forksSchedule, validatorProviderFactory, blockValidatorProvider);

    when(blockValidatorProvider.getValidatorsAtHead()).thenReturn(BLOCK_ADDRESSES);
    when(blockValidatorProvider.getValidatorsAfterBlock(header1)).thenReturn(BLOCK_ADDRESSES);

    assertThat(validatorProvider.getValidatorsAtHead()).isEqualTo(BLOCK_ADDRESSES);
    assertThat(validatorProvider.getValidatorsForBlock(header1)).isEqualTo(BLOCK_ADDRESSES);
    assertThat(validatorProvider.getValidatorsAfterBlock(header1)).isEqualTo(BLOCK_ADDRESSES);
  }

  @Test
  public void migratesFromBlockToContractValidatorProvider() {
    final QbftForksSchedule forksSchedule =
        new QbftForksSchedule(List.of(createContractFork(1L, CONTRACT_ADDRESS_1)));
    final ForkingValidatorProvider validatorProvider =
        new ForkingValidatorProvider(
            blockChain, forksSchedule, validatorProviderFactory, blockValidatorProvider);

    assertThat(validatorProvider.getValidatorsForBlock(genesisHeader)).isEqualTo(BLOCK_ADDRESSES);
    assertThat(validatorProvider.getValidatorsForBlock(header1)).isEqualTo(CONTRACT_ADDRESSES_1);
    assertThat(validatorProvider.getValidatorsForBlock(header2)).isEqualTo(CONTRACT_ADDRESSES_1);
  }

  @Test
  public void migratesFromContractToBlockValidatorProvider() {
    final QbftForksSchedule forksSchedule = new QbftForksSchedule(List.of(createBlockFork(1L)));
    final ForkingValidatorProvider validatorProvider =
        new ForkingValidatorProvider(
            blockChain, forksSchedule, validatorProviderFactory, contractValidatorProvider1);

    assertThat(validatorProvider.getValidatorsForBlock(genesisHeader))
        .isEqualTo(CONTRACT_ADDRESSES_1);
    assertThat(validatorProvider.getValidatorsForBlock(header1)).isEqualTo(CONTRACT_ADDRESSES_1);
    assertThat(validatorProvider.getValidatorsForBlock(header2)).isEqualTo(BLOCK_ADDRESSES);
  }

  @Test
  public void migratesFromContractToContractValidatorProvider() {
    final QbftForksSchedule forksSchedule =
        new QbftForksSchedule(
            List.of(
                createContractFork(1L, CONTRACT_ADDRESS_1),
                createContractFork(2L, CONTRACT_ADDRESS_2)));

    final ForkingValidatorProvider validatorProvider =
        new ForkingValidatorProvider(
            blockChain, forksSchedule, validatorProviderFactory, blockValidatorProvider);

    assertThat(validatorProvider.getValidatorsForBlock(genesisHeader)).isEqualTo(BLOCK_ADDRESSES);
    assertThat(validatorProvider.getValidatorsForBlock(header1)).isEqualTo(CONTRACT_ADDRESSES_1);
    assertThat(validatorProvider.getValidatorsForBlock(header2)).isEqualTo(CONTRACT_ADDRESSES_2);
  }

  @Test
  public void migratingToContractWithoutAddressIsIgnored() {
    final ObjectNode config =
        JsonUtil.objectNodeFromMap(Map.of("block", 1L, "validatorselectionmode", "contract"));
    final QbftForksSchedule forksSchedule = new QbftForksSchedule(List.of(new QbftFork(config)));

    final ForkingValidatorProvider validatorProvider =
        new ForkingValidatorProvider(
            blockChain, forksSchedule, validatorProviderFactory, blockValidatorProvider);

    assertThat(validatorProvider.getValidatorsForBlock(genesisHeader)).isEqualTo(BLOCK_ADDRESSES);
    assertThat(validatorProvider.getValidatorsForBlock(header1)).isEqualTo(BLOCK_ADDRESSES);
    assertThat(validatorProvider.getValidatorsForBlock(header2)).isEqualTo(BLOCK_ADDRESSES);
  }

  @Test
  public void voteProviderIsDelegatesToHeadFork() {
    final QbftForksSchedule forksSchedule =
        new QbftForksSchedule(
            List.of(createBlockFork(1), createContractFork(2, CONTRACT_ADDRESS_1)));
    final ForkingValidatorProvider validatorProvider =
        new ForkingValidatorProvider(
            blockChain, forksSchedule, validatorProviderFactory, blockValidatorProvider);

    final VoteProvider voteProvider = Mockito.mock(VoteProvider.class);
    when(contractValidatorProvider1.getVoteProvider()).thenReturn(Optional.of(voteProvider));

    assertThat(validatorProvider.getVoteProvider()).contains(voteProvider);
  }

  @Test
  public void migratesOnBlockZero() {
    final QbftForksSchedule forksSchedule =
        new QbftForksSchedule(
            List.of(
                createContractFork(0L, CONTRACT_ADDRESS_1),
                createContractFork(1L, CONTRACT_ADDRESS_2)));

    final ForkingValidatorProvider validatorProvider =
        new ForkingValidatorProvider(
            blockChain, forksSchedule, validatorProviderFactory, blockValidatorProvider);

    assertThat(validatorProvider.getValidatorsForBlock(genesisHeader))
        .isEqualTo(CONTRACT_ADDRESSES_1);
    assertThat(validatorProvider.getValidatorsForBlock(header1)).isEqualTo(CONTRACT_ADDRESSES_2);
  }

  private QbftFork createContractFork(final long block, final Address contractAddress) {
    return new QbftFork(
        JsonUtil.objectNodeFromMap(
            Map.of(
                "block",
                block,
                "validatorselectionmode",
                "contract",
                "validatorcontractaddress",
                contractAddress.toHexString())));
  }

  private QbftFork createBlockFork(final long block) {
    return new QbftFork(
        JsonUtil.objectNodeFromMap(
            Map.of("block", block, "validatorselectionmode", "blockheader")));
  }
}
