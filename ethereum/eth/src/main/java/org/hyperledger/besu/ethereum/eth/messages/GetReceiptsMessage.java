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
package org.hyperledger.besu.ethereum.eth.messages;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.AbstractMessageData;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPInput;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.ethereum.rlp.RLPInput;

import java.util.ArrayList;
import java.util.List;

import org.apache.tuweni.bytes.Bytes;

public final class GetReceiptsMessage extends AbstractMessageData {
  private List<Hash> blockHashes;
  private int firstBlockReceiptIndex;

  public static GetReceiptsMessage readFrom(final MessageData message) {
    if (message instanceof GetReceiptsMessage) {
      return (GetReceiptsMessage) message;
    }
    final int code = message.getCode();
    if (code != EthProtocolMessages.GET_RECEIPTS) {
      throw new IllegalArgumentException(
          String.format("Message has code %d and thus is not a GetReceipts.", code));
    }
    return new GetReceiptsMessage(message.getData());
  }

  public static GetReceiptsMessage create(final List<Hash> blockHashes) {
    return create(blockHashes, -1);
  }

  public static GetReceiptsMessage create(
      final List<Hash> blockHashes, final int firstBlockReceiptIndex) {
    final BytesValueRLPOutput tmp = new BytesValueRLPOutput();
    if (firstBlockReceiptIndex >= 0) {
      tmp.writeIntScalar(firstBlockReceiptIndex);
    }
    tmp.startList();
    blockHashes.forEach(hash -> tmp.writeBytes(hash.getBytes()));
    tmp.endList();
    return new GetReceiptsMessage(tmp.encoded(), blockHashes, firstBlockReceiptIndex);
  }

  private GetReceiptsMessage(final Bytes data) {
    super(data);
    deserialize(data);
  }

  private GetReceiptsMessage(
      final Bytes data, final List<Hash> blockHashes, final int firstBlockReceiptIndex) {
    super(data);
    this.blockHashes = blockHashes;
    this.firstBlockReceiptIndex = firstBlockReceiptIndex;
  }

  @Override
  public int getCode() {
    return EthProtocolMessages.GET_RECEIPTS;
  }

  public List<Hash> blockHashes() {
    return blockHashes;
  }

  public int firstBlockReceiptIndex() {
    return firstBlockReceiptIndex;
  }

  private void deserialize(final Bytes data) {
    final RLPInput input = new BytesValueRLPInput(data, false);

    this.firstBlockReceiptIndex = input.nextIsList() ? -1 : input.readIntScalar();

    this.blockHashes = new ArrayList<>(input.enterList());
    while (!input.isEndOfCurrentList()) {
      blockHashes.add(Hash.wrap(input.readBytes32()));
    }
    input.leaveList();
  }
}
