/*
 * Copyright Hyperledger Besu Contributors.
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
 *
 */
package org.hyperledger.besu.ethereum.bonsai.trielog;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.bonsai.BonsaiAccount;
import org.hyperledger.besu.ethereum.bonsai.BonsaiValue;
import org.hyperledger.besu.ethereum.bonsai.worldview.BonsaiWorldStateUpdateAccumulator;
import org.hyperledger.besu.ethereum.bonsai.worldview.StorageSlotKey;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPInput;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.ethereum.rlp.RLPOutput;
import org.hyperledger.besu.ethereum.worldstate.StateTrieAccountValue;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;

public class TrieLogFactoryImpl implements TrieLogFactory<TrieLogLayer> {

  @Override
  public TrieLogLayer create(
      final BonsaiWorldStateUpdateAccumulator accumulator, final Hash blockHash) {
    TrieLogLayer layer = new TrieLogLayer();
    layer.setBlockHash(blockHash);
    for (final Map.Entry<Address, BonsaiValue<BonsaiAccount>> updatedAccount :
        accumulator.getAccountsToUpdate().entrySet()) {
      final BonsaiValue<BonsaiAccount> bonsaiValue = updatedAccount.getValue();
      final BonsaiAccount oldValue = bonsaiValue.getPrior();
      final StateTrieAccountValue oldAccount =
          oldValue == null
              ? null
              : new StateTrieAccountValue(
                  oldValue.getNonce(),
                  oldValue.getBalance(),
                  oldValue.getStorageRoot(),
                  oldValue.getCodeHash());
      final BonsaiAccount newValue = bonsaiValue.getUpdated();
      final StateTrieAccountValue newAccount =
          newValue == null
              ? null
              : new StateTrieAccountValue(
                  newValue.getNonce(),
                  newValue.getBalance(),
                  newValue.getStorageRoot(),
                  newValue.getCodeHash());
      if (oldValue == null && newValue == null) {
        // by default do not persist empty reads of accounts to the trie log
        continue;
      }
      layer.addAccountChange(updatedAccount.getKey(), oldAccount, newAccount);
    }

    for (final Map.Entry<Address, BonsaiValue<Bytes>> updatedCode :
        accumulator.getCodeToUpdate().entrySet()) {
      layer.addCodeChange(
          updatedCode.getKey(),
          updatedCode.getValue().getPrior(),
          updatedCode.getValue().getUpdated(),
          blockHash);
    }

    for (final Map.Entry<
            Address,
            BonsaiWorldStateUpdateAccumulator.StorageConsumingMap<
                StorageSlotKey, BonsaiValue<UInt256>>>
        updatesStorage : accumulator.getStorageToUpdate().entrySet()) {
      final Address address = updatesStorage.getKey();
      for (final Map.Entry<StorageSlotKey, BonsaiValue<UInt256>> slotUpdate :
          updatesStorage.getValue().entrySet()) {
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

      final BonsaiValue<StateTrieAccountValue> accountChange = layer.accounts.get(address);
      if (accountChange == null || accountChange.isUnchanged()) {
        output.writeNull();
      } else {
        accountChange.writeRlp(output, (o, sta) -> sta.writeTo(o));
      }

      final BonsaiValue<Bytes> codeChange = layer.code.get(address);
      if (codeChange == null || codeChange.isUnchanged()) {
        output.writeNull();
      } else {
        codeChange.writeRlp(output, RLPOutput::writeBytes);
      }

      final Map<StorageSlotKey, BonsaiValue<UInt256>> storageChanges = layer.storage.get(address);
      if (storageChanges == null) {
        output.writeNull();
      } else {
        output.startList();
        for (final Map.Entry<StorageSlotKey, BonsaiValue<UInt256>> storageChangeEntry :
            storageChanges.entrySet()) {
          output.startList();
          // do not write slotKey, it is not used in mainnet bonsai trielogs
          output.writeBytes(storageChangeEntry.getKey().slotHash());
          storageChangeEntry.getValue().writeInnerRlp(output, RLPOutput::writeUInt256Scalar);
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
        final StateTrieAccountValue oldValue = nullOrValue(input, StateTrieAccountValue::readFrom);
        final StateTrieAccountValue newValue = nullOrValue(input, StateTrieAccountValue::readFrom);
        final boolean isCleared = getOptionalIsCleared(input);
        input.leaveList();
        newLayer.accounts.put(address, new BonsaiValue<>(oldValue, newValue, isCleared));
      }

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
        final Map<StorageSlotKey, BonsaiValue<UInt256>> storageChanges = new TreeMap<>();
        input.enterList();
        while (!input.isEndOfCurrentList()) {
          input.enterList();
          final Hash slotHash = Hash.wrap(input.readBytes32());
          final StorageSlotKey storageSlotKey = new StorageSlotKey(slotHash, Optional.empty());
          final UInt256 oldValue = nullOrValue(input, RLPInput::readUInt256Scalar);
          final UInt256 newValue = nullOrValue(input, RLPInput::readUInt256Scalar);
          final boolean isCleared = getOptionalIsCleared(input);
          storageChanges.put(storageSlotKey, new BonsaiValue<>(oldValue, newValue, isCleared));
          input.leaveList();
        }
        input.leaveList();
        newLayer.storage.put(address, storageChanges);
      }

      // TODO add trie nodes

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
