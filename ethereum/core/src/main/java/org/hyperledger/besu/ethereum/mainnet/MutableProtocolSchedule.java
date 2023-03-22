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
package org.hyperledger.besu.ethereum.mainnet;

import static com.google.common.base.Preconditions.checkArgument;

import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.TransactionFilter;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;

import java.math.BigInteger;
import java.util.Comparator;
import java.util.NavigableSet;
import java.util.Optional;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class MutableProtocolSchedule implements ProtocolSchedule {

  protected NavigableSet<ScheduledProtocolSpec> protocolSpecs =
      new TreeSet<>(Comparator.comparing(ScheduledProtocolSpec::milestone).reversed());

  private final Optional<BigInteger> chainId;

  public MutableProtocolSchedule(final Optional<BigInteger> chainId) {
    this.chainId = chainId;
  }

  protected MutableProtocolSchedule(final MutableProtocolSchedule protocolSchedule) {
    this.chainId = protocolSchedule.chainId;
    this.protocolSpecs = protocolSchedule.protocolSpecs;
  }

  @Override
  public Optional<BigInteger> getChainId() {
    return chainId;
  }

  @Override
  public void putMilestone(final long blockNumber, final ProtocolSpec protocolSpec) {
    final ScheduledProtocolSpec scheduledProtocolSpec =
        new ScheduledProtocolSpec(blockNumber, protocolSpec);
    // Ensure this replaces any existing spec at the same block number.
    protocolSpecs.remove(scheduledProtocolSpec);
    protocolSpecs.add(scheduledProtocolSpec);
  }

  @Override
  public ProtocolSpec getByBlockNumber(final long number) {
    checkArgument(number >= 0, "number must be non-negative");
    checkArgument(
        !protocolSpecs.isEmpty(), "At least 1 milestone must be provided to the protocol schedule");
    checkArgument(
        protocolSpecs.last().milestone() == 0, "There must be a milestone starting from block 0");
    // protocolSpecs is sorted in descending block order, so the first one we find that's lower than
    // the requested level will be the most appropriate spec
    for (final ScheduledProtocolSpec s : protocolSpecs) {
      if (number >= s.milestone()) {
        return s.spec();
      }
    }
    return null;
  }

  @Override
  public String listMilestones() {
    return protocolSpecs.stream()
        .sorted(Comparator.comparing(ScheduledProtocolSpec::milestone))
        .map(scheduledSpec -> scheduledSpec.spec().getName() + ": " + scheduledSpec.milestone())
        .collect(Collectors.joining(", ", "[", "]"));
  }

  @Override
  public boolean anyMatch(final Predicate<ScheduledProtocolSpec> predicate) {
    return this.protocolSpecs.stream().anyMatch(predicate);
  }

  @Override
  public boolean isOnMilestoneBoundary(final BlockHeader blockHeader) {
    return this.protocolSpecs.stream().anyMatch(s -> blockHeader.getNumber() == s.milestone());
  }

  @Override
  public void setTransactionFilter(final TransactionFilter transactionFilter) {
    protocolSpecs.forEach(
        spec -> spec.spec().getTransactionValidator().setTransactionFilter(transactionFilter));
  }

  @Override
  public void setPublicWorldStateArchiveForPrivacyBlockProcessor(
      final WorldStateArchive publicWorldStateArchive) {
    protocolSpecs.forEach(
        spec -> {
          final BlockProcessor blockProcessor = spec.spec().getBlockProcessor();
          if (PrivacyBlockProcessor.class.isAssignableFrom(blockProcessor.getClass()))
            ((PrivacyBlockProcessor) blockProcessor)
                .setPublicWorldStateArchive(publicWorldStateArchive);
        });
  }
}
