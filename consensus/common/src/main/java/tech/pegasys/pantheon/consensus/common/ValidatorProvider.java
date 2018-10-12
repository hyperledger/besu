package tech.pegasys.pantheon.consensus.common;

import tech.pegasys.pantheon.ethereum.core.Address;

import java.util.Collection;

public interface ValidatorProvider {

  // Returns the current list of validators
  Collection<Address> getCurrentValidators();
}
