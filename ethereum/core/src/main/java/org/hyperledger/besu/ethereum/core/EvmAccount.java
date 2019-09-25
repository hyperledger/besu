package org.hyperledger.besu.ethereum.core;

public interface EvmAccount extends Account {

    public MutableAccount getMutable() throws DefaultEvmAccount.ModificationNotAllowedException;

}
