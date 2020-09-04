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
package org.hyperledger.besu.plugin.services.storage;

import org.hyperledger.besu.plugin.Unstable;

/**
 * A namespace identifier for the storage instance segment, a grouping of data that should be kept
 * isolated from the data of other segments.
 */
@Unstable
public interface SegmentIdentifier {

  /**
   * Name for the segment consistent throughout the lifetime of the segment.
   *
   * @return unique name of the segment.
   */
  String getName();

  /**
   * Identifier for the segment consistent throughout the lifetime of the segment.
   *
   * @return unique id of the segment.
   */
  byte[] getId();

  /**
   * Not all segments are in all DB versions. This queries the segment to see if it is in the DB
   * version.
   *
   * @param version Version of the DB
   * @return true if the segment is in that DB version
   */
  default boolean includeInDatabaseVersion(final int version) {
    return true;
  }
}
