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
package tech.pegasys.pantheon.plugin.data;

import tech.pegasys.pantheon.plugin.Unstable;

/** Super class for all types that are ultimately represented by binary data. */
@Unstable
public interface BinaryData {

  /**
   * The byte level representation of the binary data. This array should be treated as read only
   * constant data as any changes will not be reflected in the source.
   *
   * @return a read-only array of the bytes of the binary data.
   */
  byte[] getByteArray();

  /**
   * A hex string representation of the data. This hex string will represent the hex of the entire
   * binary data and will be "<code>0x</code>" prefixed. APIs that depend on shortend forms will
   * need to process the string.
   *
   * @return A string repsenting the hex encodeing of the data.
   */
  String getHexString();

  /**
   * The size, in bytes, of the contained binary data. Because {@link #getByteArray()} may cause the
   * underlying data to be copied using this size method is preferred when such a check would avoid
   * a call to {@link #getByteArray()} or {@link #getHexString()}.
   *
   * @return The length of the binary data in bytes.
   */
  int size();
}
