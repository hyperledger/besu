package org.hyperledger.besu.datatypes;

import org.apache.tuweni.units.bigints.UInt256;

import java.util.ArrayList;
import java.util.List;

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

  long touchAddressOnWriteAndComputeGas(Address address,  int treeIndex,  int subIndex);

  long touchAddressOnReadAndComputeGas(Address address,  int treeIndex,  int subIndex);

  List<Integer> getStorageSlotTreeIndexes(UInt256 storageKey);
  long touchCodeChunksUponContractCreation(Address address, long codeLength);
}
