package org.hyperledger.besu.ethereum.mainnet;

import org.hyperledger.besu.config.GenesisConfigOptions;
import org.hyperledger.besu.ethereum.linea.LineaParameters;
import org.hyperledger.besu.ethereum.mainnet.feemarket.FeeMarket;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.plugin.data.TransactionType;

import java.math.BigInteger;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

public class LineaProtocolSpecs {
  private static final int LINEA_BLOCK_MAX_CALLDATA_SIZE =
      1000000; // fake value replace with the actual one when known
  private static final int LINEA_TX_MAX_CALLDATA_SIZE =
      10000; // fake value replace with the actual one when known

  private LineaProtocolSpecs() {}

  static ProtocolSpecBuilder lineaDefinition(
      final Optional<BigInteger> chainId,
      final OptionalInt configContractSizeLimit,
      final OptionalInt configStackSizeLimit,
      final boolean enableRevertReason,
      final GenesisConfigOptions genesisConfigOptions,
      final boolean quorumCompatibilityMode,
      final EvmConfiguration evmConfiguration,
      final LineaParameters lineaParameters) {

    // calldata limits overridden?
    final int txCalldataMaxSize =
        lineaParameters.maybeTransactionCalldataMaxSize().orElse(LINEA_TX_MAX_CALLDATA_SIZE);
    final int blockCalldataMaxSize =
        lineaParameters.maybeBlockCalldataMaxSize().orElse(LINEA_BLOCK_MAX_CALLDATA_SIZE);

    return MainnetProtocolSpecs.parisDefinition(
            chainId,
            configContractSizeLimit,
            configStackSizeLimit,
            enableRevertReason,
            genesisConfigOptions,
            quorumCompatibilityMode,
            evmConfiguration)
        .transactionValidatorBuilder(
            (gasCalculator, gasLimitCalculator) ->
                new LineaTransactionValidator(
                    gasCalculator,
                    gasLimitCalculator,
                    FeeMarket.zeroBaseFee(0),
                    true,
                    chainId,
                    Set.of(
                        TransactionType.FRONTIER,
                        TransactionType.ACCESS_LIST,
                        TransactionType.EIP1559),
                    quorumCompatibilityMode,
                    txCalldataMaxSize))
        .blockMaxCalldataSize(blockCalldataMaxSize)
        .name("Linea");
  }
}
