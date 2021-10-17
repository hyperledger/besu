/*
 * Copyright Hyperledger Besu Contributors.
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

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.config.BftConfigOptions;
import org.hyperledger.besu.config.JsonQbftConfigOptions;
import org.hyperledger.besu.config.QbftConfigOptions;
import org.hyperledger.besu.consensus.common.bft.BftForkSpec;
import org.hyperledger.besu.consensus.common.bft.BftForksSchedule;
import org.hyperledger.besu.consensus.qbft.MutableQbftConfigOptions;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;

import java.util.Optional;
import java.util.function.Consumer;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ValidatorModeTransitionLoggerTest {

  @Mock private BftForksSchedule<BftConfigOptions> bftForksSchedule;

  @Mock private Consumer<String> msgConsumer;

  @InjectMocks private ValidatorModeTransitionLogger qbftTransitionNotifier;

  @Test
  public void doNotLogMessageWhenTransitioningFromBlockHeaderToBlockHeader() {
    BftForkSpec<BftConfigOptions> forkSpecA =
        new BftForkSpec<>(0, createQbftConfigOptionsForBlockHeader());
    BftForkSpec<BftConfigOptions> forkSpecB =
        new BftForkSpec<>(1, createQbftConfigOptionsForBlockHeader());

    when(bftForksSchedule.getFork(0)).thenReturn(forkSpecA);
    when(bftForksSchedule.getFork(1)).thenReturn(forkSpecB);

    qbftTransitionNotifier.logTransitionChange(blockHeader(0));

    verifyNoInteractions(msgConsumer);
  }

  @Test
  public void doNotLogMessageWhenTransitioningFromContractToContractWithSameAddress() {
    BftForkSpec<BftConfigOptions> contractForkSpecA =
        new BftForkSpec<>(0, createQbftConfigOptionsForContract("0x0"));
    BftForkSpec<BftConfigOptions> contractForkSpecB =
        new BftForkSpec<>(1, createQbftConfigOptionsForContract("0x0"));

    when(bftForksSchedule.getFork(0)).thenReturn(contractForkSpecA);
    when(bftForksSchedule.getFork(1)).thenReturn(contractForkSpecB);

    qbftTransitionNotifier.logTransitionChange(blockHeader(0));

    verifyNoInteractions(msgConsumer);
  }

  @Test
  public void logMessageWhenTransitioningFromContractToContractWithDifferentAddress() {
    BftForkSpec<BftConfigOptions> contractForkSpecA =
        new BftForkSpec<>(0, createQbftConfigOptionsForContract("0x0"));
    BftForkSpec<BftConfigOptions> contractForkSpecB =
        new BftForkSpec<>(1, createQbftConfigOptionsForContract("0x1"));

    when(bftForksSchedule.getFork(0)).thenReturn(contractForkSpecA);
    when(bftForksSchedule.getFork(1)).thenReturn(contractForkSpecB);

    qbftTransitionNotifier.logTransitionChange(blockHeader(0));

    String expectedLog =
        "Transitioning validator selection mode from contract (address: 0x0) to contract (address: 0x1)";
    verify(msgConsumer).accept(eq(expectedLog));
  }

  @Test
  public void logMessageWhenTransitioningFromContractToBlockHeader() {
    BftForkSpec<BftConfigOptions> contractForkSpec =
        new BftForkSpec<>(0, createQbftConfigOptionsForContract("0x0"));
    BftForkSpec<BftConfigOptions> blockForkSpec =
        new BftForkSpec<>(1, createQbftConfigOptionsForBlockHeader());

    when(bftForksSchedule.getFork(0)).thenReturn(contractForkSpec);
    when(bftForksSchedule.getFork(1)).thenReturn(blockForkSpec);

    qbftTransitionNotifier.logTransitionChange(blockHeader(0));

    String expectedLog =
        "Transitioning validator selection mode from contract (address: 0x0) to blockheader";
    verify(msgConsumer).accept(eq(expectedLog));
  }

  @Test
  public void logMessageWhenTransitioningFromBlockHeaderToContract() {
    BftForkSpec<BftConfigOptions> blockForkSpec =
        new BftForkSpec<>(0, createQbftConfigOptionsForBlockHeader());
    BftForkSpec<BftConfigOptions> contractForkSpec =
        new BftForkSpec<>(1, createQbftConfigOptionsForContract("0x0"));

    when(bftForksSchedule.getFork(0)).thenReturn(blockForkSpec);
    when(bftForksSchedule.getFork(1)).thenReturn(contractForkSpec);

    qbftTransitionNotifier.logTransitionChange(blockHeader(0));

    String expectedLog =
        "Transitioning validator selection mode from blockheader to contract (address: 0x0)";
    verify(msgConsumer).accept(eq(expectedLog));
  }

  private QbftConfigOptions createQbftConfigOptionsForContract(final String address) {
    final MutableQbftConfigOptions qbftConfigOptions =
        new MutableQbftConfigOptions(JsonQbftConfigOptions.DEFAULT);
    qbftConfigOptions.setValidatorContractAddress(Optional.of(address));
    return qbftConfigOptions;
  }

  private QbftConfigOptions createQbftConfigOptionsForBlockHeader() {
    return new MutableQbftConfigOptions(JsonQbftConfigOptions.DEFAULT);
  }

  private BlockHeader blockHeader(final long blockNumber) {
    return new BlockHeaderTestFixture().number(blockNumber).buildHeader();
  }
}
