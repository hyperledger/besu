package tech.pegasys.pantheon.ethereum.mainnet;

import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.mainnet.precompiles.AltBN128AddPrecompiledContract;
import tech.pegasys.pantheon.ethereum.mainnet.precompiles.AltBN128MulPrecompiledContract;
import tech.pegasys.pantheon.ethereum.mainnet.precompiles.AltBN128PairingPrecompiledContract;
import tech.pegasys.pantheon.ethereum.mainnet.precompiles.BigIntegerModularExponentiationPrecompiledContract;
import tech.pegasys.pantheon.ethereum.mainnet.precompiles.ECRECPrecompiledContract;
import tech.pegasys.pantheon.ethereum.mainnet.precompiles.IDPrecompiledContract;
import tech.pegasys.pantheon.ethereum.mainnet.precompiles.RIPEMD160PrecompiledContract;
import tech.pegasys.pantheon.ethereum.mainnet.precompiles.SHA256PrecompiledContract;
import tech.pegasys.pantheon.ethereum.vm.GasCalculator;

/** Provides the various precompiled contracts used on mainnet hard forks. */
public abstract class MainnetPrecompiledContractRegistries {

  private MainnetPrecompiledContractRegistries() {}

  private static void populateForFrontier(
      final PrecompileContractRegistry registry, final GasCalculator gasCalculator) {
    registry.put(Address.ECREC, new ECRECPrecompiledContract(gasCalculator));
    registry.put(Address.SHA256, new SHA256PrecompiledContract(gasCalculator));
    registry.put(Address.RIPEMD160, new RIPEMD160PrecompiledContract(gasCalculator));
    registry.put(Address.ID, new IDPrecompiledContract(gasCalculator));
  }

  public static PrecompileContractRegistry frontier(final GasCalculator gasCalculator) {
    final PrecompileContractRegistry registry = new PrecompileContractRegistry();
    populateForFrontier(registry, gasCalculator);
    return registry;
  }

  public static PrecompileContractRegistry byzantium(final GasCalculator gasCalculator) {
    final PrecompileContractRegistry registry = new PrecompileContractRegistry();
    populateForFrontier(registry, gasCalculator);
    registry.put(
        Address.MODEXP, new BigIntegerModularExponentiationPrecompiledContract(gasCalculator));
    registry.put(Address.ALTBN128_ADD, new AltBN128AddPrecompiledContract(gasCalculator));
    registry.put(Address.ALTBN128_MUL, new AltBN128MulPrecompiledContract(gasCalculator));
    registry.put(Address.ALTBN128_PAIRING, new AltBN128PairingPrecompiledContract(gasCalculator));
    return registry;
  }
}
