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

import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableMap;

/** The Blob Schedule config options. */
public class BlobScheduleOptions {

  private final ObjectNode blobScheduleOptionsConfigRoot;

  private static final String CANCUN_KEY = "cancun";
  private static final String PRAGUE_KEY = "prague";
  private static final String OSAKA_KEY = "osaka";
  private static final String BPO1_KEY = "bpo1";
  private static final String BPO2_KEY = "bpo2";
  private static final String BPO3_KEY = "bpo3";
  private static final String BPO4_KEY = "bpo4";
  private static final String BPO5_KEY = "bpo5";

  private static final String FUTURE_EIPS_KEY = "future_eips";

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
  public Optional<BlobSchedule> getCancun() {
    return getBlobSchedule(CANCUN_KEY);
  }

  /**
   * Gets prague blob schedule.
   *
   * @return the prague blob schedule
   */
  public Optional<BlobSchedule> getPrague() {
    return getBlobSchedule(PRAGUE_KEY);
  }

  /**
   * Gets osaka blob schedule.
   *
   * @return the osaka blob schedule
   */
  public Optional<BlobSchedule> getOsaka() {
    return getBlobSchedule(OSAKA_KEY);
  }

  /**
   * Gets bpo1 blob schedule.
   *
   * @return the bpo1 blob schedule
   */
  public Optional<BlobSchedule> getBpo1() {
    return getBlobSchedule(BPO1_KEY);
  }

  /**
   * Gets bpo2 blob schedule.
   *
   * @return the bpo2 blob schedule
   */
  public Optional<BlobSchedule> getBpo2() {
    return getBlobSchedule(BPO2_KEY);
  }

  /**
   * Gets bpo3 blob schedule.
   *
   * @return the bpo3 blob schedule
   */
  public Optional<BlobSchedule> getBpo3() {
    return getBlobSchedule(BPO3_KEY);
  }

  /**
   * Gets bpo4 blob schedule.
   *
   * @return the bpo4 blob schedule
   */
  public Optional<BlobSchedule> getBpo4() {
    return getBlobSchedule(BPO4_KEY);
  }

  /**
   * Gets bpo5 blob schedule.
   *
   * @return the bpo5 blob schedule
   */
  public Optional<BlobSchedule> getBpo5() {
    return getBlobSchedule(BPO5_KEY);
  }

  /**
   * Gets future eips blob schedule.
   *
   * @return the future eips blob schedule
   */
  public Optional<BlobSchedule> getFutureEips() {
    return getBlobSchedule(FUTURE_EIPS_KEY);
  }

  /**
   * Gets blob schedule by key.
   *
   * @param key the key for the blob schedule
   * @return the blob schedule
   */
  public Optional<BlobSchedule> getBlobSchedule(final String key) {
    return JsonUtil.getObjectNode(blobScheduleOptionsConfigRoot, key).map(BlobSchedule::new);
  }

  /**
   * As map.
   *
   * @return the map
   */
  public Map<String, Object> asMap() {
    final ImmutableMap.Builder<String, Object> builder = ImmutableMap.builder();
    getCancun().ifPresent(bs -> builder.put(CANCUN_KEY, bs.asMap()));
    getPrague().ifPresent(bs -> builder.put(PRAGUE_KEY, bs.asMap()));
    getOsaka().ifPresent(bs -> builder.put(OSAKA_KEY, bs.asMap()));
    getFutureEips().ifPresent(bs -> builder.put(FUTURE_EIPS_KEY, bs.asMap()));
    return builder.build();
  }
}
