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
 *
 */
package org.hyperledger.besu.ethereum.bonsai.storage.flat;

import java.util.stream.Stream;

import org.apache.tuweni.bytes.Bytes;

public enum FlatDbMode {
  PARTIAL(Bytes.of(0x00)),
  FULL(Bytes.of(0x01));

  final Bytes version;

  FlatDbMode(final Bytes version) {
    this.version = version;
  }

  public Bytes getVersion() {
    return version;
  }

  public static FlatDbMode fromVersion(final Bytes version) {
    return Stream.of(FlatDbMode.values())
        .filter(mode -> mode.getVersion().equals(version))
        .findFirst()
        .orElseThrow(
            () -> new IllegalArgumentException("Unknown flat DB mode version: " + version));
  }
}
