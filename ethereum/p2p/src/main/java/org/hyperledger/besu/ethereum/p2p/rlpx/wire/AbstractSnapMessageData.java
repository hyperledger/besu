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
package org.hyperledger.besu.ethereum.p2p.rlpx.wire;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.rlp.RLPInput;

import java.math.BigInteger;
import java.util.AbstractMap;
import java.util.Map;
import java.util.Optional;

import com.google.common.annotations.VisibleForTesting;
import org.apache.tuweni.bytes.Bytes;

public abstract class AbstractSnapMessageData extends AbstractMessageData {

  @VisibleForTesting
  public static final BigInteger SIZE_REQUEST = BigInteger.valueOf(524288); // 512 * 1024

  private Optional<Hash> rootHash;

  public AbstractSnapMessageData(final Bytes data) {
    super(data);
    rootHash = Optional.empty();
  }

  public Optional<Hash> getRootHash() {
    return rootHash;
  }

  public void setRootHash(final Optional<Hash> rootHash) {
    this.rootHash = rootHash;
  }

  @Override
  public MessageData wrapMessageData(final BigInteger requestId) {
    return new RawMessage(getCode(), wrap(requestId));
  }

  @Override
  public Map.Entry<BigInteger, MessageData> unwrapMessageData() {
    final RLPInput messageDataRLP = RLP.input(getData());
    messageDataRLP.enterList();
    final BigInteger requestId = messageDataRLP.readBigIntegerScalar();
    messageDataRLP.leaveListLenient();
    return new AbstractMap.SimpleImmutableEntry<>(requestId, new RawMessage(getCode(), getData()));
  }

  protected Bytes wrap(final BigInteger requestId) {
    throw new UnsupportedOperationException("cannot wrap this message");
  }

  public static MessageData create(final Message message) {
    return new AbstractSnapMessageData(message.getData().getData()) {
      @Override
      public int getCode() {
        return message.getData().getCode();
      }
    };
  }
}
