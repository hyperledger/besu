package org.hyperledger.besu.ethereum.core;

public class ModificationNotAllowedException extends RuntimeException {
  ModificationNotAllowedException() {
    super("This account may not be modified");
  }
}
