package org.hyperledger.besu.ethereum.bonsai.trielog;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.bonsai.BonsaiValue;
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
import java.util.stream.Collectors;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;

public class ZkTrieLogFactoryImpl extends TrieLogFactoryImpl {

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

      final BonsaiValue<StateTrieAccountValue> accountChange = layer.accounts.get(address);

      if (accountChange == null || accountChange.isUnchanged()) {
        output.writeNull();
      } else {
        accountChange.writeRlp(output, (o, sta) -> sta.writeTo(o));
      }

      // get storage changes for this address, filtering out self-destructed slots:
      final Map<StorageSlotKey, BonsaiValue<UInt256>> storageChanges =
          Optional.ofNullable(layer.storage.get(address))
              .map(
                  d ->
                      d.entrySet().stream()
                          .filter(k -> k.getKey().slotKey().isPresent())
                          .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)))
              .orElse(null);

      if (storageChanges == null || storageChanges.isEmpty()) {
        output.writeNull();
      } else {
        output.startList();
        for (final Map.Entry<StorageSlotKey, BonsaiValue<UInt256>> storageChangeEntry :
            storageChanges.entrySet()) {
          output.startList();
          output.writeUInt256Scalar(
              storageChangeEntry
                  .getKey()
                  .slotKey()
                  .orElseThrow(() -> new IllegalStateException("Slot key is not present")));
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
          input.enterList();
          final UInt256 slotKey = input.readUInt256Scalar();
          final StorageSlotKey storageSlotKey = new StorageSlotKey(slotKey);
          final UInt256 oldValue = nullOrValue(input, RLPInput::readUInt256Scalar);
          final UInt256 newValue = nullOrValue(input, RLPInput::readUInt256Scalar);
          final boolean isCleared = getOptionalIsCleared(input);
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
}
