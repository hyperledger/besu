package org.hyperledger.besu.datatypes;

import java.util.List;

import org.apache.tuweni.bytes.Bytes32;

/** An access list entry as defined in EIP-2930 */
public interface AccessListEntry {
  /**
   * Gets address of the access list entry
   *
   * @return the address
   */
  Address getAddress();

  /**
   * Gets storage keys of the access list entry
   *
   * @return the storage keys
   */
  List<Bytes32> getStorageKeys();
}
