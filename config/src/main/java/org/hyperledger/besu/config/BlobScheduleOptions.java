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
package org.hyperledger.besu.config;

import com.fasterxml.jackson.databind.node.ObjectNode;

/** The Checkpoint config options. */
public class BlobScheduleOptions {

  /** The constant DEFAULT. */
  public static final BlobScheduleOptions DEFAULT =
      new BlobScheduleOptions(JsonUtil.createEmptyObjectNode());

  private final ObjectNode blobScheduleOptionsConfigRoot;

  private static final String CANCUN_KEY = "cancun";
  private static final String PRAGUE_KEY = "prague";
  private static final String OSAKA_KEY = "osaka";

  /**
   * Instantiates a new Blob Schedule config options.
   *
   * @param blobScheduleConfigRoot the blob schedule config root
   */
  public BlobScheduleOptions(final ObjectNode blobScheduleConfigRoot) {
    this.blobScheduleOptionsConfigRoot = blobScheduleConfigRoot;
  }

  /**
   * Gets cancun blob schedule.
   *
   * @return the cancun blob schedule
   */
  public BlobSchedule getCancun() {
    return JsonUtil.getObjectNode(blobScheduleOptionsConfigRoot, CANCUN_KEY)
        .map(BlobSchedule::new)
        .orElse(BlobSchedule.DEFAULT);
  }

  /**
   * Gets prague blob schedule.
   *
   * @return the prague blob schedule
   */
  public BlobSchedule getPrague() {
    return JsonUtil.getObjectNode(blobScheduleOptionsConfigRoot, PRAGUE_KEY)
        .map(BlobSchedule::new)
        .orElse(BlobSchedule.DEFAULT);
  }

  /**
   * Gets osaka blob schedule.
   *
   * @return the osaka blob schedule
   */
  public BlobSchedule getOsaka() {
    return JsonUtil.getObjectNode(blobScheduleOptionsConfigRoot, OSAKA_KEY)
        .map(BlobSchedule::new)
        .orElse(BlobSchedule.DEFAULT);
  }

  /** The Blob schedule for a particular fork. */
  public static class BlobSchedule {
    private final ObjectNode blobScheduleConfigRoot;

    /** The constant DEFAULT. */
    public static final BlobSchedule DEFAULT = new BlobSchedule(JsonUtil.createEmptyObjectNode());

    /**
     * Instantiates a new Blob schedule.
     *
     * @param blobScheduleItemConfigRoot the blob schedule item config root
     */
    public BlobSchedule(final ObjectNode blobScheduleItemConfigRoot) {
      this.blobScheduleConfigRoot = blobScheduleItemConfigRoot;
    }

    /**
     * Gets target.
     *
     * @return the target
     */
    public int getTarget() {
      // TODO SLD EIP-7840 - reasonable default?
      return JsonUtil.getInt(blobScheduleConfigRoot, "target").orElse(3);
    }

    /**
     * Gets max.
     *
     * @return the max
     */
    public int getMax() {
      // TODO SLD EIP-7840 - reasonable default?
      return JsonUtil.getInt(blobScheduleConfigRoot, "max").orElse(6);
    }
  }
}
