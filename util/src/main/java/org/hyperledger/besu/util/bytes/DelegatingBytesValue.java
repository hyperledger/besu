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
package org.hyperledger.besu.util.bytes;

public class DelegatingBytesValue extends BaseDelegatingBytesValue<BytesValue>
    implements BytesValue {

  protected DelegatingBytesValue(final BytesValue wrapped) {
    super(unwrap(wrapped));
  }

  // Make sure we don't end-up with giant chains of delegating through wrapping.
  private static BytesValue unwrap(final BytesValue v) {
    // Using a loop, because we could have DelegatingBytesValue intertwined with
    // DelegatingMutableBytesValue in theory.
    BytesValue wrapped = v;

    while (wrapped instanceof BaseDelegatingBytesValue) {
      wrapped = ((BaseDelegatingBytesValue) wrapped).wrapped;
    }
    return wrapped;
  }
}
