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

public enum BaseVersionedStorageFormat implements VersionedStorageFormat {
  FOREST_ORIGINAL(DataStorageFormat.FOREST, 1),
  FOREST_WITH_VARIABLES(DataStorageFormat.FOREST, 2),
  BONSAI_ORIGINAL(DataStorageFormat.BONSAI, 1),
  BONSAI_WITH_VARIABLES(DataStorageFormat.BONSAI, 2);

  private final DataStorageFormat format;
  private final int version;

  BaseVersionedStorageFormat(final DataStorageFormat format, final int version) {
    this.format = format;
    this.version = version;
  }

  public static BaseVersionedStorageFormat defaultForNewDB(final DataStorageFormat format) {
    return switch (format) {
      case FOREST -> FOREST_WITH_VARIABLES;
      case BONSAI -> BONSAI_WITH_VARIABLES;
    };
  }

  @Override
  public DataStorageFormat getFormat() {
    return format;
  }

  @Override
  public int getVersion() {
    return version;
  }

  @Override
  public OptionalInt getPrivacyVersion() {
    return OptionalInt.empty();
  }

  @Override
  public String toString() {
    return "BaseVersionedStorageFormat{" + "format=" + format + ", version=" + version + '}';
  }
}
