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
package org.hyperledger.besu.consensus.qbft.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.config.BftFork;
import org.hyperledger.besu.config.JsonUtil;
import org.hyperledger.besu.config.QbftFork;
import org.hyperledger.besu.config.QbftFork.VALIDATOR_SELECTION_MODE;
import org.hyperledger.besu.consensus.common.bft.ConsensusRoundIdentifier;
import org.hyperledger.besu.consensus.common.bft.events.BlockTimerExpiry;
import org.hyperledger.besu.consensus.common.bft.events.NewChainHead;
import org.hyperledger.besu.consensus.common.bft.inttest.NodeParams;
import org.hyperledger.besu.consensus.common.validator.ValidatorProvider;
import org.hyperledger.besu.consensus.qbft.QbftExtraDataCodec;
import org.hyperledger.besu.consensus.qbft.support.TestContext;
import org.hyperledger.besu.consensus.qbft.support.TestContextBuilder;
import org.hyperledger.besu.crypto.NodeKeyUtils;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.core.BlockHeader;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.common.io.Resources;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.Test;

public class ValidatorContractTest {

  public static final Address NODE_ADDRESS =
      Address.fromHexString("0xeac51e3fe1afc9894f0dfeab8ceb471899b932df");
  public static final Bytes32 NODE_PRIVATE_KEY =
      Bytes32.fromHexString("0xa3bdf521b0f286a80918c4b67000dfd2a2bdef97e94d268016ef9ec86648eac3");

  private final long blockTimeStamp = 100;
  private final Clock fixedClock =
      Clock.fixed(Instant.ofEpochSecond(blockTimeStamp), ZoneId.systemDefault());

  @Test
  public void retrievesValidatorsFromValidatorContract() {
    final TestContext context =
        new TestContextBuilder()
            .indexOfFirstLocallyProposedBlock(0)
            .nodeParams(
                List.of(new NodeParams(NODE_ADDRESS, NodeKeyUtils.createFrom(NODE_PRIVATE_KEY))))
            .clock(fixedClock)
            .genesisFile(Resources.getResource("genesis_validator_contract.json").getFile())
            .useValidatorContract(true)
            .buildAndStart();

    createNewBlock(context, 1);

    final ValidatorProvider validatorProvider = context.getValidatorProvider();
    final BlockHeader genesisBlock = context.getBlockchain().getBlockHeader(0).get();
    final BlockHeader block1 = context.getBlockchain().getBlockHeader(1).get();
    assertThat(validatorProvider.getValidatorsForBlock(genesisBlock)).containsExactly(NODE_ADDRESS);
    assertThat(validatorProvider.getValidatorsForBlock(block1)).containsExactly(NODE_ADDRESS);
  }

  @Test
  public void transitionsFromBlockHeaderModeToValidatorContractMode() {
    final QbftExtraDataCodec extraDataCodec = new QbftExtraDataCodec();
    final List<QbftFork> qbftForks =
        List.of(createContractFork(1, TestContextBuilder.VALIDATOR_CONTRACT_ADDRESS));
    final TestContext context =
        new TestContextBuilder()
            .indexOfFirstLocallyProposedBlock(0)
            .nodeParams(
                List.of(new NodeParams(NODE_ADDRESS, NodeKeyUtils.createFrom(NODE_PRIVATE_KEY))))
            .clock(fixedClock)
            .genesisFile(
                Resources.getResource("genesis_migrating_validator_contract.json").getFile())
            .useValidatorContract(false)
            .qbftForks(qbftForks)
            .buildAndStart();

    // block 0 uses block header voting with 1 validator
    // block 1 onwards uses validator contract with 2 validators
    final List<Address> block0Addresses = List.of(NODE_ADDRESS);
    final List<Address> block1Addresses =
        Stream.of(NODE_ADDRESS, Address.fromHexString("ca1c5ff73ed8370397114006dd1258e16433f41b"))
            .sorted()
            .collect(Collectors.toList());

    createNewBlock(context, 1);

    final ValidatorProvider validatorProvider = context.getValidatorProvider();
    final BlockHeader genesisBlock = context.getBlockchain().getBlockHeader(0).get();
    final BlockHeader block1 = context.getBlockchain().getBlockHeader(1).get();

    assertThat(validatorProvider.getValidatorsForBlock(genesisBlock)).isEqualTo(block0Addresses);
    assertThat(extraDataCodec.decode(genesisBlock).getValidators()).containsExactly(NODE_ADDRESS);

    // contract block extra data cannot contain validators or vote
    assertThat(validatorProvider.getValidatorsForBlock(block1)).isEqualTo(block1Addresses);
    assertThat(extraDataCodec.decode(block1).getValidators()).isEmpty();
    assertThat(extraDataCodec.decode(block1).getVote()).isEmpty();
  }

  @Test
  public void transitionsFromValidatorContractModeToBlockHeaderMode() {
    final QbftExtraDataCodec extraDataCodec = new QbftExtraDataCodec();
    final List<QbftFork> qbftForks = List.of(createBlockHeaderFork(1));
    final TestContext context =
        new TestContextBuilder()
            .indexOfFirstLocallyProposedBlock(0)
            .nodeParams(
                List.of(new NodeParams(NODE_ADDRESS, NodeKeyUtils.createFrom(NODE_PRIVATE_KEY))))
            .clock(fixedClock)
            .genesisFile(
                Resources.getResource("genesis_migrating_validator_blockheader.json").getFile())
            .useValidatorContract(true)
            .qbftForks(qbftForks)
            .buildAndStart();

    // block 0 uses validator contract with 1 validator
    // block 1 onwards uses block header voting (which reuses previous block's validators upon
    // switching)
    final List<Address> block0Addresses = Stream.of(NODE_ADDRESS).collect(Collectors.toList());

    createNewBlock(context, 1);

    final ValidatorProvider validatorProvider = context.getValidatorProvider();
    final BlockHeader genesisBlock = context.getBlockchain().getBlockHeader(0).get();
    final BlockHeader block1 = context.getBlockchain().getBlockHeader(1).get();

    // contract block extra data cannot contain validators or vote
    assertThat(validatorProvider.getValidatorsForBlock(genesisBlock)).isEqualTo(block0Addresses);
    assertThat(extraDataCodec.decode(genesisBlock).getValidators()).isEmpty();
    assertThat(extraDataCodec.decode(genesisBlock).getVote()).isEmpty();

    // uses previous block's validators
    assertThat(validatorProvider.getValidatorsForBlock(block1)).isEqualTo(block0Addresses);
    assertThat(extraDataCodec.decode(block1).getValidators()).containsExactly(NODE_ADDRESS);
    ;
  }

  @Test
  public void
      transitionsFromBlockHeaderModeToValidatorContractModeThenBackToBlockHeaderModeThenMinesAnotherBlock() {
    final QbftExtraDataCodec extraDataCodec = new QbftExtraDataCodec();
    final List<QbftFork> qbftForks =
        List.of(
            createContractFork(1, TestContextBuilder.VALIDATOR_CONTRACT_ADDRESS),
            createBlockHeaderFork(2));

    final TestContext context =
        new TestContextBuilder()
            .indexOfFirstLocallyProposedBlock(0)
            .nodeParams(
                List.of(new NodeParams(NODE_ADDRESS, NodeKeyUtils.createFrom(NODE_PRIVATE_KEY))))
            .clock(tickingClock())
            .genesisFile(
                Resources.getResource(
                        "genesis_migrating_validator_contract_with_one_validator.json")
                    .getFile())
            .useValidatorContract(false)
            .qbftForks(qbftForks)
            .buildAndStart();

    // block 0 uses block header voting with 1 validator
    // block 1 uses validator contract with 1 validator
    //   - in order for mining to work, it needs to be one validator (for quorum = 1) and _same_
    // validator (for finalState.isLocalNodeValidator() to be true)
    // block 2 uses block header voting with block 1's validators
    // block 3 uses block header voting with block 2's validators
    final List<Address> block0Addresses = List.of(NODE_ADDRESS);

    createNewBlock(context, 1L);
    createNewBlock(
        context,
        2L); // tickingClock moves forward to avoid the block failing TimestampMoreRecentThanParent
    // validation rule
    createNewBlock(
        context,
        3L); // tickingClock moves forward to avoid the block failing TimestampMoreRecentThanParent
    // validation rule

    final ValidatorProvider validatorProvider = context.getValidatorProvider();
    final BlockHeader genesisBlock = context.getBlockchain().getBlockHeader(0).get();
    final BlockHeader block1 = context.getBlockchain().getBlockHeader(1).get();
    final BlockHeader block2 = context.getBlockchain().getBlockHeader(2).get();

    assertThat(validatorProvider.getValidatorsForBlock(genesisBlock)).isEqualTo(block0Addresses);
    assertThat(extraDataCodec.decode(genesisBlock).getValidators()).containsExactly(NODE_ADDRESS);

    // contract block extra data cannot contain validators or vote
    assertThat(validatorProvider.getValidatorsForBlock(block1)).isEqualTo(block0Addresses);
    assertThat(extraDataCodec.decode(block1).getValidators()).isEmpty();
    assertThat(extraDataCodec.decode(block1).getVote()).isEmpty();

    assertThat(validatorProvider.getValidatorsForBlock(block2)).isEqualTo(block0Addresses);
    assertThat(extraDataCodec.decode(block2).getValidators()).containsExactly(NODE_ADDRESS);
  }

  private void createNewBlock(final TestContext context, final long blockNumber) {
    context
        .getController()
        .handleBlockTimerExpiry(new BlockTimerExpiry(new ConsensusRoundIdentifier(blockNumber, 0)));
    assertThat(context.getBlockchain().getChainHeadBlockNumber()).isEqualTo(blockNumber);
    context
        .getController()
        .handleNewBlockEvent(new NewChainHead(context.getBlockchain().getChainHeadHeader()));
  }

  private QbftFork createContractFork(final long block, final Address contractAddress) {
    return new QbftFork(
        JsonUtil.objectNodeFromMap(
            Map.of(
                BftFork.FORK_BLOCK_KEY,
                block,
                QbftFork.VALIDATOR_SELECTION_MODE_KEY,
                VALIDATOR_SELECTION_MODE.CONTRACT,
                QbftFork.VALIDATOR_CONTRACT_ADDRESS_KEY,
                contractAddress.toHexString())));
  }

  private QbftFork createBlockHeaderFork(final long block) {
    return new QbftFork(
        JsonUtil.objectNodeFromMap(
            Map.of(
                BftFork.FORK_BLOCK_KEY,
                block,
                QbftFork.VALIDATOR_SELECTION_MODE_KEY,
                VALIDATOR_SELECTION_MODE.BLOCKHEADER)));
  }

  private Clock tickingClock() {
    Clock tickingClock = mock(Clock.class);
    Instant blockInstant = Instant.ofEpochSecond(blockTimeStamp);
    when(tickingClock.millis())
        .thenReturn(blockInstant.toEpochMilli())
        .thenReturn(blockInstant.plusSeconds(1).toEpochMilli())
        .thenReturn(
            blockInstant.plusSeconds(2).toEpochMilli()); // supports creation of up to three blocks
    return tickingClock;
  }
}
