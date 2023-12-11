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
import static org.hyperledger.besu.consensus.qbft.validator.ValidatorTestUtils.createContractForkSpec;
import static org.hyperledger.besu.ethereum.core.InMemoryKeyValueStorageProvider.createInMemoryBlockchain;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.config.QbftConfigOptions;
import org.hyperledger.besu.consensus.common.ForksSchedule;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.AddressHelpers;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import com.google.common.collect.Lists;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class TransactionValidatorProviderTest {
  @Mock private ValidatorContractController validatorContractController;

  protected MutableBlockchain blockChain;
  protected Block genesisBlock;
  protected Block block_1;
  protected Block block_2;
  private Block block_3;
  private ForksSchedule<QbftConfigOptions> forksSchedule;

  private final BlockHeaderTestFixture headerBuilder = new BlockHeaderTestFixture();
  private static final Address CONTRACT_ADDRESS = Address.fromHexString("1");

  @BeforeEach
  public void setup() {
    forksSchedule = new ForksSchedule<>(List.of(createContractForkSpec(0L, CONTRACT_ADDRESS)));
    genesisBlock = createEmptyBlock(0, Hash.ZERO);
    blockChain = createInMemoryBlockchain(genesisBlock);
    headerBuilder.extraData(Bytes.wrap(new byte[32]));

    block_1 = createEmptyBlock(1, genesisBlock.getHeader().getHash());
    block_2 = createEmptyBlock(2, block_1.getHeader().getHash());
    block_3 = createEmptyBlock(3, block_2.getHeader().getHash());

    blockChain.appendBlock(block_1, emptyList());
    blockChain.appendBlock(block_2, emptyList());
    blockChain.appendBlock(block_3, emptyList());
  }

  private Block createEmptyBlock(final long blockNumber, final Hash parentHash) {
    headerBuilder.number(blockNumber).parentHash(parentHash).coinbase(AddressHelpers.ofValue(0));
    return new Block(headerBuilder.buildHeader(), new BlockBody(emptyList(), emptyList()));
  }

  @Test
  public void validatorsAfterBlockAreRetrievedUsingContractController() {
    final List<Address> validatorsAt2 =
        Lists.newArrayList(Address.fromHexString("5"), Address.fromHexString("6"));
    final List<Address> validatorsAt3 =
        Lists.newArrayList(
            Address.fromHexString("5"), Address.fromHexString("6"), Address.fromHexString("7"));
    when(validatorContractController.getValidators(2, CONTRACT_ADDRESS)).thenReturn(validatorsAt2);
    when(validatorContractController.getValidators(3, CONTRACT_ADDRESS)).thenReturn(validatorsAt3);

    final TransactionValidatorProvider validatorProvider =
        new TransactionValidatorProvider(blockChain, validatorContractController, forksSchedule);

    assertThat(validatorProvider.getValidatorsAfterBlock(block_2.getHeader()))
        .containsExactlyElementsOf(validatorsAt2);
    assertThat(validatorProvider.getValidatorsAfterBlock(block_3.getHeader()))
        .containsExactlyElementsOf(validatorProvider.getValidatorsAfterBlock(block_3.getHeader()));
  }

  @Test
  public void validatorsForBlockAreRetrievedUsingContractController() {
    final List<Address> validatorsAt2 =
        Lists.newArrayList(Address.fromHexString("5"), Address.fromHexString("6"));
    final List<Address> validatorsAt3 =
        Lists.newArrayList(
            Address.fromHexString("5"), Address.fromHexString("6"), Address.fromHexString("7"));
    when(validatorContractController.getValidators(2, CONTRACT_ADDRESS)).thenReturn(validatorsAt2);
    when(validatorContractController.getValidators(3, CONTRACT_ADDRESS)).thenReturn(validatorsAt3);

    final TransactionValidatorProvider validatorProvider =
        new TransactionValidatorProvider(blockChain, validatorContractController, forksSchedule);

    assertThat(validatorProvider.getValidatorsForBlock(block_2.getHeader()))
        .containsExactlyElementsOf(validatorsAt2);
    assertThat(validatorProvider.getValidatorsForBlock(block_3.getHeader()))
        .containsExactlyElementsOf(validatorProvider.getValidatorsForBlock(block_3.getHeader()));
  }

  @Test
  public void validatorsAtHeadAreRetrievedUsingContractController() {
    final List<Address> validators =
        Lists.newArrayList(Address.fromHexString("5"), Address.fromHexString("6"));
    when(validatorContractController.getValidators(3, CONTRACT_ADDRESS)).thenReturn(validators);

    final TransactionValidatorProvider validatorProvider =
        new TransactionValidatorProvider(blockChain, validatorContractController, forksSchedule);

    assertThat(validatorProvider.getValidatorsAtHead()).containsExactlyElementsOf(validators);
  }

  @Test
  public void validatorsAtHeadContractCallIsCached() {
    final List<Address> validators =
        Lists.newArrayList(Address.fromHexString("5"), Address.fromHexString("6"));
    when(validatorContractController.getValidators(3, CONTRACT_ADDRESS)).thenReturn(validators);

    final TransactionValidatorProvider validatorProvider =
        new TransactionValidatorProvider(blockChain, validatorContractController, forksSchedule);

    assertThat(validatorProvider.getValidatorsAtHead()).containsExactlyElementsOf(validators);
    verify(validatorContractController).getValidators(3, CONTRACT_ADDRESS);

    assertThat(validatorProvider.getValidatorsAtHead()).containsExactlyElementsOf(validators);
    verifyNoMoreInteractions(validatorContractController);
  }

  @Test
  public void validatorsAfterBlockContractCallIsCached() {
    final List<Address> validators =
        Lists.newArrayList(Address.fromHexString("5"), Address.fromHexString("6"));
    when(validatorContractController.getValidators(2, CONTRACT_ADDRESS)).thenReturn(validators);

    final TransactionValidatorProvider validatorProvider =
        new TransactionValidatorProvider(blockChain, validatorContractController, forksSchedule);

    final Collection<Address> result =
        validatorProvider.getValidatorsAfterBlock(block_2.getHeader());
    assertThat(result).containsExactlyElementsOf(validators);
    verify(validatorContractController).getValidators(2, CONTRACT_ADDRESS);

    final Collection<Address> resultCached =
        validatorProvider.getValidatorsAfterBlock(block_2.getHeader());
    assertThat(resultCached).containsExactlyElementsOf(validators);
    verifyNoMoreInteractions(validatorContractController);
  }

  @Test
  public void getValidatorsAfterBlock_and_getValidatorsForBlock_useDifferentCaches() {
    final List<Address> validators =
        Lists.newArrayList(Address.fromHexString("5"), Address.fromHexString("6"));
    when(validatorContractController.getValidators(2, CONTRACT_ADDRESS)).thenReturn(validators);

    final TransactionValidatorProvider validatorProvider =
        new TransactionValidatorProvider(blockChain, validatorContractController, forksSchedule);

    validatorProvider.getValidatorsAfterBlock(block_2.getHeader()); // cache miss
    verify(validatorContractController, times(1)).getValidators(2, CONTRACT_ADDRESS);

    validatorProvider.getValidatorsAfterBlock(block_2.getHeader()); // cache hit
    verifyNoMoreInteractions(validatorContractController);

    validatorProvider.getValidatorsForBlock(block_2.getHeader()); // cache miss
    verify(validatorContractController, times(2)).getValidators(2, CONTRACT_ADDRESS);

    validatorProvider.getValidatorsAfterBlock(block_2.getHeader()); // cache hit
    verifyNoMoreInteractions(validatorContractController);
  }

  @Test
  public void validatorsMustBeSorted() {
    final List<Address> validators =
        Lists.newArrayList(
            Address.fromHexString("9"), Address.fromHexString("8"), Address.fromHexString("7"));
    when(validatorContractController.getValidators(3, CONTRACT_ADDRESS)).thenReturn(validators);

    final TransactionValidatorProvider validatorProvider =
        new TransactionValidatorProvider(blockChain, validatorContractController, forksSchedule);

    final Collection<Address> result = validatorProvider.getValidatorsAtHead();
    final List<Address> expectedValidators =
        validators.stream().sorted().collect(Collectors.toList());
    assertThat(result).containsExactlyElementsOf(expectedValidators);
  }

  @Test
  public void voteProviderIsEmpty() {
    TransactionValidatorProvider transactionValidatorProvider =
        new TransactionValidatorProvider(blockChain, validatorContractController, forksSchedule);

    assertThat(transactionValidatorProvider.getVoteProviderAtHead()).isEmpty();
  }
}
