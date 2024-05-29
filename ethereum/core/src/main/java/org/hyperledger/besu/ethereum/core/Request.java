/*
 * Copyright contributors to Hyperledger Besu.
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

import org.hyperledger.besu.datatypes.RequestType;
import org.hyperledger.besu.ethereum.core.encoding.RequestDecoder;
import org.hyperledger.besu.ethereum.core.encoding.RequestEncoder;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.ethereum.rlp.RLPOutput;

import org.apache.tuweni.bytes.Bytes;

public abstract class Request implements org.hyperledger.besu.plugin.data.Request {
  @Override
  public abstract RequestType getType();

  public static Request readFrom(final Bytes rlpBytes) {
    return readFrom(RLP.input(rlpBytes));
  }

  public static Request readFrom(final RLPInput rlpInput) {
    return RequestDecoder.decode(rlpInput);
  }

  public void writeTo(final RLPOutput out) {
    RequestEncoder.encode(this, out);
  }
}
