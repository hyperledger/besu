package org.hyperledger.besu.datatypes;

import java.util.List;

import org.apache.tuweni.units.bigints.UInt256;

public interface AccessWitness {

  void merge(AccessWitness other);

  List<Address> keys();

  AccessWitness copy();

  long touchAndChargeProofOfAbsence(Address address);

  long touchAndChargeValueTransfer(Address caller, Address target);

  long touchAndChargeMessageCall(Address address);

  long touchTxOriginAndComputeGas(Address origin);

  long touchTxExistingAndComputeGas(Address target, boolean sendsValue);

  long touchAndChargeContractCreateInit(Address address, boolean createSendsValue);

  long touchAndChargeContractCreateCompleted(final Address address);

  long touchAddressOnWriteAndComputeGas(Address address, UInt256 treeIndex, UInt256 subIndex);

  long touchAddressOnReadAndComputeGas(Address address, UInt256 treeIndex, UInt256 subIndex);

  List<UInt256> getStorageSlotTreeIndexes(UInt256 storageKey);

  long touchCodeChunksUponContractCreation(Address address, long codeLength);
}
