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
package org.hyperledger.besu.consensus.clique.blockcreation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.consensus.common.validator.ValidatorProvider;
import org.hyperledger.besu.crypto.KeyPair;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.blockcreation.AbstractBlockScheduler.BlockCreationTimeResult;
import org.hyperledger.besu.ethereum.core.AddressHelpers;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.Util;

import java.time.Clock;
import java.util.List;

import com.google.common.collect.Lists;
import org.junit.Before;
import org.junit.Test;

public class CliqueBlockSchedulerTest {

  private final KeyPair proposerKeyPair = SignatureAlgorithmFactory.getInstance().generateKeyPair();
  private Address localAddr;

  private final List<Address> validatorList = Lists.newArrayList();
  private ValidatorProvider validatorProvider;
  private BlockHeaderTestFixture blockHeaderBuilder;

  @Before
  public void setup() {
    localAddr = Util.publicKeyToAddress(proposerKeyPair.getPublicKey());

    validatorList.add(localAddr);
    validatorList.add(AddressHelpers.calculateAddressWithRespectTo(localAddr, 1));

    validatorProvider = mock(ValidatorProvider.class);
    when(validatorProvider.getValidatorsAfterBlock(any())).thenReturn(validatorList);

    blockHeaderBuilder = new BlockHeaderTestFixture();
  }

  @Test
  public void inturnValidatorWaitsExactlyBlockInterval() {
    final Clock clock = mock(Clock.class);
    final long currentSecondsSinceEpoch = 10L;
    final long secondsBetweenBlocks = 5L;
    when(clock.millis()).thenReturn(currentSecondsSinceEpoch * 1000);
    final CliqueBlockScheduler scheduler =
        new CliqueBlockScheduler(clock, validatorProvider, localAddr, secondsBetweenBlocks);

    // There are 2 validators, therefore block 2 will put localAddr as the in-turn voter, therefore
    // parent block should be number 1.
    final BlockHeader parentHeader =
        blockHeaderBuilder.number(1).timestamp(currentSecondsSinceEpoch).buildHeader();

    final BlockCreationTimeResult result = scheduler.getNextTimestamp(parentHeader);

    assertThat(result.getTimestampForHeader())
        .isEqualTo(currentSecondsSinceEpoch + secondsBetweenBlocks);
    assertThat(result.getMillisecondsUntilValid()).isEqualTo(secondsBetweenBlocks * 1000);
  }

  @Test
  public void outOfturnValidatorWaitsLongerThanBlockInterval() {
    final Clock clock = mock(Clock.class);
    final long currentSecondsSinceEpoch = 10L;
    final long secondsBetweenBlocks = 5L;
    when(clock.millis()).thenReturn(currentSecondsSinceEpoch * 1000);
    final CliqueBlockScheduler scheduler =
        new CliqueBlockScheduler(clock, validatorProvider, localAddr, secondsBetweenBlocks);

    // There are 2 validators, therefore block 3 will put localAddr as the out-turn voter, therefore
    // parent block should be number 2.
    final BlockHeader parentHeader =
        blockHeaderBuilder.number(2).timestamp(currentSecondsSinceEpoch).buildHeader();

    final BlockCreationTimeResult result = scheduler.getNextTimestamp(parentHeader);

    assertThat(result.getTimestampForHeader())
        .isEqualTo(currentSecondsSinceEpoch + secondsBetweenBlocks);
    assertThat(result.getMillisecondsUntilValid()).isGreaterThan(secondsBetweenBlocks * 1000);
  }

  @Test
  public void inTurnValidatorCreatesBlockNowIFParentTimestampSufficientlyBehindNow() {
    final Clock clock = mock(Clock.class);
    final long currentSecondsSinceEpoch = 10L;
    final long secondsBetweenBlocks = 5L;
    when(clock.millis()).thenReturn(currentSecondsSinceEpoch * 1000);
    final CliqueBlockScheduler scheduler =
        new CliqueBlockScheduler(clock, validatorProvider, localAddr, secondsBetweenBlocks);

    // There are 2 validators, therefore block 2 will put localAddr as the in-turn voter, therefore
    // parent block should be number 1.
    final BlockHeader parentHeader =
        blockHeaderBuilder
            .number(1)
            .timestamp(currentSecondsSinceEpoch - secondsBetweenBlocks)
            .buildHeader();

    final BlockCreationTimeResult result = scheduler.getNextTimestamp(parentHeader);

    assertThat(result.getTimestampForHeader()).isEqualTo(currentSecondsSinceEpoch);
    assertThat(result.getMillisecondsUntilValid()).isEqualTo(0);
  }
}
