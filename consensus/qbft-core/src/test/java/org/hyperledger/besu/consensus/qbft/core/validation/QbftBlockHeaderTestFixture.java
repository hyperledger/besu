/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.consensus.qbft.core.validation;

import org.hyperledger.besu.consensus.qbft.core.types.QbftBlockHeader;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;

public class QbftBlockHeaderTestFixture {

  private long number = 0;
  private long timestamp = 0;
  private Hash hash = Hash.EMPTY;
  private Address coinbase = Address.ZERO;

  public QbftBlockHeaderTestFixture number(final long number) {
    this.number = number;
    return this;
  }

  public QbftBlockHeaderTestFixture timestamp(final long timestamp) {
    this.timestamp = timestamp;
    return this;
  }

  public QbftBlockHeaderTestFixture hash(final Hash hash) {
    this.hash = hash;
    return this;
  }

  public QbftBlockHeaderTestFixture coinbase(final Address coinbase) {
    this.coinbase = coinbase;
    return this;
  }

  public QbftBlockHeader buildHeader() {
    return new QbftBlockHeader() {
      @Override
      public long getNumber() {
        return number;
      }

      @Override
      public long getTimestamp() {
        return timestamp;
      }

      @Override
      public Hash getHash() {
        return hash;
      }

      @Override
      public Address getCoinbase() {
        return coinbase;
      }
    };
  }
}
