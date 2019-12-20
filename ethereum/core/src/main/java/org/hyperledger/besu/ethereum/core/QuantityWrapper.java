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
package org.hyperledger.besu.ethereum.core;

import org.hyperledger.besu.plugin.data.Quantity;

import org.apache.tuweni.units.bigints.UInt256;

public class QuantityWrapper implements Quantity {

  private final UInt256 value;

  public QuantityWrapper(final UInt256 value) {
    this.value = value;
  }

  @Override
  public Number getValue() {
    return value.toBigInteger();
  }

  @Override
  public byte[] getByteArray() {
    return value.toBytes().toArrayUnsafe();
  }

  @Override
  public String getHexString() {
    return value.toHexString();
  }

  @Override
  public int size() {
    return value.toBytes().size();
  }
}
