/*
 * Copyright contributors to Hyperledger Besu.
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

import static com.google.common.base.Preconditions.checkArgument;

import org.hyperledger.besu.config.Fork;

import java.util.Comparator;
import java.util.List;
import java.util.NavigableSet;
import java.util.TreeSet;

/** The forks schedule factory. */
public class ForksScheduleFactory {

  /**
   * The interface spec creator.
   *
   * @param <T> the type parameter
   * @param <U> the type parameter
   */
  public interface SpecCreator<T, U> {
    /**
     * Create type of ConfigOptions.
     *
     * @param lastSpec the last spec
     * @param fork the fork
     * @return the type of ConfigOptions
     */
    T create(ForkSpec<T> lastSpec, U fork);
  }

  /** Default constructor. */
  private ForksScheduleFactory() {}

  /**
   * Create forks schedule.
   *
   * @param <T> the type parameter ConfigOptions
   * @param <U> the type parameter Fork
   * @param initial the initial
   * @param forks the forks
   * @param specCreator the spec creator
   * @return the forks schedule
   */
  public static <T, U extends Fork> ForksSchedule<T> create(
      final T initial, final List<U> forks, final SpecCreator<T, U> specCreator) {
    checkArgument(
        forks.stream().allMatch(f -> f.getForkBlock() > 0),
        "Transition cannot be created for genesis block");
    checkArgument(
        forks.stream().map(Fork::getForkBlock).distinct().count() == forks.size(),
        "Duplicate transitions cannot be created for the same block");

    final NavigableSet<ForkSpec<T>> specs = new TreeSet<>(Comparator.comparing(ForkSpec::getBlock));
    final ForkSpec<T> initialForkSpec = new ForkSpec<>(0, initial);
    specs.add(initialForkSpec);

    forks.stream()
        .sorted(Comparator.comparing(Fork::getForkBlock))
        .forEachOrdered(
            f -> {
              final T spec = specCreator.create(specs.last(), f);
              specs.add(new ForkSpec<>(f.getForkBlock(), spec));
            });

    return new ForksSchedule<>(specs);
  }
}
