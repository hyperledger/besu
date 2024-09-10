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

import org.hyperledger.besu.plugin.services.storage.DataStorageConfiguration;
import org.hyperledger.besu.plugin.services.storage.DataStorageFormat;

import java.util.OptionalInt;

/** Privacy enabled versioned data storage format */
public enum PrivacyVersionedStorageFormat implements VersionedStorageFormat {
  /** Original Forest version, not used since replace by FOREST_WITH_VARIABLES */
  FOREST_ORIGINAL(BaseVersionedStorageFormat.FOREST_ORIGINAL, 1),
  /**
   * Current Forest version, with blockchain variables in a dedicated column family, in order to
   * make BlobDB more effective
   */
  FOREST_WITH_VARIABLES(BaseVersionedStorageFormat.FOREST_WITH_VARIABLES, 1),
  /**
   * Current Forest version, with receipts using compaction, in order to make Receipts use less disk
   * space
   */
  FOREST_WITH_RECEIPT_COMPACTION(BaseVersionedStorageFormat.FOREST_WITH_RECEIPT_COMPACTION, 2),
  /** Original Bonsai version, not used since replace by BONSAI_WITH_VARIABLES */
  BONSAI_ORIGINAL(BaseVersionedStorageFormat.BONSAI_ORIGINAL, 1),
  /**
   * Current Bonsai version, with blockchain variables in a dedicated column family, in order to
   * make BlobDB more effective
   */
  BONSAI_WITH_VARIABLES(BaseVersionedStorageFormat.BONSAI_WITH_VARIABLES, 1),
  /**
   * Current Bonsai version, with receipts using compaction, in order to make Receipts use less disk
   * space
   */
  BONSAI_WITH_RECEIPT_COMPACTION(BaseVersionedStorageFormat.BONSAI_WITH_RECEIPT_COMPACTION, 2);

  private final VersionedStorageFormat baseVersionedStorageFormat;
  private final OptionalInt privacyVersion;

  PrivacyVersionedStorageFormat(
      final VersionedStorageFormat baseVersionedStorageFormat, final int privacyVersion) {
    this.baseVersionedStorageFormat = baseVersionedStorageFormat;
    this.privacyVersion = OptionalInt.of(privacyVersion);
  }

  /**
   * Return the default version for new db for a specific format
   *
   * @param configuration data storage configuration
   * @return the version to use for new db
   */
  public static VersionedStorageFormat defaultForNewDB(
      final DataStorageConfiguration configuration) {
    return switch (configuration.getDatabaseFormat()) {
      case FOREST -> FOREST_WITH_RECEIPT_COMPACTION;
      case BONSAI -> BONSAI_WITH_RECEIPT_COMPACTION;
    };
  }

  @Override
  public DataStorageFormat getFormat() {
    return baseVersionedStorageFormat.getFormat();
  }

  @Override
  public int getVersion() {
    return baseVersionedStorageFormat.getVersion();
  }

  @Override
  public OptionalInt getPrivacyVersion() {
    return privacyVersion;
  }

  @Override
  public String toString() {
    return "PrivateVersionedStorageFormat{"
        + "versionedStorageFormat="
        + baseVersionedStorageFormat
        + ", privacyVersion="
        + privacyVersion
        + '}';
  }
}
