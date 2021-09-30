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
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.AbstractSnapMessageData;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPInput;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.ethereum.rlp.RLPInput;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.immutables.value.Value;

public final class GetByteCodesMessage extends AbstractSnapMessageData {

  public static GetByteCodesMessage readFrom(final MessageData message) {
    if (message instanceof GetByteCodesMessage) {
      return (GetByteCodesMessage) message;
    }
    final int code = message.getCode();
    if (code != SnapV1.GET_BYTECODES) {
      throw new IllegalArgumentException(
          String.format("Message has code %d and thus is not a GetByteCodesMessage.", code));
    }
    return new GetByteCodesMessage(message.getData());
  }

  public static GetByteCodesMessage create(
      final List<Hash> hashes, final BigInteger responseBytes) {
    return create(Optional.empty(), hashes, responseBytes);
  }

  public static GetByteCodesMessage create(
      final Optional<BigInteger> requestId,
      final List<Hash> hashes,
      final BigInteger responseBytes) {
    final BytesValueRLPOutput tmp = new BytesValueRLPOutput();
    tmp.startList();
    requestId.ifPresent(tmp::writeBigIntegerScalar);
    tmp.writeList(hashes, (hash, rlpOutput) -> rlpOutput.writeBytes(hash));
    tmp.writeBigIntegerScalar(responseBytes);
    tmp.endList();
    return new GetByteCodesMessage(tmp.encoded());
  }

  private GetByteCodesMessage(final Bytes data) {
    super(data);
  }

  @Override
  protected Bytes wrap(final BigInteger requestId) {
    final GetByteCodesMessage.ByteCodesRequest request = request(false);
    return create(Optional.of(requestId), request.hashes(), request.responseBytes()).getData();
  }

  @Override
  public int getCode() {
    return SnapV1.GET_BYTECODES;
  }

  public ByteCodesRequest request(final boolean withRequestId) {
    final RLPInput input = new BytesValueRLPInput(data, false);
    input.enterList();
    if (withRequestId) input.skipNext();
    final ImmutableByteCodesRequest request =
        ImmutableByteCodesRequest.builder()
            .hashes(input.readList(rlpInput -> Hash.wrap(rlpInput.readBytes32())))
            .responseBytes(input.readBigIntegerScalar())
            .build();
    input.leaveList();
    return request;
  }

  @Value.Immutable
  public interface ByteCodesRequest {

    List<Hash> hashes();

    BigInteger responseBytes();
  }
}
