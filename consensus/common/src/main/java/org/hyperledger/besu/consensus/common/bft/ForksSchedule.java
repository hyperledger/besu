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
package org.hyperledger.besu.consensus.common.bft;

import static com.google.common.base.Preconditions.checkArgument;

import org.hyperledger.besu.config.BftConfigOptions;
import org.hyperledger.besu.config.BftFork;
import org.hyperledger.besu.consensus.common.ForkSpec;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;

import com.google.common.annotations.VisibleForTesting;

public class ForksSchedule<C> {

  private final NavigableSet<ForkSpec<C>> forks =
      new TreeSet<>(
          Comparator.comparing((Function<ForkSpec<C>, Long>) ForkSpec::getBlock).reversed());

  public interface BftSpecCreator<T extends BftConfigOptions, U extends BftFork> {
    T create(ForkSpec<T> lastSpec, U fork);
  }

  @VisibleForTesting
  public ForksSchedule(final ForkSpec<C> genesisFork, final Collection<ForkSpec<C>> forks) {
    this.forks.add(genesisFork);
    this.forks.addAll(forks);
  }

  public static <T extends BftConfigOptions, U extends BftFork> ForksSchedule<T> create(
      final T initial, final List<U> forks, final BftSpecCreator<T, U> specCreator) {
    checkArgument(
        forks.stream().allMatch(f -> f.getForkBlock() > 0),
        "Transition cannot be created for genesis block");
    checkArgument(
        forks.stream().map(BftFork::getForkBlock).distinct().count() == forks.size(),
        "Duplicate transitions cannot be created for the same block");

    final NavigableSet<ForkSpec<T>> specs = new TreeSet<>(Comparator.comparing(ForkSpec::getBlock));
    final ForkSpec<T> initialForkSpec = new ForkSpec<>(0, initial);
    specs.add(initialForkSpec);

    forks.stream()
        .sorted(Comparator.comparing(BftFork::getForkBlock))
        .forEachOrdered(
            f -> {
              final T spec = specCreator.create(specs.last(), f);
              specs.add(new ForkSpec<>(f.getForkBlock(), spec));
            });

    return new ForksSchedule<>(initialForkSpec, specs.tailSet(initialForkSpec, false));
  }

  public ForkSpec<C> getFork(final long blockNumber) {
    for (final ForkSpec<C> f : forks) {
      if (blockNumber >= f.getBlock()) {
        return f;
      }
    }

    return forks.first();
  }

  public Set<ForkSpec<C>> getForks() {
    return Collections.unmodifiableSet(forks);
  }
}
