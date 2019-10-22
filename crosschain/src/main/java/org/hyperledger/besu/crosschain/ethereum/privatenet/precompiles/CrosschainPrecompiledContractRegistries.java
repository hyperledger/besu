package org.hyperledger.besu.crosschain.ethereum.privatenet.precompiles;

import org.hyperledger.besu.ethereum.core.Account;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.mainnet.MainnetPrecompiledContractRegistries;
import org.hyperledger.besu.ethereum.mainnet.PrecompileContractRegistry;
import org.hyperledger.besu.ethereum.mainnet.PrecompiledContractConfiguration;

public class CrosschainPrecompiledContractRegistries {
    public static PrecompileContractRegistry crosschainPrecompiles(
        final PrecompiledContractConfiguration precompiledContractConfiguration) {
      final PrecompileContractRegistry registry = MainnetPrecompiledContractRegistries.istanbul(precompiledContractConfiguration);
      registry.put(
          Address.CROSSCHAIN_SUBTRANS,
          Account.DEFAULT_VERSION,
          new CrossChainSubTransPrecompiledContract(
              precompiledContractConfiguration.getGasCalculator()));
      registry.put(
          Address.CROSSCHAIN_SUBVIEW,
          Account.DEFAULT_VERSION,
          new CrossChainSubViewPrecompiledContract(
              precompiledContractConfiguration.getGasCalculator()));
      return registry;
    }
}
