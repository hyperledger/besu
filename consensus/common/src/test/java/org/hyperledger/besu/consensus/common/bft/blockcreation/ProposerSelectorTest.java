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
package org.hyperledger.besu.consensus.common.bft.blockcreation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.consensus.common.BlockInterface;
import org.hyperledger.besu.consensus.common.bft.ConsensusRoundIdentifier;
import org.hyperledger.besu.consensus.common.validator.ValidatorProvider;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.AddressHelpers;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.junit.Test;

public class ProposerSelectorTest {

  private final BlockInterface blockInterface = mock(BlockInterface.class);
  private final ValidatorProvider validatorProvider = mock(ValidatorProvider.class);

  private Blockchain createMockedBlockChainWithHeadOf(
      final long blockNumber, final Address proposer, final Collection<Address> validators) {

    when(blockInterface.getProposerOfBlock(any())).thenReturn(proposer);
    when(validatorProvider.getValidatorsAfterBlock(any())).thenReturn(validators);

    final BlockHeaderTestFixture headerBuilderFixture = new BlockHeaderTestFixture();
    headerBuilderFixture.number(blockNumber);
    final BlockHeader prevBlockHeader = headerBuilderFixture.buildHeader();

    // Construct a block chain and world state
    final MutableBlockchain blockchain = mock(MutableBlockchain.class);
    when(blockchain.getBlockHeader(anyLong())).thenReturn(Optional.of(prevBlockHeader));

    return blockchain;
  }

  /**
   * This creates a list of validators, with the a number of validators above and below the local
   * address. The returned list is sorted.
   *
   * @param localAddr The address of the node which signed the parent block
   * @param countLower The number of validators which have a higher address than localAddr
   * @param countHigher The number of validators which have a lower address than localAddr
   * @return A sorted list of validators which matches parameters (including the localAddr).
   */
  private List<Address> createValidatorList(
      final Address localAddr, final int countLower, final int countHigher) {
    final List<Address> result = new ArrayList<>();

    // Note: Order of this list is irrelevant, is sorted by value later.
    result.add(localAddr);

    for (int i = 0; i < countLower; i++) {
      result.add(AddressHelpers.calculateAddressWithRespectTo(localAddr, i - countLower));
    }

    for (int i = 0; i < countHigher; i++) {
      result.add(AddressHelpers.calculateAddressWithRespectTo(localAddr, i + 1));
    }

    result.sort(null);
    return result;
  }

  @Test
  public void roundRobinChangesProposerOnRoundZeroOfNextBlock() {
    final long PREV_BLOCK_NUMBER = 2;
    final Address localAddr = AddressHelpers.ofValue(10); // arbitrarily selected

    final List<Address> validatorList = createValidatorList(localAddr, 0, 4);
    final Blockchain blockchain =
        createMockedBlockChainWithHeadOf(PREV_BLOCK_NUMBER, localAddr, validatorList);

    final ProposerSelector uut =
        new ProposerSelector(blockchain, blockInterface, true, validatorProvider);

    final ConsensusRoundIdentifier roundId = new ConsensusRoundIdentifier(PREV_BLOCK_NUMBER + 1, 0);

    final Address nextProposer = uut.selectProposerForRound(roundId);

    assertThat(nextProposer).isEqualTo(validatorList.get(1));
  }

  @Test
  public void lastValidatorInListValidatedPreviousBlockSoFirstIsNextProposer() {
    final long PREV_BLOCK_NUMBER = 2;
    final Address localAddr = AddressHelpers.ofValue(10); // arbitrarily selected

    final List<Address> validatorList = createValidatorList(localAddr, 4, 0);
    final Blockchain blockchain =
        createMockedBlockChainWithHeadOf(PREV_BLOCK_NUMBER, localAddr, validatorList);

    final ProposerSelector uut =
        new ProposerSelector(blockchain, blockInterface, true, validatorProvider);

    final ConsensusRoundIdentifier roundId = new ConsensusRoundIdentifier(PREV_BLOCK_NUMBER + 1, 0);

    final Address nextProposer = uut.selectProposerForRound(roundId);

    assertThat(nextProposer).isEqualTo(validatorList.get(0));
  }

  @Test
  public void stickyProposerDoesNotChangeOnRoundZeroOfNextBlock() {
    final long PREV_BLOCK_NUMBER = 2;
    final ConsensusRoundIdentifier roundId = new ConsensusRoundIdentifier(PREV_BLOCK_NUMBER + 1, 0);

    final Address localAddr = AddressHelpers.ofValue(10); // arbitrarily selected
    final List<Address> validatorList = createValidatorList(localAddr, 4, 0);
    final Blockchain blockchain =
        createMockedBlockChainWithHeadOf(PREV_BLOCK_NUMBER, localAddr, validatorList);

    final ProposerSelector uut =
        new ProposerSelector(blockchain, blockInterface, false, validatorProvider);
    final Address nextProposer = uut.selectProposerForRound(roundId);

    assertThat(nextProposer).isEqualTo(localAddr);
  }

  @Test
  public void stickyProposerChangesOnSubsequentRoundsAtSameBlockHeight() {
    final long PREV_BLOCK_NUMBER = 2;
    ConsensusRoundIdentifier roundId = new ConsensusRoundIdentifier(PREV_BLOCK_NUMBER + 1, 0);

    final Address localAddr = AddressHelpers.ofValue(10); // arbitrarily selected

    final List<Address> validatorList = createValidatorList(localAddr, 4, 0);
    final Blockchain blockchain =
        createMockedBlockChainWithHeadOf(PREV_BLOCK_NUMBER, localAddr, validatorList);

    final ProposerSelector uut =
        new ProposerSelector(blockchain, blockInterface, false, validatorProvider);
    assertThat(uut.selectProposerForRound(roundId)).isEqualTo(localAddr);

    roundId = new ConsensusRoundIdentifier(PREV_BLOCK_NUMBER + 1, 1);
    assertThat(uut.selectProposerForRound(roundId)).isEqualTo(validatorList.get(0));

    roundId = new ConsensusRoundIdentifier(PREV_BLOCK_NUMBER + 1, 2);
    assertThat(uut.selectProposerForRound(roundId)).isEqualTo(validatorList.get(1));
  }

  @Test
  public void whenProposerSelfRemovesSelectsNextProposerInLineEvenWhenSticky() {
    final long PREV_BLOCK_NUMBER = 2;
    final ConsensusRoundIdentifier roundId = new ConsensusRoundIdentifier(PREV_BLOCK_NUMBER + 1, 0);

    final Address localAddr = AddressHelpers.ofValue(10); // arbitrarily selected

    // LocalAddr will be in index 2 - the next proposer will also be in 2 (as prev proposer is
    // removed)
    final List<Address> validatorList = createValidatorList(localAddr, 2, 2);
    validatorList.remove(localAddr);

    // Note the signer of the Previous block was not included.
    final Blockchain blockchain =
        createMockedBlockChainWithHeadOf(PREV_BLOCK_NUMBER, localAddr, validatorList);

    final ProposerSelector uut =
        new ProposerSelector(blockchain, blockInterface, false, validatorProvider);

    assertThat(uut.selectProposerForRound(roundId)).isEqualTo(validatorList.get(2));
  }

  @Test
  public void whenProposerSelfRemovesSelectsNextProposerInLineEvenWhenRoundRobin() {
    final long PREV_BLOCK_NUMBER = 2;
    final ConsensusRoundIdentifier roundId = new ConsensusRoundIdentifier(PREV_BLOCK_NUMBER + 1, 0);

    final Address localAddr = AddressHelpers.ofValue(10); // arbitrarily selected

    // LocalAddr will be in index 2 - the next proposer will also be in 2 (as prev proposer is
    // removed)
    final List<Address> validatorList = createValidatorList(localAddr, 2, 2);
    validatorList.remove(localAddr);

    final Blockchain blockchain =
        createMockedBlockChainWithHeadOf(PREV_BLOCK_NUMBER, localAddr, validatorList);

    final ProposerSelector uut =
        new ProposerSelector(blockchain, blockInterface, true, validatorProvider);

    assertThat(uut.selectProposerForRound(roundId)).isEqualTo(validatorList.get(2));
  }

  @Test
  public void proposerSelfRemovesAndHasHighestAddressNewProposerIsFirstInList() {
    final long PREV_BLOCK_NUMBER = 2;
    final ConsensusRoundIdentifier roundId = new ConsensusRoundIdentifier(PREV_BLOCK_NUMBER + 1, 0);

    final Address localAddr = AddressHelpers.ofValue(10); // arbitrarily selected

    // LocalAddr will be in index 2 - the next proposer will also be in 2 (as prev proposer is
    // removed)
    final List<Address> validatorList = createValidatorList(localAddr, 4, 0);
    validatorList.remove(localAddr);

    // Note the signer of the Previous block was not included.
    final Blockchain blockchain =
        createMockedBlockChainWithHeadOf(PREV_BLOCK_NUMBER, localAddr, validatorList);

    final ProposerSelector uut =
        new ProposerSelector(blockchain, blockInterface, false, validatorProvider);

    assertThat(uut.selectProposerForRound(roundId)).isEqualTo(validatorList.get(0));
  }
}
