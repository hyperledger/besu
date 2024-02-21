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
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.crypto.SignatureAlgorithmType;
import org.hyperledger.besu.ethereum.chain.BadBlockManager;
import org.hyperledger.besu.ethereum.core.BlockHeaderFunctions;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.mainnet.MainnetBlockHeaderFunctions;
import org.hyperledger.besu.ethereum.mainnet.MainnetProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolScheduleBuilder;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpecAdapters;
import org.hyperledger.besu.evm.internal.EvmConfiguration;

import java.math.BigInteger;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.inject.Named;

import picocli.CommandLine;

class MainnetGenesisFileModule extends GenesisFileModule {

  MainnetGenesisFileModule(final String genesisConfig) {
    super(genesisConfig);
  }

  @Override
  BlockHeaderFunctions blockHashFunction() {
    return new MainnetBlockHeaderFunctions();
  }

  @Override
  ProtocolSchedule provideProtocolSchedule(
      final GenesisConfigOptions configOptions,
      @Named("Fork") final Optional<String> fork,
      @Named("RevertReasonEnabled") final boolean revertReasonEnabled,
      final EvmConfiguration evmConfiguration) {

    final Optional<String> ecCurve = configOptions.getEcCurve();
    if (ecCurve.isEmpty()) {
      SignatureAlgorithmFactory.setDefaultInstance();
    } else {
      try {
        SignatureAlgorithmFactory.setInstance(SignatureAlgorithmType.create(ecCurve.get()));
      } catch (final IllegalArgumentException e) {
        throw new CommandLine.InitializationException(
            "Invalid genesis file configuration for ecCurve. " + e.getMessage());
      }
    }

    if (fork.isPresent()) {
      var schedules = createSchedules();
      var schedule = schedules.get(fork.map(String::toLowerCase).get());
      if (schedule != null) {
        return schedule.get();
      }
    }
    return MainnetProtocolSchedule.fromConfig(
        configOptions, evmConfiguration, new BadBlockManager());
  }

  public static Map<String, Supplier<ProtocolSchedule>> createSchedules() {
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
            createSchedule(new StubGenesisConfigOptions().shanghaiTime(0).baseFeePerGas(0x0a))),
        Map.entry(
            "cancun",
            createSchedule(new StubGenesisConfigOptions().cancunTime(0).baseFeePerGas(0x0a))),
        Map.entry(
            "prague",
            createSchedule(new StubGenesisConfigOptions().pragueTime(0).baseFeePerGas(0x0a))),
        Map.entry(
            "futureeips",
            createSchedule(new StubGenesisConfigOptions().futureEipsTime(0).baseFeePerGas(0x0a))),
        Map.entry(
            "experimentaleips",
            createSchedule(
                new StubGenesisConfigOptions().experimentalEipsTime(0).baseFeePerGas(0x0a))));
  }

  private static Supplier<ProtocolSchedule> createSchedule(final GenesisConfigOptions options) {
    return () ->
        new ProtocolScheduleBuilder(
                options,
                options.getChainId().orElse(BigInteger.ONE),
                ProtocolSpecAdapters.create(0, Function.identity()),
                PrivacyParameters.DEFAULT,
                false,
                EvmConfiguration.DEFAULT,
                new BadBlockManager())
            .createProtocolSchedule();
  }
}
