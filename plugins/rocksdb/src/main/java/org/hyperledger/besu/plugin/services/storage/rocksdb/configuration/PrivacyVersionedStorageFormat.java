/*
 * Copyright Hyperledger Besu Contributors.
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

public enum PrivacyVersionedStorageFormat implements VersionedStorageFormat {
  FOREST_ORIGINAL(BaseVersionedStorageFormat.FOREST_ORIGINAL, 1),
  FOREST_WITH_VARIABLES(BaseVersionedStorageFormat.FOREST_WITH_VARIABLES, 1),
  BONSAI_ORIGINAL(BaseVersionedStorageFormat.BONSAI_ORIGINAL, 1),
  BONSAI_WITH_VARIABLES(BaseVersionedStorageFormat.BONSAI_WITH_VARIABLES, 1);

  private final VersionedStorageFormat baseVersionedStorageFormat;
  private final OptionalInt privacyVersion;

  PrivacyVersionedStorageFormat(
      final VersionedStorageFormat baseVersionedStorageFormat, final int privacyVersion) {
    this.baseVersionedStorageFormat = baseVersionedStorageFormat;
    this.privacyVersion = OptionalInt.of(privacyVersion);
  }

  public static VersionedStorageFormat defaultForNewDB(final DataStorageFormat format) {
    return switch (format) {
      case FOREST -> FOREST_WITH_VARIABLES;
      case BONSAI -> BONSAI_WITH_VARIABLES;
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
