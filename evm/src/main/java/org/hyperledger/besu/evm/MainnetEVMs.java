/*
 * Copyright ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.evm;

import org.hyperledger.besu.evm.gascalculator.BerlinGasCalculator;
import org.hyperledger.besu.evm.gascalculator.ByzantiumGasCalculator;
import org.hyperledger.besu.evm.gascalculator.ConstantinopleGasCalculator;
import org.hyperledger.besu.evm.gascalculator.FrontierGasCalculator;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.gascalculator.IstanbulGasCalculator;
import org.hyperledger.besu.evm.gascalculator.LondonGasCalculator;
import org.hyperledger.besu.evm.gascalculator.PetersburgGasCalculator;
import org.hyperledger.besu.evm.gascalculator.SpuriousDragonGasCalculator;
import org.hyperledger.besu.evm.gascalculator.TangerineWhistleGasCalculator;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.evm.operation.AddModOperation;
import org.hyperledger.besu.evm.operation.AddOperation;
import org.hyperledger.besu.evm.operation.AddressOperation;
import org.hyperledger.besu.evm.operation.AndOperation;
import org.hyperledger.besu.evm.operation.BalanceOperation;
import org.hyperledger.besu.evm.operation.BaseFeeOperation;
import org.hyperledger.besu.evm.operation.BlockHashOperation;
import org.hyperledger.besu.evm.operation.ByteOperation;
import org.hyperledger.besu.evm.operation.CallCodeOperation;
import org.hyperledger.besu.evm.operation.CallDataCopyOperation;
import org.hyperledger.besu.evm.operation.CallDataLoadOperation;
import org.hyperledger.besu.evm.operation.CallDataSizeOperation;
import org.hyperledger.besu.evm.operation.CallOperation;
import org.hyperledger.besu.evm.operation.CallValueOperation;
import org.hyperledger.besu.evm.operation.CallerOperation;
import org.hyperledger.besu.evm.operation.ChainIdOperation;
import org.hyperledger.besu.evm.operation.CodeCopyOperation;
import org.hyperledger.besu.evm.operation.CodeSizeOperation;
import org.hyperledger.besu.evm.operation.CoinbaseOperation;
import org.hyperledger.besu.evm.operation.Create2Operation;
import org.hyperledger.besu.evm.operation.CreateOperation;
import org.hyperledger.besu.evm.operation.DelegateCallOperation;
import org.hyperledger.besu.evm.operation.DifficultyOperation;
import org.hyperledger.besu.evm.operation.DivOperation;
import org.hyperledger.besu.evm.operation.DupOperation;
import org.hyperledger.besu.evm.operation.EqOperation;
import org.hyperledger.besu.evm.operation.ExpOperation;
import org.hyperledger.besu.evm.operation.ExtCodeCopyOperation;
import org.hyperledger.besu.evm.operation.ExtCodeHashOperation;
import org.hyperledger.besu.evm.operation.ExtCodeSizeOperation;
import org.hyperledger.besu.evm.operation.GasLimitOperation;
import org.hyperledger.besu.evm.operation.GasOperation;
import org.hyperledger.besu.evm.operation.GasPriceOperation;
import org.hyperledger.besu.evm.operation.GtOperation;
import org.hyperledger.besu.evm.operation.InvalidOperation;
import org.hyperledger.besu.evm.operation.IsZeroOperation;
import org.hyperledger.besu.evm.operation.JumpDestOperation;
import org.hyperledger.besu.evm.operation.JumpOperation;
import org.hyperledger.besu.evm.operation.JumpiOperation;
import org.hyperledger.besu.evm.operation.Keccak256Operation;
import org.hyperledger.besu.evm.operation.LogOperation;
import org.hyperledger.besu.evm.operation.LtOperation;
import org.hyperledger.besu.evm.operation.MLoadOperation;
import org.hyperledger.besu.evm.operation.MSizeOperation;
import org.hyperledger.besu.evm.operation.MStore8Operation;
import org.hyperledger.besu.evm.operation.MStoreOperation;
import org.hyperledger.besu.evm.operation.ModOperation;
import org.hyperledger.besu.evm.operation.MulModOperation;
import org.hyperledger.besu.evm.operation.MulOperation;
import org.hyperledger.besu.evm.operation.NotOperation;
import org.hyperledger.besu.evm.operation.NumberOperation;
import org.hyperledger.besu.evm.operation.OperationRegistry;
import org.hyperledger.besu.evm.operation.OrOperation;
import org.hyperledger.besu.evm.operation.OriginOperation;
import org.hyperledger.besu.evm.operation.PCOperation;
import org.hyperledger.besu.evm.operation.PopOperation;
import org.hyperledger.besu.evm.operation.PrevRanDaoOperation;
import org.hyperledger.besu.evm.operation.PushOperation;
import org.hyperledger.besu.evm.operation.ReturnDataCopyOperation;
import org.hyperledger.besu.evm.operation.ReturnDataSizeOperation;
import org.hyperledger.besu.evm.operation.ReturnOperation;
import org.hyperledger.besu.evm.operation.RevertOperation;
import org.hyperledger.besu.evm.operation.SDivOperation;
import org.hyperledger.besu.evm.operation.SGtOperation;
import org.hyperledger.besu.evm.operation.SLoadOperation;
import org.hyperledger.besu.evm.operation.SLtOperation;
import org.hyperledger.besu.evm.operation.SModOperation;
import org.hyperledger.besu.evm.operation.SStoreOperation;
import org.hyperledger.besu.evm.operation.SarOperation;
import org.hyperledger.besu.evm.operation.SelfBalanceOperation;
import org.hyperledger.besu.evm.operation.SelfDestructOperation;
import org.hyperledger.besu.evm.operation.ShlOperation;
import org.hyperledger.besu.evm.operation.ShrOperation;
import org.hyperledger.besu.evm.operation.SignExtendOperation;
import org.hyperledger.besu.evm.operation.StaticCallOperation;
import org.hyperledger.besu.evm.operation.StopOperation;
import org.hyperledger.besu.evm.operation.SubOperation;
import org.hyperledger.besu.evm.operation.SwapOperation;
import org.hyperledger.besu.evm.operation.TimestampOperation;
import org.hyperledger.besu.evm.operation.XorOperation;

import java.math.BigInteger;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

/** Provides EVMs supporting the appropriate operations for mainnet hard forks. */
public abstract class MainnetEVMs {

  public static final BigInteger DEV_NET_CHAIN_ID = BigInteger.valueOf(1337);

  public static EVM frontier(final EvmConfiguration evmConfiguration) {
    return frontier(new FrontierGasCalculator(), evmConfiguration);
  }

  public static EVM frontier(
      final GasCalculator gasCalculator, final EvmConfiguration evmConfiguration) {
    return new EVM(frontierOperations(gasCalculator), gasCalculator, evmConfiguration);
  }

  public static OperationRegistry frontierOperations(final GasCalculator gasCalculator) {
    OperationRegistry operationRegistry = new OperationRegistry();
    registerFrontierOperations(operationRegistry, gasCalculator);
    return operationRegistry;
  }

  public static void registerFrontierOperations(
      final OperationRegistry registry, final GasCalculator gasCalculator) {
    registry.put(new AddOperation(gasCalculator));
    registry.put(new MulOperation(gasCalculator));
    registry.put(new SubOperation(gasCalculator));
    registry.put(new DivOperation(gasCalculator));
    registry.put(new SDivOperation(gasCalculator));
    registry.put(new ModOperation(gasCalculator));
    registry.put(new SModOperation(gasCalculator));
    registry.put(new ExpOperation(gasCalculator));
    registry.put(new AddModOperation(gasCalculator));
    registry.put(new MulModOperation(gasCalculator));
    registry.put(new SignExtendOperation(gasCalculator));
    registry.put(new LtOperation(gasCalculator));
    registry.put(new GtOperation(gasCalculator));
    registry.put(new SLtOperation(gasCalculator));
    registry.put(new SGtOperation(gasCalculator));
    registry.put(new EqOperation(gasCalculator));
    registry.put(new IsZeroOperation(gasCalculator));
    registry.put(new AndOperation(gasCalculator));
    registry.put(new OrOperation(gasCalculator));
    registry.put(new XorOperation(gasCalculator));
    registry.put(new NotOperation(gasCalculator));
    registry.put(new ByteOperation(gasCalculator));
    registry.put(new Keccak256Operation(gasCalculator));
    registry.put(new AddressOperation(gasCalculator));
    registry.put(new BalanceOperation(gasCalculator));
    registry.put(new OriginOperation(gasCalculator));
    registry.put(new CallerOperation(gasCalculator));
    registry.put(new CallValueOperation(gasCalculator));
    registry.put(new CallDataLoadOperation(gasCalculator));
    registry.put(new CallDataSizeOperation(gasCalculator));
    registry.put(new CallDataCopyOperation(gasCalculator));
    registry.put(new CodeSizeOperation(gasCalculator));
    registry.put(new CodeCopyOperation(gasCalculator));
    registry.put(new GasPriceOperation(gasCalculator));
    registry.put(new ExtCodeCopyOperation(gasCalculator));
    registry.put(new ExtCodeSizeOperation(gasCalculator));
    registry.put(new BlockHashOperation(gasCalculator));
    registry.put(new CoinbaseOperation(gasCalculator));
    registry.put(new TimestampOperation(gasCalculator));
    registry.put(new NumberOperation(gasCalculator));
    registry.put(new DifficultyOperation(gasCalculator));
    registry.put(new GasLimitOperation(gasCalculator));
    registry.put(new PopOperation(gasCalculator));
    registry.put(new MLoadOperation(gasCalculator));
    registry.put(new MStoreOperation(gasCalculator));
    registry.put(new MStore8Operation(gasCalculator));
    registry.put(new SLoadOperation(gasCalculator));
    registry.put(new SStoreOperation(gasCalculator, SStoreOperation.FRONTIER_MINIMUM));
    registry.put(new JumpOperation(gasCalculator));
    registry.put(new JumpiOperation(gasCalculator));
    registry.put(new PCOperation(gasCalculator));
    registry.put(new MSizeOperation(gasCalculator));
    registry.put(new GasOperation(gasCalculator));
    registry.put(new JumpDestOperation(gasCalculator));
    registry.put(new ReturnOperation(gasCalculator));
    registry.put(new InvalidOperation(gasCalculator));
    registry.put(new StopOperation(gasCalculator));
    registry.put(new SelfDestructOperation(gasCalculator));
    registry.put(new CreateOperation(gasCalculator));
    registry.put(new CallOperation(gasCalculator));
    registry.put(new CallCodeOperation(gasCalculator));

    // Register the PUSH1, PUSH2, ..., PUSH32 operations.
    for (int i = 1; i <= 32; ++i) {
      registry.put(new PushOperation(i, gasCalculator));
    }

    // Register the DUP1, DUP2, ..., DUP16 operations.
    for (int i = 1; i <= 16; ++i) {
      registry.put(new DupOperation(i, gasCalculator));
    }

    // Register the SWAP1, SWAP2, ..., SWAP16 operations.
    for (int i = 1; i <= 16; ++i) {
      registry.put(new SwapOperation(i, gasCalculator));
    }

    // Register the LOG0, LOG1, ..., LOG4 operations.
    for (int i = 0; i < 5; ++i) {
      registry.put(new LogOperation(i, gasCalculator));
    }
  }

  public static EVM homestead(final EvmConfiguration evmConfiguration) {
    return homestead(new FrontierGasCalculator(), evmConfiguration);
  }

  public static EVM homestead(
      final GasCalculator gasCalculator, final EvmConfiguration evmConfiguration) {
    return new EVM(homesteadOperations(gasCalculator), gasCalculator, evmConfiguration);
  }

  public static OperationRegistry homesteadOperations(final GasCalculator gasCalculator) {
    OperationRegistry operationRegistry = new OperationRegistry();
    registerHomesteadOperations(operationRegistry, gasCalculator);
    return operationRegistry;
  }

  public static void registerHomesteadOperations(
      final OperationRegistry registry, final GasCalculator gasCalculator) {
    registerFrontierOperations(registry, gasCalculator);
    registry.put(new DelegateCallOperation(gasCalculator));
  }

  public static EVM spuriousDragon(final EvmConfiguration evmConfiguration) {
    return homestead(new SpuriousDragonGasCalculator(), evmConfiguration);
  }

  public static EVM tangerineWhistle(final EvmConfiguration evmConfiguration) {
    return homestead(new TangerineWhistleGasCalculator(), evmConfiguration);
  }

  public static EVM byzantium(final EvmConfiguration evmConfiguration) {
    return byzantium(new ByzantiumGasCalculator(), evmConfiguration);
  }

  public static EVM byzantium(
      final GasCalculator gasCalculator, final EvmConfiguration evmConfiguration) {
    return new EVM(byzantiumOperations(gasCalculator), gasCalculator, evmConfiguration);
  }

  public static OperationRegistry byzantiumOperations(final GasCalculator gasCalculator) {
    OperationRegistry operationRegistry = new OperationRegistry();
    registerByzantiumOperations(operationRegistry, gasCalculator);
    return operationRegistry;
  }

  public static void registerByzantiumOperations(
      final OperationRegistry registry, final GasCalculator gasCalculator) {
    registerHomesteadOperations(registry, gasCalculator);
    registry.put(new ReturnDataCopyOperation(gasCalculator));
    registry.put(new ReturnDataSizeOperation(gasCalculator));
    registry.put(new RevertOperation(gasCalculator));
    registry.put(new StaticCallOperation(gasCalculator));
  }

  public static EVM constantinople(final EvmConfiguration evmConfiguration) {
    return constantinople(new ConstantinopleGasCalculator(), evmConfiguration);
  }

  public static EVM constantinople(
      final GasCalculator gasCalculator, final EvmConfiguration evmConfiguration) {
    return new EVM(constantinopleOperations(gasCalculator), gasCalculator, evmConfiguration);
  }

  public static OperationRegistry constantinopleOperations(final GasCalculator gasCalculator) {
    OperationRegistry operationRegistry = new OperationRegistry();
    registerConstantinopleOperations(operationRegistry, gasCalculator);
    return operationRegistry;
  }

  public static void registerConstantinopleOperations(
      final OperationRegistry registry, final GasCalculator gasCalculator) {
    registerByzantiumOperations(registry, gasCalculator);
    registry.put(new Create2Operation(gasCalculator));
    registry.put(new SarOperation(gasCalculator));
    registry.put(new ShlOperation(gasCalculator));
    registry.put(new ShrOperation(gasCalculator));
    registry.put(new ExtCodeHashOperation(gasCalculator));
  }

  public static EVM petersburg(final EvmConfiguration evmConfiguration) {
    return constantinople(new PetersburgGasCalculator(), evmConfiguration);
  }

  public static EVM istanbul(final EvmConfiguration evmConfiguration) {
    return istanbul(DEV_NET_CHAIN_ID, evmConfiguration);
  }

  public static EVM istanbul(final BigInteger chainId, final EvmConfiguration evmConfiguration) {
    return istanbul(new IstanbulGasCalculator(), chainId, evmConfiguration);
  }

  public static EVM istanbul(
      final GasCalculator gasCalculator,
      final BigInteger chainId,
      final EvmConfiguration evmConfiguration) {
    return new EVM(istanbulOperations(gasCalculator, chainId), gasCalculator, evmConfiguration);
  }

  public static OperationRegistry istanbulOperations(
      final GasCalculator gasCalculator, final BigInteger chainId) {
    OperationRegistry operationRegistry = new OperationRegistry();
    registerIstanbulOperations(operationRegistry, gasCalculator, chainId);
    return operationRegistry;
  }

  public static void registerIstanbulOperations(
      final OperationRegistry registry,
      final GasCalculator gasCalculator,
      final BigInteger chainId) {
    registerConstantinopleOperations(registry, gasCalculator);
    registry.put(
        new ChainIdOperation(gasCalculator, Bytes32.leftPad(Bytes.of(chainId.toByteArray()))));
    registry.put(new SelfBalanceOperation(gasCalculator));
    registry.put(new SStoreOperation(gasCalculator, SStoreOperation.EIP_1706_MINIMUM));
  }

  public static EVM berlin(final EvmConfiguration evmConfiguration) {
    return berlin(DEV_NET_CHAIN_ID, evmConfiguration);
  }

  public static EVM berlin(final BigInteger chainId, final EvmConfiguration evmConfiguration) {
    return istanbul(new BerlinGasCalculator(), chainId, evmConfiguration);
  }

  public static EVM london(final EvmConfiguration evmConfiguration) {
    return london(DEV_NET_CHAIN_ID, evmConfiguration);
  }

  public static EVM london(final BigInteger chainId, final EvmConfiguration evmConfiguration) {
    return london(new LondonGasCalculator(), chainId, evmConfiguration);
  }

  public static EVM london(
      final GasCalculator gasCalculator,
      final BigInteger chainId,
      final EvmConfiguration evmConfiguration) {
    return new EVM(londonOperations(gasCalculator, chainId), gasCalculator, evmConfiguration);
  }

  public static OperationRegistry londonOperations(
      final GasCalculator gasCalculator, final BigInteger chainId) {
    OperationRegistry operationRegistry = new OperationRegistry();
    registerLondonOperations(operationRegistry, gasCalculator, chainId);
    return operationRegistry;
  }

  public static void registerLondonOperations(
      final OperationRegistry registry,
      final GasCalculator gasCalculator,
      final BigInteger chainId) {
    registerIstanbulOperations(registry, gasCalculator, chainId);
    registry.put(new BaseFeeOperation(gasCalculator));
  }

  public static EVM paris(final BigInteger chainId, final EvmConfiguration evmConfiguration) {
    return paris(new LondonGasCalculator(), chainId, evmConfiguration);
  }

  public static EVM paris(
      final GasCalculator gasCalculator,
      final BigInteger chainId,
      final EvmConfiguration evmConfiguration) {
    return new EVM(parisOperations(gasCalculator, chainId), gasCalculator, evmConfiguration);
  }

  public static OperationRegistry parisOperations(
      final GasCalculator gasCalculator, final BigInteger chainId) {
    OperationRegistry operationRegistry = new OperationRegistry();
    registerParisOperations(operationRegistry, gasCalculator, chainId);
    return operationRegistry;
  }

  public static void registerParisOperations(
      final OperationRegistry registry,
      final GasCalculator gasCalculator,
      final BigInteger chainID) {
    registerLondonOperations(registry, gasCalculator, chainID);
    registry.put(new PrevRanDaoOperation(gasCalculator));
  }
}
