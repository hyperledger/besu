/*
 *
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

package org.hyperledger.besu.ethereum.eth.sync.backwardsync;

import org.hyperledger.besu.datatypes.Hash;

import org.apache.tuweni.bytes.Bytes32;

public class HashConvertor implements ValueConvertor<Hash> {
  @Override
  public Hash fromBytes(final byte[] bytes) {
    return Hash.wrap(Bytes32.wrap(bytes));
  }

  @Override
  public byte[] toBytes(final Hash value) {
    return value.toArrayUnsafe();
  }
}
