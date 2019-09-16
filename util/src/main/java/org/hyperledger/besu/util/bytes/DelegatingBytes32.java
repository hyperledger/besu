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
package org.hyperledger.besu.util.bytes;

public class DelegatingBytes32 extends BaseDelegatingBytesValue<Bytes32> implements Bytes32 {
  protected DelegatingBytes32(final Bytes32 wrapped) {
    super(unwrap(wrapped));
  }

  // Make sure we don't end-up with giant chains of delegating through wrapping.
  private static Bytes32 unwrap(final Bytes32 value) {
    if (value instanceof DelegatingBytes32) {
      return ((DelegatingBytes32) value).wrapped;
    }
    return value;
  }

  @Override
  public Bytes32 copy() {
    return wrapped.copy();
  }

  @Override
  public MutableBytes32 mutableCopy() {
    return wrapped.mutableCopy();
  }
}
