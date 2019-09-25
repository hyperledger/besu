package org.hyperledger.besu.ethereum.core;

import org.hyperledger.besu.util.bytes.Bytes32;
import org.hyperledger.besu.util.bytes.BytesValue;
import org.hyperledger.besu.util.uint.UInt256;

import java.util.NavigableMap;

public class DefaultEvmAccount implements EvmAccount {
    private MutableAccount mutableAccount;

    public boolean isImmutable() {
        return isImmutable;
    }

    public void setImmutable(final boolean immutable) {
        isImmutable = immutable;
    }

    private boolean isImmutable;



    public DefaultEvmAccount(final MutableAccount mutableAccount) {
        this.mutableAccount = mutableAccount;
        this.isImmutable = false;
    }

    @Override
    public MutableAccount getMutable() throws ModificationNotAllowedException {
        if (isImmutable) {
            throw new ModificationNotAllowedException();
        }
        return mutableAccount;
    }

    @Override
    public Address getAddress() {
        return mutableAccount.getAddress();
    }

    @Override
    public Hash getAddressHash() {
        return mutableAccount.getAddressHash();
    }

    @Override
    public long getNonce() {
        return mutableAccount.getNonce();
    }

    @Override
    public Wei getBalance() {
        return mutableAccount.getBalance();
    }

    @Override
    public BytesValue getCode() {
        return mutableAccount.getCode();
    }

    @Override
    public Hash getCodeHash() {
        return mutableAccount.getCodeHash();
    }

    @Override
    public int getVersion() {
        return mutableAccount.getVersion();
    }

    @Override
    public UInt256 getStorageValue(final UInt256 key) {
        return mutableAccount.getStorageValue(key);
    }

    @Override
    public UInt256 getOriginalStorageValue(final UInt256 key) {
        return mutableAccount.getOriginalStorageValue(key);
    }

    @Override
    public NavigableMap<Bytes32, AccountStorageEntry> storageEntriesFrom(final Bytes32 startKeyHash, final int limit) {
        return mutableAccount.storageEntriesFrom(startKeyHash, limit);
    }

    static class ModificationNotAllowedException extends Exception {
        ModificationNotAllowedException() {
            super("This account may not be modified");
        }
    }
}
