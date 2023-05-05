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
package org.hyperledger.besu.ethereum.bonsai.trielog;

import org.hyperledger.besu.datatypes.AccountValue;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.StorageSlotKey;
import org.hyperledger.besu.ethereum.bonsai.BonsaiValue;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPInput;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.ethereum.rlp.RLPOutput;
import org.hyperledger.besu.ethereum.worldstate.StateTrieAccountValue;
import org.hyperledger.besu.plugin.data.BlockHeader;
import org.hyperledger.besu.plugin.services.trielogs.TrieLogAccumulator;
import org.hyperledger.besu.plugin.services.trielogs.TrieLogFactory;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;

public class ZkTrieLogFactoryImpl implements TrieLogFactory<TrieLogLayer> {

  // DUPLICATED here because this code will be in the plugin, and cannot extend TrieLogFactoryImpl
  @Override
  public <U extends TrieLogAccumulator> TrieLogLayer create(
      final U accumulator, final BlockHeader blockHeader) {
    TrieLogLayer layer = new TrieLogLayer();
    layer.setBlockHash(blockHeader.getBlockHash());
    layer.setBlockNumber(blockHeader.getNumber());
    for (final var updatedAccount : accumulator.getAccountsToUpdate().entrySet()) {
      final var bonsaiValue = updatedAccount.getValue();
      final var oldAccountValue = bonsaiValue.getPrior();
      final var newAccountValue = bonsaiValue.getUpdated();
      if (oldAccountValue == null && newAccountValue == null) {
        // by default do not persist empty reads of accounts to the trie log
        continue;
      }
      layer.addAccountChange(updatedAccount.getKey(), oldAccountValue, newAccountValue);
    }

    for (final var updatedCode : accumulator.getCodeToUpdate().entrySet()) {
      layer.addCodeChange(
          updatedCode.getKey(),
          updatedCode.getValue().getPrior(),
          updatedCode.getValue().getUpdated(),
          blockHeader.getBlockHash());
    }

    for (final var updatesStorage : accumulator.getStorageToUpdate().entrySet()) {
      final Address address = updatesStorage.getKey();
      for (final var slotUpdate : updatesStorage.getValue().entrySet()) {
        var val = slotUpdate.getValue();

        if (val.getPrior() == null && val.getUpdated() == null) {
          // by default do not persist empty reads to the trie log
          continue;
        }

        layer.addStorageChange(address, slotUpdate.getKey(), val.getPrior(), val.getUpdated());
      }
    }
    return layer;
  }

  @Override
  public byte[] serialize(final TrieLogLayer layer) {
    final BytesValueRLPOutput rlpLog = new BytesValueRLPOutput();
    writeTo(layer, rlpLog);
    return rlpLog.encoded().toArrayUnsafe();
  }

  public static void writeTo(final TrieLogLayer layer, final RLPOutput output) {
    layer.freeze();

    final Set<Address> addresses = new TreeSet<>();
    addresses.addAll(layer.getAccounts().keySet());
    addresses.addAll(layer.getCode().keySet());
    addresses.addAll(layer.getStorage().keySet());

    output.startList(); // container
    output.writeBytes(layer.blockHash);

    for (final Address address : addresses) {
      output.startList(); // this change
      output.writeBytes(address);

      final BonsaiValue<Bytes> codeChange = layer.code.get(address);
      if (codeChange == null || codeChange.isUnchanged()) {
        output.writeNull();
      } else {
        codeChange.writeRlp(output, RLPOutput::writeBytes);
      }

      final BonsaiValue<AccountValue> accountChange = layer.accounts.get(address);

      if (accountChange == null) {
        output.writeNull();
      } else {
        accountChange.writeRlp(output, (o, sta) -> sta.writeTo(o));
      }

      // get storage changes for this address:
      final Map<StorageSlotKey, BonsaiValue<UInt256>> storageChanges = layer.storage.get(address);

      if (storageChanges == null || storageChanges.isEmpty()) {
        output.writeNull();
      } else {
        output.startList();
        for (final Map.Entry<StorageSlotKey, BonsaiValue<UInt256>> storageChangeEntry :
            storageChanges.entrySet()) {
          output.startList();

          StorageSlotKey storageSlotKey = storageChangeEntry.getKey();
          output.writeBytes(storageSlotKey.getSlotHash());
          storageChangeEntry.getValue().writeInnerRlp(output, RLPOutput::writeUInt256Scalar);
          if (storageSlotKey.getSlotKey().isPresent()) {
            output.writeUInt256Scalar(storageSlotKey.getSlotKey().get());
          }
          output.endList();
        }
        output.endList();
      }

      output.endList(); // this change
    }
    output.endList(); // container
  }

  @Override
  public TrieLogLayer deserialize(final byte[] bytes) {
    return readFrom(new BytesValueRLPInput(Bytes.wrap(bytes), false));
  }

  public static TrieLogLayer readFrom(final RLPInput input) {
    final TrieLogLayer newLayer = new TrieLogLayer();

    input.enterList();
    newLayer.blockHash = Hash.wrap(input.readBytes32());

    while (!input.isEndOfCurrentList()) {
      input.enterList();
      final Address address = Address.readFrom(input);

      if (input.nextIsNull()) {
        input.skipNext();
      } else {
        input.enterList();
        final Bytes oldCode = nullOrValue(input, RLPInput::readBytes);
        final Bytes newCode = nullOrValue(input, RLPInput::readBytes);
        final boolean isCleared = getOptionalIsCleared(input);
        input.leaveList();
        newLayer.code.put(address, new BonsaiValue<>(oldCode, newCode, isCleared));
      }

      if (input.nextIsNull()) {
        input.skipNext();
      } else {
        input.enterList();
        final StateTrieAccountValue oldValue = nullOrValue(input, StateTrieAccountValue::readFrom);
        final StateTrieAccountValue newValue = nullOrValue(input, StateTrieAccountValue::readFrom);
        final boolean isCleared = getOptionalIsCleared(input);
        input.leaveList();
        newLayer.accounts.put(address, new BonsaiValue<>(oldValue, newValue, isCleared));
      }

      if (input.nextIsNull()) {
        input.skipNext();
      } else {
        final Map<StorageSlotKey, BonsaiValue<UInt256>> storageChanges = new TreeMap<>();
        input.enterList();
        while (!input.isEndOfCurrentList()) {
          int storageElementlistSize = input.enterList();

          final Hash slotHash = Hash.wrap(input.readBytes32());
          final UInt256 oldValue = nullOrValue(input, RLPInput::readUInt256Scalar);
          final UInt256 newValue = nullOrValue(input, RLPInput::readUInt256Scalar);
          final boolean isCleared = getOptionalIsCleared(input);
          final Optional<UInt256> slotKey =
              Optional.of(storageElementlistSize)
                  .filter(listSize -> listSize == 5)
                  .map(__ -> input.readUInt256Scalar())
                  .or(Optional::empty);

          final StorageSlotKey storageSlotKey = new StorageSlotKey(slotHash, slotKey);

          storageChanges.put(storageSlotKey, new BonsaiValue<>(oldValue, newValue, isCleared));
          input.leaveList();
        }
        input.leaveList();
        newLayer.storage.put(address, storageChanges);
      }

      // lenient leave list for forward compatible additions.
      input.leaveListLenient();
    }
    input.leaveListLenient();
    newLayer.freeze();

    return newLayer;
  }
  protected static <T> T nullOrValue(final RLPInput input, final Function<RLPInput, T> reader) {
    if (input.nextIsNull()) {
      input.skipNext();
      return null;
    } else {
      return reader.apply(input);
    }
  }

  protected static boolean getOptionalIsCleared(final RLPInput input) {
    return Optional.of(input.isEndOfCurrentList())
        .filter(isEnd -> !isEnd) // isCleared is optional
        .map(__ -> nullOrValue(input, RLPInput::readInt))
        .filter(i -> i == 1)
        .isPresent();
  }
}
