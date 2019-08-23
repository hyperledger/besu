/*
 * Copyright 2018 ConsenSys AG.
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
package tech.pegasys.pantheon.util.bytes;

import tech.pegasys.pantheon.plugin.data.BinaryData;

/** Base interface for a value whose content is stored as bytes. */
public interface BytesBacked extends BinaryData {
  /** @return The underlying backing bytes of the value. */
  BytesValue getBytes();

  @Override
  default byte[] getByteArray() {
    return getBytes().getByteArray();
  }

  @Override
  default String getHexString() {
    return getBytes().getHexString();
  }
}
