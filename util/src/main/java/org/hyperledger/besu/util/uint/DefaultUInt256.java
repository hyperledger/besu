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
package org.hyperledger.besu.util.uint;

import org.hyperledger.besu.util.bytes.Bytes32;

/**
 * Default implementation of a {@link UInt256}.
 *
 * <p>Note that this class is not meant to be exposed outside of this package. Use {@link UInt256}
 * static methods to build {@link UInt256} values instead.
 */
class DefaultUInt256 extends AbstractUInt256Value<UInt256> implements UInt256 {

  DefaultUInt256(final Bytes32 bytes) {
    super(bytes, UInt256Counter::new);
  }

  static Counter<UInt256> newVar() {
    return new UInt256Counter();
  }

  @Override
  public UInt256 asUInt256() {
    return this;
  }

  private static class UInt256Counter extends Counter<UInt256> {
    private UInt256Counter() {
      super(DefaultUInt256::new);
    }
  }
}
