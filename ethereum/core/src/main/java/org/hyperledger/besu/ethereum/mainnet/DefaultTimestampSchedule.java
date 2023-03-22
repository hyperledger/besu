/*
 *
 *  * Copyright Hyperledger Besu Contributors.
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 *  * the License. You may obtain a copy of the License at
 *  *
 *  * http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 *  * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 *  * specific language governing permissions and limitations under the License.
 *  *
 *  * SPDX-License-Identifier: Apache-2.0
 *
 */

package org.hyperledger.besu.ethereum.mainnet;

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

import com.google.common.annotations.VisibleForTesting;

public class DefaultTimestampSchedule implements TimestampSchedule {

  @VisibleForTesting
  protected NavigableSet<ScheduledProtocolSpec> protocolSpecs =
      new TreeSet<>(Comparator.comparing(ScheduledProtocolSpec::milestone).reversed());

  private final Optional<BigInteger> chainId;

  DefaultTimestampSchedule(final Optional<BigInteger> chainId) {
    this.chainId = chainId;
  }

  @VisibleForTesting
  protected DefaultTimestampSchedule(final DefaultTimestampSchedule timestampSchedule) {
    this.chainId = timestampSchedule.chainId;
    this.protocolSpecs = timestampSchedule.protocolSpecs;
  }

  @Override
  public Optional<ProtocolSpec> getByTimestamp(final long timestamp) {
    for (final ScheduledProtocolSpec protocolSpec : protocolSpecs) {
      if (protocolSpec.milestone() <= timestamp) {
        return Optional.of(protocolSpec.spec());
      }
    }
    return Optional.empty();
  }

  @Override
  public boolean anyMatch(final Predicate<ScheduledProtocolSpec> predicate) {
    return this.protocolSpecs.stream().anyMatch(predicate);
  }

  @Override
  public boolean isOnMilestoneBoundary(final BlockHeader blockHeader) {
    return this.protocolSpecs.stream().anyMatch(s -> blockHeader.getTimestamp() == s.milestone());
  }

  @Override
  public Optional<BigInteger> getChainId() {
    return chainId;
  }

  @Override
  public void putMilestone(final long timestamp, final ProtocolSpec protocolSpec) {
    final ScheduledProtocolSpec scheduledProtocolSpec =
        new ScheduledProtocolSpec(timestamp, protocolSpec);
    // Ensure this replaces any existing spec at the same block number.
    protocolSpecs.remove(scheduledProtocolSpec);
    protocolSpecs.add(scheduledProtocolSpec);
  }

  @Override
  public String listMilestones() {
    return protocolSpecs.stream()
        .sorted(Comparator.comparing(ScheduledProtocolSpec::milestone))
        .map(scheduledSpec -> scheduledSpec.spec().getName() + ": " + scheduledSpec.milestone())
        .collect(Collectors.joining(", ", "[", "]"));
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
