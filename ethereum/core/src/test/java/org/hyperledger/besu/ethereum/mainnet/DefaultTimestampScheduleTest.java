/*
 * Copyright contributors to Hyperledger Besu.
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

package org.hyperledger.besu.ethereum.mainnet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.hyperledger.besu.config.StubGenesisConfigOptions;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderBuilder;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.evm.internal.EvmConfiguration;

import java.math.BigInteger;
import java.util.Optional;
import java.util.function.Function;

import org.junit.Before;
import org.junit.Test;

public class DefaultTimestampScheduleTest {

  private static final BigInteger chainId = BigInteger.ONE;
  private static final BigInteger defaultChainId = BigInteger.ONE;
  private static final PrivacyParameters privacyParameters = new PrivacyParameters();
  private static final EvmConfiguration evmConfiguration = EvmConfiguration.DEFAULT;
  private static final BlockHeader BLOCK_HEADER =
      new BlockHeaderTestFixture().timestamp(1L).buildHeader();
  private TimestampScheduleBuilder builder;
  private StubGenesisConfigOptions config;

  private final Function<ProtocolSpecBuilder, ProtocolSpecBuilder> modifier = Function.identity();

  private final long FIRST_TIMESTAMP_FORK = 1L;

  @Before
  public void setup() {
    config = new StubGenesisConfigOptions();
    config.chainId(chainId);
    boolean isRevertReasonEnabled = false;
    builder =
        new TimestampScheduleBuilder(
            config,
            defaultChainId,
            ProtocolSpecAdapters.create(FIRST_TIMESTAMP_FORK, modifier),
            privacyParameters,
            isRevertReasonEnabled,
            evmConfiguration);
  }

  @Test
  public void getByBlockHeader_whenSpecFound() {
    config.shanghaiTime(FIRST_TIMESTAMP_FORK);
    final TimestampSchedule schedule = builder.createTimestampSchedule();

    assertThat(schedule.getByBlockHeader(BLOCK_HEADER)).isNotNull();
  }

  @Test
  public void getByBlockHeader_whenSpecNotFoundReturnsNull() {
    config.shanghaiTime(2L);
    builder =
        new TimestampScheduleBuilder(
            config,
            defaultChainId,
            ProtocolSpecAdapters.create(2L, modifier),
            privacyParameters,
            false,
            evmConfiguration);
    final TimestampSchedule schedule = builder.createTimestampSchedule();

    assertThat(schedule.getByBlockHeader(BLOCK_HEADER)).isNull();
  }

  @Test
  public void isOnMilestoneBoundary() {
    config.shanghaiTime(FIRST_TIMESTAMP_FORK);
    config.cancunTime(2L);
    config.experimentalEipsTime(4L);
    final HeaderBasedProtocolSchedule protocolSchedule = builder.createTimestampSchedule();

    assertThat(protocolSchedule.isOnMilestoneBoundary(header(0))).isEqualTo(false);
    assertThat(protocolSchedule.isOnMilestoneBoundary(header(FIRST_TIMESTAMP_FORK)))
        .isEqualTo(true);
    assertThat(protocolSchedule.isOnMilestoneBoundary(header(2))).isEqualTo(true);
    assertThat(protocolSchedule.isOnMilestoneBoundary(header(3))).isEqualTo(false);
    assertThat(protocolSchedule.isOnMilestoneBoundary(header(4))).isEqualTo(true);
  }

  @Test
  public void getForNextBlockHeader_shouldGetHeaderForNextTimestamp() {
    final ProtocolSpec spec1 = mock(ProtocolSpec.class);
    final ProtocolSpec spec2 = mock(ProtocolSpec.class);

    final TimestampSchedule protocolSchedule = new DefaultTimestampSchedule(Optional.of(chainId));
    protocolSchedule.putMilestone(0, spec1);
    protocolSchedule.putMilestone(1000, spec2);

    final BlockHeader blockHeader =
        BlockHeaderBuilder.createDefault().number(0L).buildBlockHeader();
    final ProtocolSpec spec = protocolSchedule.getForNextBlockHeader(blockHeader, 1000);

    assertThat(spec).isEqualTo(spec2);
  }

  private BlockHeader header(final long timestamp) {
    return new BlockHeaderTestFixture().timestamp(timestamp).buildHeader();
  }
}
