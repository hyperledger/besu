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

import org.hyperledger.besu.consensus.common.bft.ConsensusRoundIdentifier;
import org.hyperledger.besu.consensus.common.bft.events.BlockTimerExpiry;
import org.hyperledger.besu.consensus.common.bft.inttest.NodeParams;
import org.hyperledger.besu.consensus.common.validator.ValidatorProvider;
import org.hyperledger.besu.consensus.qbft.support.TestContext;
import org.hyperledger.besu.consensus.qbft.support.TestContextBuilder;
import org.hyperledger.besu.crypto.NodeKeyUtils;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.BlockHeader;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

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

  // Configuration ensures unit under test will be responsible for sending first proposal
  @SuppressWarnings("UnstableApiUsage")
  private final TestContext context =
      new TestContextBuilder()
          .indexOfFirstLocallyProposedBlock(0)
          .nodeParams(
              List.of(new NodeParams(NODE_ADDRESS, NodeKeyUtils.createFrom(NODE_PRIVATE_KEY))))
          .clock(fixedClock)
          .genesisFile(Resources.getResource("genesis_validator_contract.json").getFile())
          .useValidatorContract(true)
          .buildAndStart();

  private final ConsensusRoundIdentifier roundId = new ConsensusRoundIdentifier(1, 0);

  @Test
  public void retrievesValidatorsFromValidatorContract() {
    // create new block
    context.getController().handleBlockTimerExpiry(new BlockTimerExpiry(roundId));
    assertThat(context.getBlockchain().getChainHeadBlockNumber()).isEqualTo(1);

    final ValidatorProvider validatorProvider = context.getValidatorProvider();
    final BlockHeader genesisBlock = context.getBlockchain().getBlockHeader(0).get();
    final BlockHeader block1 = context.getBlockchain().getBlockHeader(1).get();
    assertThat(validatorProvider.getValidatorsForBlock(genesisBlock)).containsExactly(NODE_ADDRESS);
    assertThat(validatorProvider.getValidatorsForBlock(block1)).containsExactly(NODE_ADDRESS);
  }
}
