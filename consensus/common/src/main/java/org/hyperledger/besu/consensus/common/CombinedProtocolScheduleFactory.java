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
package org.hyperledger.besu.consensus.common;

import static com.google.common.base.Preconditions.checkState;

import org.hyperledger.besu.ethereum.mainnet.MutableProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ScheduledProtocolSpec;

import java.math.BigInteger;
import java.util.NavigableSet;
import java.util.Optional;
import java.util.function.Predicate;

public class CombinedProtocolScheduleFactory {

  public ProtocolSchedule create(
      final NavigableSet<ForkSpec<ProtocolSchedule>> forkSpecs,
      final Optional<BigInteger> chainId) {
    final MutableProtocolSchedule combinedProtocolSchedule = new MutableProtocolSchedule(chainId);
    for (ForkSpec<ProtocolSchedule> spec : forkSpecs) {
      checkState(
          spec.getValue() instanceof MutableProtocolSchedule,
          "Consensus migration requires a MutableProtocolSchedule");
      final MutableProtocolSchedule protocolSchedule = (MutableProtocolSchedule) spec.getValue();

      final Optional<Long> endBlock =
          Optional.ofNullable(forkSpecs.higher(spec)).map(ForkSpec::getBlock);
      protocolSchedule.getScheduledProtocolSpecs().stream()
          .filter(protocolSpecMatchesConsensusBlockRange(spec.getBlock(), endBlock))
          .forEach(s -> combinedProtocolSchedule.putMilestone(s.getBlock(), s.getSpec()));

      // When moving to a new consensus mechanism we want to use the last milestone but created by
      // our consensus mechanism's BesuControllerBuilder so any additional rules are applied
      if (spec.getBlock() > 0) {
        combinedProtocolSchedule.putMilestone(
            spec.getBlock(), protocolSchedule.getByBlockNumber(spec.getBlock()));
      }
    }
    return combinedProtocolSchedule;
  }

  private Predicate<ScheduledProtocolSpec> protocolSpecMatchesConsensusBlockRange(
      final long startBlock, final Optional<Long> endBlock) {
    return scheduledProtocolSpec ->
        scheduledProtocolSpec.getBlock() >= startBlock
            && endBlock.map(b -> scheduledProtocolSpec.getBlock() < b).orElse(true);
  }
}
