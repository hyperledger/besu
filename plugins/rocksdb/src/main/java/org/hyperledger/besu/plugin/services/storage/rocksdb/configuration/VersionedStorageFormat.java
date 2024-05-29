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
package org.hyperledger.besu.plugin.services.storage.rocksdb.configuration;

import org.hyperledger.besu.plugin.services.storage.DataStorageFormat;

import java.util.OptionalInt;

/** Represent a specific version of a data storage format */
public interface VersionedStorageFormat {
  /**
   * Get the data storage format
   *
   * @return the data storage format
   */
  DataStorageFormat getFormat();

  /**
   * Get the version of the data storage format
   *
   * @return the version of the data storage format
   */
  int getVersion();

  /**
   * Get the version of the privacy db, in case the privacy feature is enabled, or empty otherwise
   *
   * @return the optional privacy version
   */
  OptionalInt getPrivacyVersion();
}
