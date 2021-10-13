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

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.NavigableSet;
import java.util.TreeSet;
import java.util.function.Function;

import com.google.common.annotations.VisibleForTesting;

public class BftForksSchedule<C extends BftConfigOptions> {

  private final NavigableSet<BftForkSpec<C>> forks =
      new TreeSet<>(
          Comparator.comparing((Function<BftForkSpec<C>, Long>) BftForkSpec::getBlock).reversed());

  public interface BftSpecCreator<T extends BftConfigOptions, U extends BftFork> {
    T create(BftForkSpec<T> lastSpec, U fork);
  }

  @VisibleForTesting
  public BftForksSchedule(
      final BftForkSpec<C> genesisFork, final Collection<BftForkSpec<C>> forks) {
    this.forks.add(genesisFork);
    this.forks.addAll(forks);
  }

  public static <T extends BftConfigOptions, U extends BftFork> BftForksSchedule<T> create(
      final T initial, final List<U> forks, final BftSpecCreator<T, U> specCreator) {
    checkArgument(
        forks.stream().allMatch(f -> f.getForkBlock() > 0),
        "Transition cannot be created for genesis block");
    checkArgument(
        forks.stream().map(BftFork::getForkBlock).distinct().count() == forks.size(),
        "Duplicate transitions cannot be created for the same block");

    final NavigableSet<BftForkSpec<T>> specs =
        new TreeSet<>(Comparator.comparing(BftForkSpec::getBlock));
    final BftForkSpec<T> initialForkSpec = new BftForkSpec<>(0, initial);
    specs.add(initialForkSpec);

    forks.stream()
        .sorted(Comparator.comparing(BftFork::getForkBlock))
        .forEachOrdered(
            f -> {
              final T spec = specCreator.create(specs.last(), f);
              specs.add(new BftForkSpec<>(f.getForkBlock(), spec));
            });

    return new BftForksSchedule<>(initialForkSpec, specs.tailSet(initialForkSpec, false));
  }

  public BftForkSpec<C> getFork(final long blockNumber) {
    for (final BftForkSpec<C> f : forks) {
      if (blockNumber >= f.getBlock()) {
        return f;
      }
    }

    return forks.first();
  }
}
