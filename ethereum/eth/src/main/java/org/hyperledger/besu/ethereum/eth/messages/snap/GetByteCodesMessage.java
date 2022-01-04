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

import org.hyperledger.besu.ethereum.p2p.rlpx.wire.AbstractSnapMessageData;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPInput;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.ethereum.rlp.RLPInput;

import java.math.BigInteger;
import java.util.Optional;

import kotlin.collections.ArrayDeque;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.immutables.value.Value;

public final class GetByteCodesMessage extends AbstractSnapMessageData {

  final Optional<ArrayDeque<Bytes32>> accountHashes;

  public static GetByteCodesMessage readFrom(final MessageData message) {
    if (message instanceof GetByteCodesMessage) {
      return (GetByteCodesMessage) message;
    }
    final int code = message.getCode();
    if (code != SnapV1.GET_BYTECODES) {
      throw new IllegalArgumentException(
          String.format("Message has code %d and thus is not a GetByteCodesMessage.", code));
    }
    return new GetByteCodesMessage(Optional.empty(), message.getData());
  }

  public static GetByteCodesMessage create(
      final Optional<ArrayDeque<Bytes32>> accountHashes,
      final ArrayDeque<Bytes32> codeHashes,
      final BigInteger responseBytes) {
    return create(Optional.empty(), accountHashes, codeHashes, responseBytes);
  }

  public static GetByteCodesMessage create(
      final Optional<BigInteger> requestId,
      final Optional<ArrayDeque<Bytes32>> accountHashes,
      final ArrayDeque<Bytes32> codeHashes,
      final BigInteger responseBytes) {
    final BytesValueRLPOutput tmp = new BytesValueRLPOutput();
    tmp.startList();
    requestId.ifPresent(tmp::writeBigIntegerScalar);
    tmp.writeList(codeHashes, (hash, rlpOutput) -> rlpOutput.writeBytes(hash));
    tmp.writeBigIntegerScalar(responseBytes);
    tmp.endList();
    return new GetByteCodesMessage(accountHashes, tmp.encoded());
  }

  public GetByteCodesMessage(final Optional<ArrayDeque<Bytes32>> accountHashes, final Bytes data) {
    super(data);
    this.accountHashes = accountHashes;
  }

  @Override
  protected Bytes wrap(final BigInteger requestId) {
    final CodeHashes request = codeHashes(false);
    return create(Optional.of(requestId), accountHashes, request.hashes(), request.responseBytes())
        .getData();
  }

  @Override
  public int getCode() {
    return SnapV1.GET_BYTECODES;
  }

  public CodeHashes codeHashes(final boolean withRequestId) {
    final ArrayDeque<Bytes32> hashes = new ArrayDeque<>();
    final BigInteger responseBytes;
    final RLPInput input = new BytesValueRLPInput(data, false);
    input.enterList();
    if (withRequestId) input.skipNext();
    input.enterList();
    while (!input.isEndOfCurrentList()) {
      hashes.add(input.readBytes32());
    }
    input.leaveList();
    responseBytes = input.readBigIntegerScalar();
    input.leaveList();
    return ImmutableCodeHashes.builder().hashes(hashes).responseBytes(responseBytes).build();
  }

  public Optional<ArrayDeque<Bytes32>> getAccountHashes() {
    return accountHashes;
  }

  @Value.Immutable
  public interface CodeHashes {

    ArrayDeque<Bytes32> hashes();

    BigInteger responseBytes();
  }
}
