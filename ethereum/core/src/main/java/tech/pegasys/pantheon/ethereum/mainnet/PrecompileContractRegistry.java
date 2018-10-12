package tech.pegasys.pantheon.ethereum.mainnet;

import tech.pegasys.pantheon.ethereum.core.Address;

import java.util.HashMap;
import java.util.Map;

/** Encapsulates a group of {@link PrecompiledContract}s used together. */
public class PrecompileContractRegistry {

  private final Map<Address, PrecompiledContract> precompiles;

  public PrecompileContractRegistry() {
    this.precompiles = new HashMap<>();
  }

  public PrecompiledContract get(final Address address) {
    return precompiles.get(address);
  }

  public void put(final Address address, final PrecompiledContract precompile) {
    precompiles.put(address, precompile);
  }
}
