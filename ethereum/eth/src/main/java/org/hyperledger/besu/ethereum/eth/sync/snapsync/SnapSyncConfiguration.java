/*
 * Copyright contributors to Hyperledger Besu
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
package org.hyperledger.besu.ethereum.eth.sync.snapsync;

import org.immutables.value.Value;

@Value.Immutable
public class SnapSyncConfiguration {

  // we use 126 and not the max value (128) to avoid sending requests that will be refused
  public static final int DEFAULT_PIVOT_BLOCK_WINDOW_VALIDITY = 126;
  public static final int DEFAULT_PIVOT_BLOCK_DISTANCE_BEFORE_CACHING = 60;
  public static final int DEFAULT_STORAGE_COUNT_PER_REQUEST = 384;
  public static final int DEFAULT_BYTECODE_COUNT_PER_REQUEST = 84;
  public static final int DEFAULT_TRIENODE_COUNT_PER_REQUEST = 384;

  public static SnapSyncConfiguration getDefault() {
    return ImmutableSnapSyncConfiguration.builder().build();
  }

  @Value.Default
  public int getPivotBlockWindowValidity() {
    return DEFAULT_PIVOT_BLOCK_WINDOW_VALIDITY;
  }

  @Value.Default
  public int getPivotBlockDistanceBeforeCaching() {
    return DEFAULT_PIVOT_BLOCK_DISTANCE_BEFORE_CACHING;
  }

  @Value.Default
  public int getStorageCountPerRequest() {
    return DEFAULT_STORAGE_COUNT_PER_REQUEST;
  }

  @Value.Default
  public int getBytecodeCountPerRequest() {
    return DEFAULT_BYTECODE_COUNT_PER_REQUEST;
  }

  @Value.Default
  public int getTrienodeCountPerRequest() {
    return DEFAULT_TRIENODE_COUNT_PER_REQUEST;
  }
}
