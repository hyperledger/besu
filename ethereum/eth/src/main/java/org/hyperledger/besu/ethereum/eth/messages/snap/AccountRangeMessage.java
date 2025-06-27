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
package org.hyperledger.besu.ethereum.eth.messages.snap;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.AbstractSnapMessageData;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPInput;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.ethereum.rlp.RLPOutput;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Maps;
import kotlin.collections.ArrayDeque;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.immutables.value.Value;

public final class AccountRangeMessage extends AbstractSnapMessageData {

  public AccountRangeMessage(final Bytes data) {
    super(data);
  }

  public static AccountRangeMessage readFrom(final MessageData message) {
    if (message instanceof AccountRangeMessage) {
      return (AccountRangeMessage) message;
    }
    final int code = message.getCode();
    if (code != SnapV1.ACCOUNT_RANGE) {
      throw new IllegalArgumentException(
          String.format("Message has code %d and thus is not a AccountRangeMessage.", code));
    }
    return new AccountRangeMessage(message.getData());
  }

  public static AccountRangeMessage create(
      final Map<Bytes32, Bytes> accounts, final List<Bytes> proof) {
    return create(Optional.empty(), accounts, proof);
  }

  public static AccountRangeMessage create(
      final Optional<BigInteger> requestId,
      final Map<Bytes32, Bytes> accounts,
      final List<Bytes> proof) {
    final BytesValueRLPOutput tmp = new BytesValueRLPOutput();
    tmp.startList();
    requestId.ifPresent(tmp::writeBigIntegerScalar);
    tmp.writeList(
        accounts.entrySet(),
        (entry, rlpOutput) -> {
          rlpOutput.startList();
          rlpOutput.writeBytes(entry.getKey());
          rlpOutput.writeRLPBytes(entry.getValue());
          rlpOutput.endList();
        });
    tmp.writeList(proof, (bytes, rlpOutput) -> rlpOutput.writeBytes(bytes));
    tmp.endList();
    return new AccountRangeMessage(tmp.encoded());
  }

  @Override
  protected Bytes wrap(final BigInteger requestId) {
    final AccountRangeData accountData = accountData(false);
    return create(Optional.of(requestId), accountData.accounts(), accountData.proofs()).getData();
  }

  @Override
  public int getCode() {
    return SnapV1.ACCOUNT_RANGE;
  }

  public AccountRangeData accountData(final boolean withRequestId) {
    final TreeMap<Bytes32, Bytes> accounts = new TreeMap<>();
    final ArrayDeque<Bytes> proofs = new ArrayDeque<>();
    final RLPInput input = new BytesValueRLPInput(data, false);
    input.enterList();

    if (withRequestId) input.skipNext();

    input
        .readList(
            rlpInput -> {
              rlpInput.enterList();
              Map.Entry<Bytes32, Bytes> entry =
                  Maps.immutableEntry(rlpInput.readBytes32(), toFullAccount(rlpInput.readAsRlp()));
              rlpInput.leaveList();
              return entry;
            })
        .forEach(entry -> accounts.put(entry.getKey(), entry.getValue()));

    input.enterList();
    while (!input.isEndOfCurrentList()) {
      proofs.add(input.readBytes());
    }
    input.leaveList();

    input.leaveList();
    return ImmutableAccountRangeData.builder().accounts(accounts).proofs(proofs).build();
  }

  @VisibleForTesting
  public static Bytes toFullAccount(final RLPInput rlpInput) {
    BytesValueRLPOutput rlpOutput = new BytesValueRLPOutput();
    rlpOutput.startList();

    List<RLPInput> rlpInputs = readAllElementsFromList(rlpInput);
    // Write Nonce and Balance as-is
    rlpOutput.writeRaw(rlpInputs.getFirst().raw());
    rlpOutput.writeRaw(rlpInputs.get(1).raw());
    // Write Storage Root and Code Hash, defaulting to empty hashes if null
    writeBytesOrEmptyHash(rlpOutput, rlpInputs.get(2), Hash.EMPTY_TRIE_HASH);
    writeBytesOrEmptyHash(rlpOutput, rlpInputs.get(3), Hash.EMPTY);

    rlpOutput.endList();
    return rlpOutput.encoded();
  }

  private static void writeBytesOrEmptyHash(
      final RLPOutput rlpOutput, final RLPInput rlpInput, final Hash emptyHash) {
    if (rlpInput.nextIsNull()) {
      rlpOutput.writeBytes(emptyHash);
      rlpInput.skipNext();
    } else {
      rlpOutput.writeBytes(rlpInput.readBytes32());
    }
  }

  public static Bytes toSlimAccount(final RLPInput rlpInput) {
    BytesValueRLPOutput rlpOutput = new BytesValueRLPOutput();
    rlpOutput.startList();

    List<RLPInput> rlpInputs = readAllElementsFromList(rlpInput);
    // Write Nonce and Balance as-is
    rlpOutput.writeRaw(rlpInputs.getFirst().raw());
    rlpOutput.writeRaw(rlpInputs.get(1).raw());
    // Write Storage Root and Code Hash, writing null for empty hashes
    writeBytesOrNull(rlpOutput, rlpInputs.get(2), Hash.EMPTY_TRIE_HASH);
    writeBytesOrNull(rlpOutput, rlpInputs.get(3), Hash.EMPTY);

    rlpOutput.endList();
    return rlpOutput.encoded();
  }

  private static void writeBytesOrNull(
      final RLPOutput rlpOutput, final RLPInput rlpInput, final Hash hash) {
    if (rlpInput.nextIsNull()) {
      rlpOutput.writeNull();
      rlpInput.skipNext();
    } else {
      Bytes32 bytes = rlpInput.readBytes32();
      if (hash.equals(bytes)) {
        rlpOutput.writeNull();
      } else {
        rlpOutput.writeBytes(bytes);
      }
    }
  }

  private static List<RLPInput> readAllElementsFromList(final RLPInput rlpInput) {
    ArrayList<RLPInput> inputs = new ArrayList<>();
    rlpInput.enterList();
    while (!rlpInput.isEndOfCurrentList()) {
      inputs.add(rlpInput.readAsRlp());
    }
    rlpInput.leaveList();
    return inputs;
  }

  @Value.Immutable
  public interface AccountRangeData {

    NavigableMap<Bytes32, Bytes> accounts();

    ArrayDeque<Bytes> proofs();
  }
}
