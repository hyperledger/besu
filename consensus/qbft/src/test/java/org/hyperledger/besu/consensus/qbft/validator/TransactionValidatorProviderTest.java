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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

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
import org.junit.Before;
import org.junit.Test;

public class TransactionValidatorProviderTest {
  private final ValidatorContractController validatorContractController =
      mock(ValidatorContractController.class);

  protected MutableBlockchain blockChain;
  protected Block genesisBlock;
  protected Block block_1;
  protected Block block_2;
  private Block block_3;

  private final BlockHeaderTestFixture headerBuilder = new BlockHeaderTestFixture();

  @Before
  public void setup() {
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
    when(validatorContractController.getValidators(2)).thenReturn(validatorsAt2);
    when(validatorContractController.getValidators(3)).thenReturn(validatorsAt3);

    final TransactionValidatorProvider validatorProvider =
        new TransactionValidatorProvider(blockChain, validatorContractController);

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
    when(validatorContractController.getValidators(2)).thenReturn(validatorsAt2);
    when(validatorContractController.getValidators(3)).thenReturn(validatorsAt3);

    final TransactionValidatorProvider validatorProvider =
        new TransactionValidatorProvider(blockChain, validatorContractController);

    assertThat(validatorProvider.getValidatorsForBlock(block_2.getHeader()))
        .containsExactlyElementsOf(validatorsAt2);
    assertThat(validatorProvider.getValidatorsForBlock(block_3.getHeader()))
        .containsExactlyElementsOf(validatorProvider.getValidatorsForBlock(block_3.getHeader()));
  }

  @Test
  public void validatorsAtHeadAreRetrievedUsingContractController() {
    final List<Address> validators =
        Lists.newArrayList(Address.fromHexString("5"), Address.fromHexString("6"));
    when(validatorContractController.getValidators(3)).thenReturn(validators);

    final TransactionValidatorProvider validatorProvider =
        new TransactionValidatorProvider(blockChain, validatorContractController);

    assertThat(validatorProvider.getValidatorsAtHead()).containsExactlyElementsOf(validators);
  }

  @Test
  public void validatorsAtHeadContractCallIsCached() {
    final List<Address> validators =
        Lists.newArrayList(Address.fromHexString("5"), Address.fromHexString("6"));
    when(validatorContractController.getValidators(3)).thenReturn(validators);

    final TransactionValidatorProvider validatorProvider =
        new TransactionValidatorProvider(blockChain, validatorContractController);

    assertThat(validatorProvider.getValidatorsAtHead()).containsExactlyElementsOf(validators);
    verify(validatorContractController).getValidators(3);

    assertThat(validatorProvider.getValidatorsAtHead()).containsExactlyElementsOf(validators);
    verifyNoMoreInteractions(validatorContractController);
  }

  @Test
  public void validatorsAfterBlockContractCallIsCached() {
    final List<Address> validators =
        Lists.newArrayList(Address.fromHexString("5"), Address.fromHexString("6"));
    when(validatorContractController.getValidators(2)).thenReturn(validators);

    final TransactionValidatorProvider validatorProvider =
        new TransactionValidatorProvider(blockChain, validatorContractController);

    final Collection<Address> result =
        validatorProvider.getValidatorsAfterBlock(block_2.getHeader());
    assertThat(result).containsExactlyElementsOf(validators);
    verify(validatorContractController).getValidators(2);

    final Collection<Address> resultCached =
        validatorProvider.getValidatorsAfterBlock(block_2.getHeader());
    assertThat(resultCached).containsExactlyElementsOf(validators);
    verifyNoMoreInteractions(validatorContractController);
  }

  @Test
  public void validatorsMustBeSorted() {
    final List<Address> validators =
        Lists.newArrayList(
            Address.fromHexString("9"), Address.fromHexString("8"), Address.fromHexString("7"));
    when(validatorContractController.getValidators(3)).thenReturn(validators);

    final TransactionValidatorProvider validatorProvider =
        new TransactionValidatorProvider(blockChain, validatorContractController);

    final Collection<Address> result = validatorProvider.getValidatorsAtHead();
    final List<Address> expectedValidators =
        validators.stream().sorted().collect(Collectors.toList());
    assertThat(result).containsExactlyElementsOf(expectedValidators);
  }
}
