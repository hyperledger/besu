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

import org.hyperledger.besu.ethereum.core.TransactionFilter;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;

import java.math.BigInteger;
import java.util.Comparator;
import java.util.NavigableSet;
import java.util.Optional;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DefaultTimestampSchedule implements TimestampSchedule {
  private final NavigableSet<TimedProtocolSpec> protocolSpecs =
      new TreeSet<>(Comparator.comparing(TimedProtocolSpec::getTimestamp).reversed());
  private final Optional<BigInteger> chainId;

  DefaultTimestampSchedule(final Optional<BigInteger> chainId) {
    this.chainId = chainId;
  }

  @Override
  public Optional<ProtocolSpec> getByTimestamp(final long timestamp) {
    for (final TimedProtocolSpec protocolSpec : protocolSpecs) {
      if (protocolSpec.getTimestamp() <= timestamp) {
        return Optional.of(protocolSpec.getSpec());
      }
    }
    return Optional.empty();
  }

  @Override
  public Stream<Long> streamMilestoneBlocks() {
    return protocolSpecs.stream().map(TimedProtocolSpec::getTimestamp);
  }

  @Override
  public Optional<BigInteger> getChainId() {
    return chainId;
  }

  @Override
  public void putMilestone(final long timestamp, final ProtocolSpec protocolSpec) {
    final TimedProtocolSpec scheduledProtocolSpec = new TimedProtocolSpec(timestamp, protocolSpec);
    // Ensure this replaces any existing spec at the same block number.
    protocolSpecs.remove(scheduledProtocolSpec);
    protocolSpecs.add(scheduledProtocolSpec);
  }

  @Override
  public String listMilestones() {
    return protocolSpecs.stream()
        .sorted(Comparator.comparing(TimedProtocolSpec::getTimestamp))
        .map(spec -> spec.getSpec().getName() + ": " + spec.getTimestamp())
        .collect(Collectors.joining(", ", "[", "]"));
  }

  @Override
  public void setTransactionFilter(final TransactionFilter transactionFilter) {
    protocolSpecs.forEach(
        spec -> spec.getSpec().getTransactionValidator().setTransactionFilter(transactionFilter));
  }

  @Override
  public void setPublicWorldStateArchiveForPrivacyBlockProcessor(
      final WorldStateArchive publicWorldStateArchive) {
    protocolSpecs.forEach(
        spec -> {
          final BlockProcessor blockProcessor = spec.getSpec().getBlockProcessor();
          if (PrivacyBlockProcessor.class.isAssignableFrom(blockProcessor.getClass()))
            ((PrivacyBlockProcessor) blockProcessor)
                .setPublicWorldStateArchive(publicWorldStateArchive);
        });
  }

  private static class TimedProtocolSpec {
    private final long timestamp;
    private final ProtocolSpec spec;

    public TimedProtocolSpec(final long timestamp, final ProtocolSpec spec) {
      this.timestamp = timestamp;
      this.spec = spec;
    }

    public long getTimestamp() {
      return timestamp;
    }

    public ProtocolSpec getSpec() {
      return spec;
    }
  }
}
