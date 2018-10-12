package tech.pegasys.pantheon.ethereum.vm;

import tech.pegasys.pantheon.ethereum.core.Address;

import com.fasterxml.jackson.annotation.JsonCreator;

/** A AccountAddress mock for testing. */
public class AddressMock extends Address {

  /**
   * Public constructor.
   *
   * @param value The value the AccountAddress represents.
   */
  @JsonCreator
  public AddressMock(final String value) {
    super(Address.fromHexString(value));
  }
}
