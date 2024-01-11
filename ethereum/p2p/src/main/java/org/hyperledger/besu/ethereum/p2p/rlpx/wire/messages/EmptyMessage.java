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
package org.hyperledger.besu.ethereum.p2p.rlpx.wire.messages;

import static org.hyperledger.besu.ethereum.rlp.RLP.EMPTY_LIST;

import org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData;

import org.apache.tuweni.bytes.Bytes;

/** A message without a body. */
abstract class EmptyMessage implements MessageData {

  @Override
  public final int getSize() {
    return EMPTY_LIST.size();
  }

  @Override
  public Bytes getData() {
    return EMPTY_LIST;
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + "{ code=" + getCode() + ", size=0}";
  }
}
