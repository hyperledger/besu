/*
 * Copyright 2019 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.hyperledger.besu.ethereum.privacy;

import static java.nio.charset.StandardCharsets.UTF_8;

import org.hyperledger.besu.util.bytes.BytesValue;

public enum Restriction {
  RESTRICTED(BytesValue.wrap("restricted".getBytes(UTF_8))),
  UNRESTRICTED(BytesValue.wrap("unrestricted".getBytes(UTF_8))),
  UNSUPPORTED(BytesValue.EMPTY);

  private final BytesValue bytes;

  Restriction(final BytesValue bytes) {
    this.bytes = bytes;
  }

  public BytesValue getBytes() {
    return bytes;
  }
}
