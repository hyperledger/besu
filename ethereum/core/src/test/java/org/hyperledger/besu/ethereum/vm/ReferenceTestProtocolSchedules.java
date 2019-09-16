/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.hyperledger.besu.ethereum.vm;

import org.hyperledger.besu.config.GenesisConfigOptions;
import org.hyperledger.besu.config.StubGenesisConfigOptions;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolScheduleBuilder;

import java.math.BigInteger;
import java.util.Map;
import java.util.function.Function;

import com.google.common.collect.ImmutableMap;

public class ReferenceTestProtocolSchedules {

  private static final BigInteger CHAIN_ID = BigInteger.ONE;

  public static ReferenceTestProtocolSchedules create() {
    final ImmutableMap.Builder<String, ProtocolSchedule<Void>> builder = ImmutableMap.builder();
    builder.put("Frontier", createSchedule(new StubGenesisConfigOptions()));
    builder.put(
        "FrontierToHomesteadAt5", createSchedule(new StubGenesisConfigOptions().homesteadBlock(5)));
    builder.put("Homestead", createSchedule(new StubGenesisConfigOptions().homesteadBlock(0)));
    builder.put(
        "HomesteadToEIP150At5",
        createSchedule(new StubGenesisConfigOptions().homesteadBlock(0).eip150Block(5)));
    builder.put(
        "HomesteadToDaoAt5",
        createSchedule(new StubGenesisConfigOptions().homesteadBlock(0).daoForkBlock(5)));
    builder.put("EIP150", createSchedule(new StubGenesisConfigOptions().eip150Block(0)));
    builder.put("EIP158", createSchedule(new StubGenesisConfigOptions().eip158Block(0)));
    builder.put(
        "EIP158ToByzantiumAt5",
        createSchedule(new StubGenesisConfigOptions().eip158Block(0).byzantiumBlock(5)));
    builder.put("Byzantium", createSchedule(new StubGenesisConfigOptions().byzantiumBlock(0)));
    builder.put(
        "Constantinople", createSchedule(new StubGenesisConfigOptions().constantinopleBlock(0)));
    builder.put(
        "ConstantinopleFix",
        createSchedule(new StubGenesisConfigOptions().constantinopleFixBlock(0)));
    builder.put("Istanbul", createSchedule(new StubGenesisConfigOptions().istanbulBlock(0)));
    return new ReferenceTestProtocolSchedules(builder.build());
  }

  private final Map<String, ProtocolSchedule<Void>> schedules;

  private ReferenceTestProtocolSchedules(final Map<String, ProtocolSchedule<Void>> schedules) {
    this.schedules = schedules;
  }

  public ProtocolSchedule<Void> getByName(final String name) {
    return schedules.get(name);
  }

  private static ProtocolSchedule<Void> createSchedule(final GenesisConfigOptions options) {
    return new ProtocolScheduleBuilder<>(
            options, CHAIN_ID, Function.identity(), PrivacyParameters.DEFAULT, false)
        .createProtocolSchedule();
  }
}
