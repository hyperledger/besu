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

package org.hyperledger.besu.consensus.qbft.validator;

import org.hyperledger.besu.config.QbftFork;

import java.util.Comparator;
import java.util.List;
import java.util.NavigableSet;
import java.util.Optional;
import java.util.TreeSet;

public class QbftForksSchedule {

  private final NavigableSet<QbftFork> qbftForks =
      new TreeSet<>(Comparator.comparing(QbftFork::getForkBlock).reversed());

  public QbftForksSchedule(final List<QbftFork> forks) {
    qbftForks.addAll(forks);
  }

  public Optional<QbftFork> getByBlockNumber(final long blockNumber) {
    for (final QbftFork f : qbftForks) {
      if (blockNumber >= f.getForkBlock()) {
        return Optional.of(f);
      }
    }

    return Optional.empty();
  }
}
