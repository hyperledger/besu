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
package org.hyperledger.besu.plugin.services.nodekey;

import org.apache.tuweni.bytes.Bytes;

public class PublicKey {

  private final Bytes encoded;

  public PublicKey(final Bytes encoded) {
    this.encoded = encoded;
  }

  public static PublicKey create(final Bytes encoded) {
    return new PublicKey(encoded);
  }

  public Bytes getEncoded() {
    return encoded;
  }
}
