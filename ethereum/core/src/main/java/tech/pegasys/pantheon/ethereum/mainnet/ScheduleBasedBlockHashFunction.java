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
package tech.pegasys.pantheon.ethereum.mainnet;

import tech.pegasys.pantheon.ethereum.core.BlockHashFunction;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.core.Hash;

/**
 * Looks up the correct {@link BlockHashFunction} to use based on a {@link ProtocolSchedule} to
 * ensure that the correct hash is created given the block number.
 */
public class ScheduleBasedBlockHashFunction<C> implements BlockHashFunction {

  private final ProtocolSchedule<C> protocolSchedule;

  private ScheduleBasedBlockHashFunction(final ProtocolSchedule<C> protocolSchedule) {
    this.protocolSchedule = protocolSchedule;
  }

  public static <C> BlockHashFunction create(final ProtocolSchedule<C> protocolSchedule) {
    return new ScheduleBasedBlockHashFunction<>(protocolSchedule);
  }

  @Override
  public Hash apply(final BlockHeader header) {
    return protocolSchedule
        .getByBlockNumber(header.getNumber())
        .getBlockHashFunction()
        .apply(header);
  }
}
