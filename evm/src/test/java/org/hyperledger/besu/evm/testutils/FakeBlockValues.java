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
package org.hyperledger.besu.evm.testutils;

import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.frame.BlockValues;

import java.util.Optional;

public class FakeBlockValues implements BlockValues {
  final long number;
  final Optional<Wei> baseFee;

  public FakeBlockValues(final long number) {
    this(number, Optional.empty());
  }

  public FakeBlockValues(final Optional<Wei> baseFee) {
    this(1337, baseFee);
  }

  public FakeBlockValues(final long number, final Optional<Wei> baseFee) {
    this.number = number;
    this.baseFee = baseFee;
  }

  @Override
  public long getNumber() {
    return number;
  }

  @Override
  public Optional<Wei> getBaseFee() {
    return baseFee;
  }
}
