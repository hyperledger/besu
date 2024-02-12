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
package org.hyperledger.besu.consensus.ibft;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.hyperledger.besu.consensus.common.bft.BftContextBuilder.setupContextWithBftExtraDataEncoder;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.config.BftConfigOptions;
import org.hyperledger.besu.config.GenesisConfigOptions;
import org.hyperledger.besu.config.JsonGenesisConfigOptions;
import org.hyperledger.besu.config.JsonQbftConfigOptions;
import org.hyperledger.besu.config.JsonUtil;
import org.hyperledger.besu.consensus.common.ForkSpec;
import org.hyperledger.besu.consensus.common.ForksSchedule;
import org.hyperledger.besu.consensus.common.bft.BftContext;
import org.hyperledger.besu.consensus.common.bft.BftExtraData;
import org.hyperledger.besu.consensus.common.bft.BftExtraDataCodec;
import org.hyperledger.besu.consensus.common.bft.BftProtocolSchedule;
import org.hyperledger.besu.consensus.common.bft.MutableBftConfigOptions;
import org.hyperledger.besu.cryptoservices.NodeKey;
import org.hyperledger.besu.cryptoservices.NodeKeyUtils;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.chain.BadBlockManager;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.MilestoneStreamingProtocolSchedule;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.core.Util;
import org.hyperledger.besu.ethereum.mainnet.HeaderValidationMode;
import org.hyperledger.besu.evm.internal.EvmConfiguration;

import java.math.BigInteger;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class IbftProtocolScheduleTest {
  private final BftExtraDataCodec bftExtraDataCodec = mock(BftExtraDataCodec.class);
  private final BftExtraData bftExtraData = mock(BftExtraData.class);
  private final NodeKey proposerNodeKey = NodeKeyUtils.generate();
  private final Address proposerAddress = Util.publicKeyToAddress(proposerNodeKey.getPublicKey());
  private final List<Address> validators = singletonList(proposerAddress);

  @BeforeEach
  public void setup() {
    when(bftExtraDataCodec.decode(any())).thenReturn(bftExtraData);
    when(bftExtraData.getValidators()).thenReturn(validators);
  }

  @Test
  public void blockModeTransitionsCreatesBlockModeHeaderValidators() {
    final MutableBftConfigOptions arbitraryTransition =
        new MutableBftConfigOptions(JsonQbftConfigOptions.DEFAULT);
    arbitraryTransition.setBlockRewardWei(BigInteger.ONE);

    final BlockHeader parentHeader =
        IbftBlockHeaderUtils.createPresetHeaderBuilder(1, proposerNodeKey, validators, null)
            .buildHeader();
    final BlockHeader blockHeader =
        IbftBlockHeaderUtils.createPresetHeaderBuilder(2, proposerNodeKey, validators, parentHeader)
            .buildHeader();

    final BftProtocolSchedule schedule =
        createProtocolSchedule(
            JsonGenesisConfigOptions.fromJsonObject(JsonUtil.createEmptyObjectNode()),
            List.of(
                new ForkSpec<>(0, JsonQbftConfigOptions.DEFAULT),
                new ForkSpec<>(1, arbitraryTransition),
                new ForkSpec<>(2, JsonQbftConfigOptions.DEFAULT)));
    assertThat(new MilestoneStreamingProtocolSchedule(schedule).streamMilestoneBlocks().count())
        .isEqualTo(3);
    assertThat(validateHeader(schedule, validators, parentHeader, blockHeader, 0)).isTrue();
    assertThat(validateHeader(schedule, validators, parentHeader, blockHeader, 1)).isTrue();
    assertThat(validateHeader(schedule, validators, parentHeader, blockHeader, 2)).isTrue();
  }

  private BftProtocolSchedule createProtocolSchedule(
      final GenesisConfigOptions genesisConfig, final List<ForkSpec<BftConfigOptions>> forks) {
    return IbftProtocolScheduleBuilder.create(
        genesisConfig,
        new ForksSchedule<>(forks),
        PrivacyParameters.DEFAULT,
        false,
        bftExtraDataCodec,
        EvmConfiguration.DEFAULT,
        new BadBlockManager());
  }

  private boolean validateHeader(
      final BftProtocolSchedule schedule,
      final List<Address> validators,
      final BlockHeader parentHeader,
      final BlockHeader blockHeader,
      final int block) {
    return schedule
        .getByBlockNumberOrTimestamp(block, blockHeader.getTimestamp())
        .getBlockHeaderValidator()
        .validateHeader(
            blockHeader, parentHeader, protocolContext(validators), HeaderValidationMode.LIGHT);
  }

  private ProtocolContext protocolContext(final Collection<Address> validators) {
    return new ProtocolContext(
        null,
        null,
        setupContextWithBftExtraDataEncoder(BftContext.class, validators, bftExtraDataCodec),
        Optional.empty());
  }
}
