/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.evm.code;

import org.apache.tuweni.bytes.Bytes;

public class FullBytecode extends Bytecode {

  public static final FullBytecode EMPTY = new FullBytecode(Bytes.EMPTY);

  private final Bytes bytes;

  public FullBytecode(final Bytes bytes) {
    this.bytes = bytes;
  }

  public FullBytecode(final byte[] bytes) {
    this.bytes = Bytes.wrap(bytes);
  }

  @Override
  public RawByteArray getRawByteArray() {
    return new RawByteArray(bytes.toArrayUnsafe());
  }

  @Override
  public int size() {
    return bytes.size();
  }

  @Override
  public byte get(final int i) {
    return bytes.get(i);
  }

  @Override
  public Bytecode copy() {
    return new FullBytecode(bytes.copy());
  }

  @Override
  public Bytecode slice(final int i, final int length) {
    return new SlicedBytecode(this, i, length);
  }

  public static Bytecode fromHexString(final String hex) {
    return new FullBytecode(Bytes.fromHexString(hex));
  }

  public static Bytecode of(final int... bytes) {
    return new FullBytecode(Bytes.of(bytes));
  }

  public static Bytecode of(final byte... bytes) {
    return new FullBytecode(Bytes.of(bytes));
  }

  public static Bytecode wrap(final byte[] bytes) {
    return new FullBytecode(bytes);
  }

  public static Bytecode wrap(final Bytes bytes) {
    return new FullBytecode(bytes);
  }
}
