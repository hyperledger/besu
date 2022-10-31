package org.hyperledger.besu.datatypes;

import org.apache.tuweni.bytes.Bytes32;

public class Constants {

  /** Constant representing uninitialized or emptied storage values */
  public static final Bytes32 ZERO_32 = Bytes32.wrap(new byte[32]);

  private Constants() {
    // non-instantiable class
  }
}
