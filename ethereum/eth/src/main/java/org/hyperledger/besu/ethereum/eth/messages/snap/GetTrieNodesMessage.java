/*
 * Copyright contributors to Hyperledger Besu
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
package org.hyperledger.besu.ethereum.eth.messages.snap;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.AbstractSnapMessageData;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPInput;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.ethereum.rlp.RLPInput;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.immutables.value.Value;

public final class GetTrieNodesMessage extends AbstractSnapMessageData {

  public GetTrieNodesMessage(final Bytes data) {
    super(data);
  }

  public static GetTrieNodesMessage readFrom(final MessageData message) {
    if (message instanceof GetTrieNodesMessage) {
      return (GetTrieNodesMessage) message;
    }
    final int code = message.getCode();
    if (code != SnapV1.GET_TRIE_NODES) {
      throw new IllegalArgumentException(
          String.format("Message has code %d and thus is not a GetTrieNodes.", code));
    }
    return new GetTrieNodesMessage(message.getData());
  }

  public static GetTrieNodesMessage create(
      final Hash worldStateRootHash, final List<List<Bytes>> requests) {
    return create(Optional.empty(), worldStateRootHash, requests);
  }

  public static GetTrieNodesMessage create(
      final Optional<BigInteger> requestId,
      final Hash worldStateRootHash,
      final List<List<Bytes>> paths) {
    final BytesValueRLPOutput tmp = new BytesValueRLPOutput();
    tmp.startList();
    requestId.ifPresent(tmp::writeBigIntegerScalar);
    tmp.writeBytes(worldStateRootHash);
    tmp.writeList(
        paths,
        (path, rlpOutput) ->
            rlpOutput.writeList(path, (b, subRlpOutput) -> subRlpOutput.writeBytes(b)));
    tmp.writeBigIntegerScalar(SIZE_REQUEST);
    tmp.endList();
    return new GetTrieNodesMessage(tmp.encoded());
  }

  @Override
  protected Bytes wrap(final BigInteger requestId) {
    final TrieNodesPaths paths = paths(false);
    return create(Optional.of(requestId), paths.worldStateRootHash(), paths.paths()).getData();
  }

  @Override
  public int getCode() {
    return SnapV1.GET_TRIE_NODES;
  }

  public TrieNodesPaths paths(final boolean withRequestId) {
    final RLPInput input = new BytesValueRLPInput(data, false);
    input.enterList();
    if (withRequestId) input.skipNext();
    final ImmutableTrieNodesPaths.Builder paths =
        ImmutableTrieNodesPaths.builder()
            .worldStateRootHash(Hash.wrap(Bytes32.wrap(input.readBytes32())))
            .paths(input.readList(rlpInput -> rlpInput.readList(RLPInput::readBytes)))
            .responseBytes(input.readBigIntegerScalar());
    input.leaveList();
    return paths.build();
  }

  @Value.Immutable
  public interface TrieNodesPaths {

    Hash worldStateRootHash();

    List<List<Bytes>> paths();

    BigInteger responseBytes();
  }
}
