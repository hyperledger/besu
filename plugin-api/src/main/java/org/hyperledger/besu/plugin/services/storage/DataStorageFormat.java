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
 *
 */

package org.hyperledger.besu.plugin.services.storage;

import java.util.Arrays;

public enum DataStorageFormat {
  FOREST, // Original format.  Store all tries
  BONSAI; // New format.  Store one trie, and trie logs to roll forward and backward.

//  @Deprecated private final int legacyVersion;

//  DataStorageFormat(final int legacyVersion) {
//    this.legacyVersion = legacyVersion;
//  }

//  public int getLegacyVersion() {
//    return legacyVersion;
//  }
//
//  public static DataStorageFormat fromLegacyVersion(final int i) {
//    return Arrays.stream(values()).filter(v -> v.legacyVersion == i).findFirst().orElseThrow();
//  }
}
