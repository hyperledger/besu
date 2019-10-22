package org.hyperledger.besu.ethereum.privatenet;

import org.hyperledger.besu.ethereum.mainnet.MainnetProtocolSpecs;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpecBuilder;
import org.hyperledger.besu.ethereum.privatenet.precompiles.crosschain.CrosschainPrecompiledContractRegistries;

import java.math.BigInteger;
import java.util.Optional;
import java.util.OptionalInt;

public class CrosschainProtocolSpecs {
    public static ProtocolSpecBuilder<Void> crossChainDefinition(
        final Optional<BigInteger> chainId,
        final OptionalInt contractSizeLimit,
        final OptionalInt configStackSizeLimit,
        final boolean enableRevertReason) {
      return MainnetProtocolSpecs.istanbulDefinition(chainId, contractSizeLimit, configStackSizeLimit, enableRevertReason)
          .precompileContractRegistryBuilder(
                  CrosschainPrecompiledContractRegistries::crosschainPrecompiles)
          .name("CrossChain");
    }
}
