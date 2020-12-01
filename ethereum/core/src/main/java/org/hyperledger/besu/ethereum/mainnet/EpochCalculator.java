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
package org.hyperledger.besu.ethereum.mainnet;

public interface EpochCalculator {
  public long epochStartBlock(final long block);

  public long cacheEpoch(final long block);

  final class DefaultEpochCalculator implements EpochCalculator {

    @Override
    public long epochStartBlock(final long block) {
      return cacheEpoch(block) * EthHash.EPOCH_LENGTH + 1;
    }

    @Override
    public long cacheEpoch(final long block) {
      return Long.divideUnsigned(block, EthHash.EPOCH_LENGTH);
    }
  }

  final class Ecip1099EpochCalculator implements EpochCalculator {
    // private final long activationBlock;

    //    public Ecip1099EpochCalculator(final long activationBlock) {
    //      this.activationBlock = activationBlock;
    //    }

    /** calculate start block given epoch */
    @Override
    public long epochStartBlock(final long block) {
      long epoch = cacheEpoch(block);
      return epoch * (EthHash.EPOCH_LENGTH * 2) + 1;
    }

    @Override
    public long cacheEpoch(final long block) {
      return Long.divideUnsigned(block, EthHash.EPOCH_LENGTH * 2);
    }
  }
}
