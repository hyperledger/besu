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
package org.hyperledger.besu.ethereum.mainnet;

import static com.google.common.base.Preconditions.checkArgument;

import org.hyperledger.besu.ethereum.core.TransactionFilter;

import java.math.BigInteger;
import java.util.Comparator;
import java.util.NavigableSet;
import java.util.Optional;
import java.util.TreeSet;
import java.util.stream.Collectors;

public class MutableProtocolSchedule<C> implements ProtocolSchedule<C> {

  private final NavigableSet<ScheduledProtocolSpec<C>> protocolSpecs =
      new TreeSet<>(
          Comparator.<ScheduledProtocolSpec<C>, Long>comparing(ScheduledProtocolSpec::getBlock)
              .reversed());
  private final Optional<BigInteger> chainId;

  public MutableProtocolSchedule(final Optional<BigInteger> chainId) {
    this.chainId = chainId;
  }

  @Override
  public Optional<BigInteger> getChainId() {
    return chainId;
  }

  public void putMilestone(final long blockNumber, final ProtocolSpec<C> protocolSpec) {
    final ScheduledProtocolSpec<C> scheduledProtocolSpec =
        new ScheduledProtocolSpec<>(blockNumber, protocolSpec);
    // Ensure this replaces any existing spec at the same block number.
    protocolSpecs.remove(scheduledProtocolSpec);
    protocolSpecs.add(scheduledProtocolSpec);
  }

  @Override
  public ProtocolSpec<C> getByBlockNumber(final long number) {
    checkArgument(number >= 0, "number must be non-negative");
    checkArgument(
        !protocolSpecs.isEmpty(), "At least 1 milestone must be provided to the protocol schedule");
    checkArgument(
        protocolSpecs.last().getBlock() == 0, "There must be a milestone starting from block 0");
    // protocolSpecs is sorted in descending block order, so the first one we find that's lower than
    // the requested level will be the most appropriate spec
    for (final ScheduledProtocolSpec<C> s : protocolSpecs) {
      if (number >= s.getBlock()) {
        return s.getSpec();
      }
    }
    return null;
  }

  public String listMilestones() {
    return protocolSpecs.stream()
        .sorted(Comparator.comparing(ScheduledProtocolSpec::getBlock))
        .map(spec -> spec.getSpec().getName() + ": " + spec.getBlock())
        .collect(Collectors.joining(", ", "[", "]"));
  }

  @Override
  public void setTransactionFilter(final TransactionFilter transactionFilter) {
    protocolSpecs.forEach(spec -> spec.getSpec().setTransactionFilter(transactionFilter));
  }
}
