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
 *
 */
package org.hyperledger.besu.evmtool;

import org.hyperledger.besu.config.GenesisConfigOptions;
import org.hyperledger.besu.config.StubGenesisConfigOptions;
import org.hyperledger.besu.ethereum.core.BlockHeaderFunctions;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.linea.LineaParameters;
import org.hyperledger.besu.ethereum.mainnet.HeaderBasedProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.MainnetBlockHeaderFunctions;
import org.hyperledger.besu.ethereum.mainnet.MainnetProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolScheduleBuilder;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpecAdapters;
import org.hyperledger.besu.ethereum.mainnet.TimestampScheduleBuilder;
import org.hyperledger.besu.evm.internal.EvmConfiguration;

import java.math.BigInteger;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.inject.Named;

class MainnetGenesisFileModule extends GenesisFileModule {

  MainnetGenesisFileModule(final String genesisConfig) {
    super(genesisConfig);
  }

  @Override
  BlockHeaderFunctions blockHashFunction() {
    return new MainnetBlockHeaderFunctions();
  }

  @Override
  HeaderBasedProtocolSchedule provideProtocolSchedule(
      final GenesisConfigOptions configOptions,
      @Named("Fork") final Optional<String> fork,
      @Named("RevertReasonEnabled") final boolean revertReasonEnabled) {
    if (fork.isPresent()) {
      var schedules = createSchedules();
      var schedule = schedules.get(fork.map(String::toLowerCase).get());
      if (schedule != null) {
        return schedule.get();
      }
    }
    return MainnetProtocolSchedule.fromConfig(configOptions, EvmConfiguration.DEFAULT);
  }

  public static Map<String, Supplier<HeaderBasedProtocolSchedule>> createSchedules() {
    return Map.ofEntries(
        Map.entry("frontier", createSchedule(new StubGenesisConfigOptions())),
        Map.entry("homestead", createSchedule(new StubGenesisConfigOptions().homesteadBlock(0))),
        Map.entry("eip150", createSchedule(new StubGenesisConfigOptions().eip150Block(0))),
        Map.entry("eip158", createSchedule(new StubGenesisConfigOptions().eip158Block(0))),
        Map.entry("byzantium", createSchedule(new StubGenesisConfigOptions().byzantiumBlock(0))),
        Map.entry(
            "constantinople",
            createSchedule(new StubGenesisConfigOptions().constantinopleBlock(0))),
        Map.entry(
            "constantinoplefix", createSchedule(new StubGenesisConfigOptions().petersburgBlock(0))),
        Map.entry("petersburg", createSchedule(new StubGenesisConfigOptions().petersburgBlock(0))),
        Map.entry("istanbul", createSchedule(new StubGenesisConfigOptions().istanbulBlock(0))),
        Map.entry(
            "muirglacier", createSchedule(new StubGenesisConfigOptions().muirGlacierBlock(0))),
        Map.entry("berlin", createSchedule(new StubGenesisConfigOptions().berlinBlock(0))),
        Map.entry(
            "london",
            createSchedule(new StubGenesisConfigOptions().londonBlock(0).baseFeePerGas(0x0a))),
        Map.entry(
            "arrowglacier", createSchedule(new StubGenesisConfigOptions().arrowGlacierBlock(0))),
        Map.entry(
            "grayglacier", createSchedule(new StubGenesisConfigOptions().grayGlacierBlock(0))),
        Map.entry(
            "merge",
            createSchedule(
                new StubGenesisConfigOptions().mergeNetSplitBlock(0).baseFeePerGas(0x0a))),
        Map.entry(
            "shanghai",
            createTimestampSchedule(
                new StubGenesisConfigOptions().shanghaiTime(0).baseFeePerGas(0x0a))),
        Map.entry(
            "cancun",
            createTimestampSchedule(
                new StubGenesisConfigOptions().cancunTime(0).baseFeePerGas(0x0a))),
        Map.entry(
            "futureeips",
            createTimestampSchedule(
                new StubGenesisConfigOptions().futureEipsTime(0).baseFeePerGas(0x0a))),
        Map.entry(
            "experimentaleips",
            createTimestampSchedule(
                new StubGenesisConfigOptions().experimentalEipsTime(0).baseFeePerGas(0x0a))));
  }

  private static Supplier<HeaderBasedProtocolSchedule> createSchedule(
      final GenesisConfigOptions options) {
    return () ->
        new ProtocolScheduleBuilder(
                options,
                options.getChainId().orElse(BigInteger.ONE),
                ProtocolSpecAdapters.create(0, Function.identity()),
                PrivacyParameters.DEFAULT,
                false,
                options.isQuorum(),
                EvmConfiguration.DEFAULT)
            .createProtocolSchedule();
  }

  private static Supplier<HeaderBasedProtocolSchedule> createTimestampSchedule(
      final GenesisConfigOptions options) {
    return () ->
        new TimestampScheduleBuilder(
                options,
                options.getChainId().orElse(BigInteger.ONE),
                ProtocolSpecAdapters.create(0, Function.identity()),
                PrivacyParameters.DEFAULT,
                false,
                options.isQuorum(),
                EvmConfiguration.DEFAULT,
                LineaParameters.DEFAULT)
            .createTimestampSchedule();
  }
}
