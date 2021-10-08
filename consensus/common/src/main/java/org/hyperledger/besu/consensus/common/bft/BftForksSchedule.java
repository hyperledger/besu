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

import org.hyperledger.besu.config.BftConfigOptions;
import org.hyperledger.besu.config.BftFork;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.NavigableSet;
import java.util.TreeSet;
import java.util.function.BiFunction;
import java.util.function.Function;

public class BftForksSchedule<C extends BftConfigOptions> {

  private final NavigableSet<BftForkSpec<C>> forks =
      new TreeSet<>(
          Comparator.comparing((Function<BftForkSpec<C>, Long>) BftForkSpec::getBlock).reversed());

  public BftForksSchedule(final Collection<BftForkSpec<C>> forks) {
    this.forks.addAll(forks);
  }

  public static <T extends BftConfigOptions, U extends BftFork> BftForksSchedule<T> create(
      final T initial, final List<U> forks, final BiFunction<BftForkSpec<T>, U, T> specCreator) {
    final BftForkSpec<T> initialSpec = new BftForkSpec<T>(0, initial);

    final NavigableSet<BftForkSpec<T>> specs =
        new TreeSet<>(
            Comparator.comparing((Function<BftForkSpec<T>, Long>) BftForkSpec::getBlock)
                .reversed());
    specs.add(initialSpec);

    forks.stream()
        .sorted(Comparator.comparing(BftFork::getForkBlock))
        .forEachOrdered(
            f -> {
              final BftForkSpec<T> lastSpec = specs.last();
              final T apply = specCreator.apply(lastSpec, f);
              specs.add(new BftForkSpec<T>(f.getForkBlock(), apply));
            });

    return new BftForksSchedule<T>(specs);
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
