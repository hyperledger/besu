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
package org.hyperledger.besu.config;

import java.util.Map;

import com.fasterxml.jackson.databind.node.ObjectNode;

/** The Blob schedule for a particular fork. */
public class BlobSchedule {
  /** The constant max number of blobs per transaction defined for Osaka */
  public static final int OSAKA_MAX_BLOBS_PER_TRANSACTION = 6;

  /** The constant CANCUN_DEFAULT. */
  public static final BlobSchedule CANCUN_DEFAULT = BlobSchedule.create(3, 6, 6, 3338477);

  /** The constant PRAGUE_DEFAULT. */
  public static final BlobSchedule PRAGUE_DEFAULT = BlobSchedule.create(6, 9, 9, 5007716);

  private final int target;
  private final int max;
  private final int maxPerTransaction;
  private final int baseFeeUpdateFraction;

  private BlobSchedule(
      final int target,
      final int max,
      final int maxPerTransaction,
      final int baseFeeUpdateFraction) {
    this.target = target;
    this.max = max;
    this.maxPerTransaction = maxPerTransaction;
    this.baseFeeUpdateFraction = baseFeeUpdateFraction;
  }

  /**
   * Creates a BlobSchedule from a JSON configuration.
   *
   * @param blobScheduleConfigRoot the JSON configuration for the BlobSchedule
   * @return a BlobSchedule instance
   */
  public static BlobSchedule create(final ObjectNode blobScheduleConfigRoot) {
    int target = JsonUtil.getInt(blobScheduleConfigRoot, "target").orElseThrow();
    int max = JsonUtil.getInt(blobScheduleConfigRoot, "max").orElseThrow();
    int baseFeeUpdateFraction =
        JsonUtil.getInt(blobScheduleConfigRoot, "basefeeupdatefraction").orElseThrow();
    return create(target, max, OSAKA_MAX_BLOBS_PER_TRANSACTION, baseFeeUpdateFraction);
  }

  private static BlobSchedule create(
      final int target,
      final int max,
      final int maxPerTransaction,
      final int baseFeeUpdateFraction) {
    return new BlobSchedule(target, max, maxPerTransaction, baseFeeUpdateFraction);
  }

  /**
   * Gets target.
   *
   * @return the target
   */
  public int getTarget() {
    return target;
  }

  /**
   * Gets max.
   *
   * @return the max
   */
  public int getMax() {
    return max;
  }

  /**
   * Gets max per transaction.
   *
   * @return the max per transaction
   */
  public int getMaxPerTransaction() {
    return maxPerTransaction;
  }

  /**
   * Gets base fee update fraction.
   *
   * @return the base fee update fraction
   */
  public int getBaseFeeUpdateFraction() {
    return baseFeeUpdateFraction;
  }

  /**
   * As map.
   *
   * @return the map
   */
  Map<String, Object> asMap() {
    return Map.of("target", target, "max", max, "baseFeeUpdateFraction", baseFeeUpdateFraction);
  }

  @Override
  public String toString() {
    return "BlobSchedule{"
        + "target="
        + target
        + ", max="
        + max
        + ", maxPerTransaction="
        + maxPerTransaction
        + ", baseFeeUpdateFraction="
        + baseFeeUpdateFraction
        + '}';
  }

  /** A class that represents a BlobSchedule where all methods throw an exception. */
  public static class NoBlobSchedule extends BlobSchedule {
    /** Constructs a NoBlobSchedule */
    public NoBlobSchedule() {
      super(0, 0, 0, 0);
    }

    @Override
    public int getTarget() {
      throw new UnsupportedOperationException("NoBlobSchedule does not support this operation.");
    }

    @Override
    public int getMax() {
      throw new UnsupportedOperationException("NoBlobSchedule does not support this operation.");
    }

    @Override
    public int getBaseFeeUpdateFraction() {
      throw new UnsupportedOperationException("NoBlobSchedule does not support this operation.");
    }

    @Override
    public int getMaxPerTransaction() {
      throw new UnsupportedOperationException("NoBlobSchedule does not support this operation.");
    }

    @Override
    public Map<String, Object> asMap() {
      throw new UnsupportedOperationException("NoBlobSchedule does not support this operation.");
    }
  }
}
