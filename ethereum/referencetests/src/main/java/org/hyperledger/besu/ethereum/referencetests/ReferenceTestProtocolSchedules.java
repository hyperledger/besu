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
package org.hyperledger.besu.ethereum.referencetests;

import org.hyperledger.besu.config.GenesisConfigOptions;
import org.hyperledger.besu.config.StubGenesisConfigOptions;
import org.hyperledger.besu.ethereum.chain.BadBlockManager;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.MiningParameters;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolScheduleBuilder;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpecAdapters;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.evm.precompile.KZGPointEvalPrecompiledContract;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ReferenceTestProtocolSchedules {

  private static final BigInteger CHAIN_ID = BigInteger.ONE;

  private static final List<String> SPECS_PRIOR_TO_DELETING_EMPTY_ACCOUNTS =
      Arrays.asList("Frontier", "Homestead", "EIP150");

  private static ReferenceTestProtocolSchedules instance;

  public static ReferenceTestProtocolSchedules getInstance() {
    if (instance == null) {
      instance = create();
    }
    return instance;
  }

  public static ReferenceTestProtocolSchedules create() {
    return create(new StubGenesisConfigOptions());
  }

  public static ReferenceTestProtocolSchedules create(final StubGenesisConfigOptions genesisStub) {
    // the following schedules activate EIP-1559, but may have non-default
    if (genesisStub.getBaseFeePerGas().isEmpty()) {
      genesisStub.baseFeePerGas(0x0a);
    }
    // also load KZG file for mainnet
    KZGPointEvalPrecompiledContract.init();
    return new ReferenceTestProtocolSchedules(
        Map.ofEntries(
                Map.entry("Frontier", createSchedule(genesisStub.clone())),
                Map.entry(
                    "FrontierToHomesteadAt5",
                    createSchedule(genesisStub.clone().homesteadBlock(5))),
                Map.entry("Homestead", createSchedule(genesisStub.clone().homesteadBlock(0))),
                Map.entry(
                    "HomesteadToEIP150At5",
                    createSchedule(genesisStub.clone().homesteadBlock(0).eip150Block(5))),
                Map.entry(
                    "HomesteadToDaoAt5",
                    createSchedule(genesisStub.clone().homesteadBlock(0).daoForkBlock(5))),
                Map.entry("EIP150", createSchedule(genesisStub.clone().eip150Block(0))),
                Map.entry("EIP158", createSchedule(genesisStub.clone().eip158Block(0))),
                Map.entry(
                    "EIP158ToByzantiumAt5",
                    createSchedule(genesisStub.clone().eip158Block(0).byzantiumBlock(5))),
                Map.entry("Byzantium", createSchedule(genesisStub.clone().byzantiumBlock(0))),
                Map.entry(
                    "Constantinople", createSchedule(genesisStub.clone().constantinopleBlock(0))),
                Map.entry(
                    "ConstantinopleFix", createSchedule(genesisStub.clone().petersburgBlock(0))),
                Map.entry("Petersburg", createSchedule(genesisStub.clone().petersburgBlock(0))),
                Map.entry("Istanbul", createSchedule(genesisStub.clone().istanbulBlock(0))),
                Map.entry("MuirGlacier", createSchedule(genesisStub.clone().muirGlacierBlock(0))),
                Map.entry("Berlin", createSchedule(genesisStub.clone().berlinBlock(0))),
                Map.entry("London", createSchedule(genesisStub.clone().londonBlock(0))),
                Map.entry("ArrowGlacier", createSchedule(genesisStub.clone().arrowGlacierBlock(0))),
                Map.entry("GrayGlacier", createSchedule(genesisStub.clone().grayGlacierBlock(0))),
                Map.entry("Merge", createSchedule(genesisStub.clone().mergeNetSplitBlock(0))),
                Map.entry("Paris", createSchedule(genesisStub.clone().mergeNetSplitBlock(0))),
                Map.entry("Shanghai", createSchedule(genesisStub.clone().shanghaiTime(0))),
                Map.entry(
                    "ShanghaiToCancunAtTime15k",
                    createSchedule(genesisStub.clone().shanghaiTime(0).cancunTime(15000))),
                Map.entry("Cancun", createSchedule(genesisStub.clone().cancunTime(0))),
                Map.entry("CancunEOF", createSchedule(genesisStub.clone().cancunEOFTime(0))),
                Map.entry(
                    "CancunToPragueAtTime15k",
                    createSchedule(genesisStub.clone().cancunTime(0).pragueTime(15000))),
                Map.entry("Prague", createSchedule(genesisStub.clone().pragueEOFTime(0))),
                Map.entry("Osaka", createSchedule(genesisStub.clone().futureEipsTime(0))),
                Map.entry("Amsterdam", createSchedule(genesisStub.clone().futureEipsTime(0))),
                Map.entry("Bogota", createSchedule(genesisStub.clone().futureEipsTime(0))),
                Map.entry("Polis", createSchedule(genesisStub.clone().futureEipsTime(0))),
                Map.entry("Bangkok", createSchedule(genesisStub.clone().futureEipsTime(0))),
                Map.entry("Future_EIPs", createSchedule(genesisStub.clone().futureEipsTime(0))),
                Map.entry(
                    "Experimental_EIPs",
                    createSchedule(genesisStub.clone().experimentalEipsTime(0))))
            .entrySet()
            .stream()
            .map(e -> Map.entry(e.getKey().toLowerCase(Locale.ROOT), e.getValue()))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
  }

  private final Map<String, ProtocolSchedule> schedules;

  private ReferenceTestProtocolSchedules(final Map<String, ProtocolSchedule> schedules) {
    this.schedules = schedules;
  }

  public ProtocolSchedule getByName(final String name) {
    return schedules.get(name.toLowerCase(Locale.ROOT));
  }

  public ProtocolSpec geSpecByName(final String name) {
    ProtocolSchedule schedule = getByName(name);
    if (schedule == null) {
      return null;
    }
    BlockHeader header =
        new BlockHeaderTestFixture().timestamp(Long.MAX_VALUE).number(Long.MAX_VALUE).buildHeader();
    return schedule.getByBlockHeader(header);
  }

  private static ProtocolSchedule createSchedule(final GenesisConfigOptions options) {
    return new ProtocolScheduleBuilder(
            options,
            CHAIN_ID,
            ProtocolSpecAdapters.create(0, Function.identity()),
            PrivacyParameters.DEFAULT,
            false,
            EvmConfiguration.DEFAULT,
            MiningParameters.MINING_DISABLED,
            new BadBlockManager(),
            false,
            new NoOpMetricsSystem())
        .createProtocolSchedule();
  }

  public static boolean shouldClearEmptyAccounts(final String fork) {
    return !SPECS_PRIOR_TO_DELETING_EMPTY_ACCOUNTS.contains(fork);
  }
}
