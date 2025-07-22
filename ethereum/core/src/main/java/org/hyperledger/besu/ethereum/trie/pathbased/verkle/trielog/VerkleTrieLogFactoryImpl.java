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
package org.hyperledger.besu.ethereum.trie.pathbased.verkle.trielog;

import org.hyperledger.besu.datatypes.AccountValue;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.StorageSlotKey;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPInput;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.ethereum.rlp.RLPOutput;
import org.hyperledger.besu.ethereum.trie.common.VerkleStateTrieAccountValue;
import org.hyperledger.besu.ethereum.trie.pathbased.common.PathBasedValue;
import org.hyperledger.besu.ethereum.trie.pathbased.common.trielog.TrieLogFactoryImpl;
import org.hyperledger.besu.ethereum.trie.pathbased.common.trielog.TrieLogLayer;
import org.hyperledger.besu.ethereum.trie.pathbased.verkle.VerkleAccount;
import org.hyperledger.besu.plugin.services.trielogs.TrieLog;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;

public class VerkleTrieLogFactoryImpl extends TrieLogFactoryImpl {

  @Override
  public byte[] serialize(final TrieLog layer) {
    final BytesValueRLPOutput rlpLog = new BytesValueRLPOutput();
    writeTo(layer, rlpLog);
    return rlpLog.encoded().toArrayUnsafe();
  }

  public static void writeTo(final TrieLog layer, final RLPOutput output) {
    layer.freeze();

    final Set<Address> addresses = new TreeSet<>();
    addresses.addAll(layer.getAccountChanges().keySet());
    addresses.addAll(layer.getCodeChanges().keySet());
    addresses.addAll(layer.getStorageChanges().keySet());

    output.startList();
    output.writeBytes(layer.getBlockHash());

    for (final Address address : addresses) {
      output.startList();
      output.writeBytes(address);

      final TrieLog.LogTuple<AccountValue> accountChange = layer.getAccountChanges().get(address);
      if (accountChange == null || accountChange.isUnchanged()) {
        output.writeNull();
      } else {
        writeRlp(
            accountChange,
            output,
            (o, sta) -> {
              if (sta instanceof VerkleAccount verkleAccount) {
                new VerkleStateTrieAccountValue(
                        verkleAccount.getNonce(),
                        verkleAccount.getBalance(),
                        verkleAccount.getCodeHash(),
                        verkleAccount.getCodeSize())
                    .writeTo(o);
              } else {
                sta.writeTo(o);
              }
            });
      }

      final TrieLog.LogTuple<Bytes> codeChange = layer.getCodeChanges().get(address);
      if (codeChange == null || codeChange.isUnchanged()) {
        output.writeNull();
      } else {
        writeRlp(codeChange, output, RLPOutput::writeBytes);
      }

      final Map<StorageSlotKey, TrieLog.LogTuple<UInt256>> storageChanges =
          layer.getStorageChanges().get(address);
      if (storageChanges == null) {
        output.writeNull();
      } else {
        output.startList();
        for (final Map.Entry<StorageSlotKey, TrieLog.LogTuple<UInt256>> storageChangeEntry :
            storageChanges.entrySet()) {
          output.startList();
          StorageSlotKey storageSlotKey = storageChangeEntry.getKey();
          output.writeBytes(storageSlotKey.getSlotHash());
          writeInnerRlp(storageChangeEntry.getValue(), output, RLPOutput::writeBytes);
          if (storageSlotKey.getSlotKey().isPresent()) {
            output.writeUInt256Scalar(storageSlotKey.getSlotKey().get());
          }
          output.endList();
        }
        output.endList();
      }

      output.endList();
    }
    output.endList();
  }

  @Override
  public TrieLogLayer deserialize(final byte[] bytes) {
    return readFrom(new BytesValueRLPInput(Bytes.wrap(bytes), false));
  }

  public static TrieLogLayer readFrom(final RLPInput input) {
    final TrieLogLayer newLayer = new TrieLogLayer();

    input.enterList();
    newLayer.setBlockHash(Hash.wrap(input.readBytes32()));

    while (!input.isEndOfCurrentList()) {
      input.enterList();
      final Address address = Address.readFrom(input);

      if (input.nextIsNull()) {
        input.skipNext();
      } else {
        input.enterList();
        final VerkleStateTrieAccountValue oldValue =
            nullOrValue(input, VerkleStateTrieAccountValue::readFrom);
        final VerkleStateTrieAccountValue newValue =
            nullOrValue(input, VerkleStateTrieAccountValue::readFrom);
        final boolean isCleared = getOptionalIsCleared(input);
        input.leaveList();
        newLayer
            .getAccountChanges()
            .put(address, new PathBasedValue<>(oldValue, newValue, isCleared));
      }

      if (input.nextIsNull()) {
        input.skipNext();
      } else {
        input.enterList();
        final Bytes oldCode = nullOrValue(input, RLPInput::readBytes);
        final Bytes newCode = nullOrValue(input, RLPInput::readBytes);
        final boolean isCleared = getOptionalIsCleared(input);
        input.leaveList();
        newLayer.getCodeChanges().put(address, new PathBasedValue<>(oldCode, newCode, isCleared));
      }

      if (input.nextIsNull()) {
        input.skipNext();
      } else {
        final Map<StorageSlotKey, PathBasedValue<UInt256>> storageChanges = new TreeMap<>();
        input.enterList();
        while (!input.isEndOfCurrentList()) {
          int storageElementlistSize = input.enterList();
          final Hash slotHash = Hash.wrap(input.readBytes32());
          final UInt256 oldValue =
              nullOrValue(input, rlpInput -> UInt256.fromBytes(rlpInput.readBytes()));
          final UInt256 newValue =
              nullOrValue(input, rlpInput -> UInt256.fromBytes(rlpInput.readBytes()));
          final boolean isCleared = getOptionalIsCleared(input);
          final Optional<UInt256> slotKey =
              Optional.of(storageElementlistSize)
                  .filter(listSize -> listSize == 5)
                  .map(__ -> input.readUInt256Scalar())
                  .or(Optional::empty);

          final StorageSlotKey storageSlotKey = new StorageSlotKey(slotHash, slotKey);
          storageChanges.put(storageSlotKey, new PathBasedValue<>(oldValue, newValue, isCleared));
          input.leaveList();
        }
        input.leaveList();
        newLayer.getStorageChanges().put(address, storageChanges);
      }

      // lenient leave list for forward compatible additions.
      input.leaveListLenient();
    }
    input.leaveListLenient();
    newLayer.freeze();

    return newLayer;
  }
}
