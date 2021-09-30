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
import javax.annotation.Nullable;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.immutables.value.Value;

public final class GetStorageRangeMessage extends AbstractSnapMessageData {

  public static GetStorageRangeMessage readFrom(final MessageData message) {
    if (message instanceof GetStorageRangeMessage) {
      return (GetStorageRangeMessage) message;
    }
    final int code = message.getCode();
    if (code != SnapV1.GET_STORAGE_RANGE) {
      throw new IllegalArgumentException(
          String.format("Message has code %d and thus is not a GetStorageRangeMessage.", code));
    }
    return new GetStorageRangeMessage(message.getData());
  }

  public static GetStorageRangeMessage create(
      final Hash worldStateRootHash,
      final List<Hash> accountHashes,
      final Hash startKeyHash,
      final BigInteger responseBytes) {
    return create(Optional.empty(), worldStateRootHash, accountHashes, startKeyHash, responseBytes);
  }

  public static GetStorageRangeMessage create(
      final Optional<BigInteger> requestId,
      final Hash worldStateRootHash,
      final List<Hash> accountHashes,
      final Hash startKeyHash,
      final BigInteger responseBytes) {
    final BytesValueRLPOutput tmp = new BytesValueRLPOutput();
    tmp.startList();
    requestId.ifPresent(tmp::writeBigIntegerScalar);
    tmp.writeBytes(worldStateRootHash);
    tmp.writeList(accountHashes, (hash, rlpOutput) -> rlpOutput.writeBytes(hash));
    tmp.writeBytes(startKeyHash);
    tmp.writeBigIntegerScalar(responseBytes);
    tmp.endList();
    return new GetStorageRangeMessage(tmp.encoded());
  }

  private GetStorageRangeMessage(final Bytes data) {
    super(data);
  }

  @Override
  protected Bytes wrap(final BigInteger requestId) {
    final GetStorageRangeMessage.StorageRange range = range(false);
    return create(
            Optional.of(requestId),
            range.worldStateRootHash(),
            range.accountHashes(),
            range.startKeyHash(),
            range.responseBytes())
        .getData();
  }

  @Override
  public int getCode() {
    return SnapV1.GET_STORAGE_RANGE;
  }

  public StorageRange range(final boolean withRequestId) {
    final RLPInput input = new BytesValueRLPInput(data, false);
    input.enterList();
    if (withRequestId) input.skipNext();
    final ImmutableStorageRange.Builder range =
        ImmutableStorageRange.builder()
            .worldStateRootHash(Hash.wrap(Bytes32.wrap(input.readBytes32())))
            .accountHashes(input.readList(rlpInput -> Hash.wrap(rlpInput.readBytes32())));
    if (input.nextIsNull()) {
      input.skipNext();
      range.startKeyHash(Hash.ZERO);
    } else {
      range.startKeyHash(Hash.wrap(Bytes32.wrap(input.readBytes32())));
    }
    if (input.nextIsNull()) {
      input.skipNext();
    } else {
      range.endKeyHash(Hash.wrap(Bytes32.wrap(input.readBytes32())));
    }
    range.responseBytes(input.readBigIntegerScalar());
    input.leaveList();
    return range.build();
  }

  @Value.Immutable
  public interface StorageRange {

    Hash worldStateRootHash();

    List<Hash> accountHashes();

    Hash startKeyHash();

    @Nullable
    Hash endKeyHash();

    BigInteger responseBytes();
  }
}
