package org.hyperledger.besu.plugin.services.storage;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.AccountValue;

import java.util.List;
import java.util.Optional;

public interface WorldStateProof {

    Optional<AccountValue> getStateTrieAccountValue();
    List<Bytes> getAccountProof();
    List<UInt256> getStorageKeys();
    UInt256 getStorageValue(final UInt256 key);
    List<Bytes> getStorageProof(final UInt256 key);
}
