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

public final class EtcHash {
  public static final int OLD_EPOCH_LENGTH = 30000;
  public static final int NEW_EPOCH_LENGTH = 60000;
  private final long activationBlock;

  /**
   * Implementation of ETH Hash with ETC ECIP-1099 Calibrate Epoch Duration
   *
   * @see <a
   *     href="https://ecips.ethereumclassic.org/ECIPs/ecip-1099">https://ecips.ethereumclassic.org/ECIPs/ecip-1099</a>
   * @param activationBlock Block to activate new epoch length
   */
  public EtcHash(final long activationBlock) {
    this.activationBlock = activationBlock;
  }

  /**
   * calcEpochLength returns the epoch length for a given block number
   *
   * @param block block number to use for epoch length calculation
   * @return epoch length
   */
  private int calcEpochLength(final long block) {
    if (block < activationBlock) {
      return OLD_EPOCH_LENGTH;
    }
    return NEW_EPOCH_LENGTH;
  }

  /**
   * Calculates the EtcHash Epoch for a given block number.
   *
   * @param block Block Number
   * @return EtcHash Epoch
   */
  public long epoch(final long block) {
    long epochLength = calcEpochLength(block);
    return Long.divideUnsigned(block, epochLength);
  }

  /**
   * Generates the EtcHash cache for given parameters.
   *
   * @param cacheSize Size of the cache to generate
   * @param block Block Number to generate cache for
   * @return EtcHash Cache
   */
  public static int[] mkCache(final int cacheSize, final long block) {
    return EthHash.mkCache(cacheSize, block);
  }

  /**
   * Calculates EtcHash Cache size at a given epoch.
   *
   * @param epoch EtcHash Epoch
   * @return Cache size
   */
  public static long cacheSize(final long epoch) {
    return EthHash.cacheSize(epoch);
  }

  /**
   * Calculates EtcHash DataSet size at a given epoch.
   *
   * @param epoch EtcHash Epoch
   * @return DataSet size
   */
  public static long datasetSize(final long epoch) {
    return EthHash.datasetSize(epoch);
  }
}
