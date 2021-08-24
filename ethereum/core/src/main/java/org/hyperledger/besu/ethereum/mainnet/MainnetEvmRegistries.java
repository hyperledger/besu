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
package org.hyperledger.besu.ethereum.mainnet;

import org.hyperledger.besu.ethereum.vm.EVM;
import org.hyperledger.besu.ethereum.vm.GasCalculator;
import org.hyperledger.besu.ethereum.vm.OperationRegistry;
import org.hyperledger.besu.ethereum.vm.operations.AddModOperation;
import org.hyperledger.besu.ethereum.vm.operations.AddOperation;
import org.hyperledger.besu.ethereum.vm.operations.AddressOperation;
import org.hyperledger.besu.ethereum.vm.operations.AndOperation;
import org.hyperledger.besu.ethereum.vm.operations.BalanceOperation;
import org.hyperledger.besu.ethereum.vm.operations.BaseFeeOperation;
import org.hyperledger.besu.ethereum.vm.operations.BlockHashOperation;
import org.hyperledger.besu.ethereum.vm.operations.ByteOperation;
import org.hyperledger.besu.ethereum.vm.operations.CallCodeOperation;
import org.hyperledger.besu.ethereum.vm.operations.CallDataCopyOperation;
import org.hyperledger.besu.ethereum.vm.operations.CallDataLoadOperation;
import org.hyperledger.besu.ethereum.vm.operations.CallDataSizeOperation;
import org.hyperledger.besu.ethereum.vm.operations.CallOperation;
import org.hyperledger.besu.ethereum.vm.operations.CallValueOperation;
import org.hyperledger.besu.ethereum.vm.operations.CallerOperation;
import org.hyperledger.besu.ethereum.vm.operations.ChainIdOperation;
import org.hyperledger.besu.ethereum.vm.operations.CodeCopyOperation;
import org.hyperledger.besu.ethereum.vm.operations.CodeSizeOperation;
import org.hyperledger.besu.ethereum.vm.operations.CoinbaseOperation;
import org.hyperledger.besu.ethereum.vm.operations.Create2Operation;
import org.hyperledger.besu.ethereum.vm.operations.CreateOperation;
import org.hyperledger.besu.ethereum.vm.operations.DelegateCallOperation;
import org.hyperledger.besu.ethereum.vm.operations.DifficultyOperation;
import org.hyperledger.besu.ethereum.vm.operations.DivOperation;
import org.hyperledger.besu.ethereum.vm.operations.DupOperation;
import org.hyperledger.besu.ethereum.vm.operations.EqOperation;
import org.hyperledger.besu.ethereum.vm.operations.ExpOperation;
import org.hyperledger.besu.ethereum.vm.operations.ExtCodeCopyOperation;
import org.hyperledger.besu.ethereum.vm.operations.ExtCodeHashOperation;
import org.hyperledger.besu.ethereum.vm.operations.ExtCodeSizeOperation;
import org.hyperledger.besu.ethereum.vm.operations.GasLimitOperation;
import org.hyperledger.besu.ethereum.vm.operations.GasOperation;
import org.hyperledger.besu.ethereum.vm.operations.GasPriceOperation;
import org.hyperledger.besu.ethereum.vm.operations.GtOperation;
import org.hyperledger.besu.ethereum.vm.operations.InvalidOperation;
import org.hyperledger.besu.ethereum.vm.operations.IsZeroOperation;
import org.hyperledger.besu.ethereum.vm.operations.JumpDestOperation;
import org.hyperledger.besu.ethereum.vm.operations.JumpOperation;
import org.hyperledger.besu.ethereum.vm.operations.JumpiOperation;
import org.hyperledger.besu.ethereum.vm.operations.LogOperation;
import org.hyperledger.besu.ethereum.vm.operations.LtOperation;
import org.hyperledger.besu.ethereum.vm.operations.MLoadOperation;
import org.hyperledger.besu.ethereum.vm.operations.MSizeOperation;
import org.hyperledger.besu.ethereum.vm.operations.MStore8Operation;
import org.hyperledger.besu.ethereum.vm.operations.MStoreOperation;
import org.hyperledger.besu.ethereum.vm.operations.ModOperation;
import org.hyperledger.besu.ethereum.vm.operations.MulModOperation;
import org.hyperledger.besu.ethereum.vm.operations.MulOperation;
import org.hyperledger.besu.ethereum.vm.operations.NotOperation;
import org.hyperledger.besu.ethereum.vm.operations.NumberOperation;
import org.hyperledger.besu.ethereum.vm.operations.OrOperation;
import org.hyperledger.besu.ethereum.vm.operations.OriginOperation;
import org.hyperledger.besu.ethereum.vm.operations.PCOperation;
import org.hyperledger.besu.ethereum.vm.operations.PopOperation;
import org.hyperledger.besu.ethereum.vm.operations.PushOperation;
import org.hyperledger.besu.ethereum.vm.operations.ReturnDataCopyOperation;
import org.hyperledger.besu.ethereum.vm.operations.ReturnDataSizeOperation;
import org.hyperledger.besu.ethereum.vm.operations.ReturnOperation;
import org.hyperledger.besu.ethereum.vm.operations.RevertOperation;
import org.hyperledger.besu.ethereum.vm.operations.SDivOperation;
import org.hyperledger.besu.ethereum.vm.operations.SGtOperation;
import org.hyperledger.besu.ethereum.vm.operations.SLoadOperation;
import org.hyperledger.besu.ethereum.vm.operations.SLtOperation;
import org.hyperledger.besu.ethereum.vm.operations.SModOperation;
import org.hyperledger.besu.ethereum.vm.operations.SStoreOperation;
import org.hyperledger.besu.ethereum.vm.operations.SarOperation;
import org.hyperledger.besu.ethereum.vm.operations.SelfBalanceOperation;
import org.hyperledger.besu.ethereum.vm.operations.SelfDestructOperation;
import org.hyperledger.besu.ethereum.vm.operations.Sha3Operation;
import org.hyperledger.besu.ethereum.vm.operations.ShlOperation;
import org.hyperledger.besu.ethereum.vm.operations.ShrOperation;
import org.hyperledger.besu.ethereum.vm.operations.SignExtendOperation;
import org.hyperledger.besu.ethereum.vm.operations.StaticCallOperation;
import org.hyperledger.besu.ethereum.vm.operations.StopOperation;
import org.hyperledger.besu.ethereum.vm.operations.SubOperation;
import org.hyperledger.besu.ethereum.vm.operations.SwapOperation;
import org.hyperledger.besu.ethereum.vm.operations.TimestampOperation;
import org.hyperledger.besu.ethereum.vm.operations.XorOperation;

import java.math.BigInteger;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

/** Provides EVMs supporting the appropriate operations for mainnet hard forks. */
abstract class MainnetEvmRegistries {

  static EVM frontier(final GasCalculator gasCalculator) {
    final OperationRegistry registry = new OperationRegistry();

    registerFrontierOpcodes(registry, gasCalculator);

    return new EVM(registry, gasCalculator);
  }

  static EVM homestead(final GasCalculator gasCalculator) {
    final OperationRegistry registry = new OperationRegistry();

    registerHomesteadOpcodes(registry, gasCalculator);

    return new EVM(registry, gasCalculator);
  }

  static EVM byzantium(final GasCalculator gasCalculator) {
    final OperationRegistry registry = new OperationRegistry();

    registerByzantiumOpcodes(registry, gasCalculator);

    return new EVM(registry, gasCalculator);
  }

  static EVM constantinople(final GasCalculator gasCalculator) {
    final OperationRegistry registry = new OperationRegistry();

    registerConstantinopleOpcodes(registry, gasCalculator);

    return new EVM(registry, gasCalculator);
  }

  static EVM istanbul(final GasCalculator gasCalculator, final BigInteger chainId) {
    final OperationRegistry registry = new OperationRegistry();

    registerIstanbulOpcodes(registry, gasCalculator, chainId);

    return new EVM(registry, gasCalculator);
  }

  static EVM london(final GasCalculator gasCalculator, final BigInteger chainId) {
    final OperationRegistry registry = new OperationRegistry();

    registerLondonOpcodes(registry, gasCalculator, chainId);

    return new EVM(registry, gasCalculator);
  }

  private static void registerFrontierOpcodes(
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
    registry.put(new Sha3Operation(gasCalculator));
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

  private static void registerHomesteadOpcodes(
      final OperationRegistry registry, final GasCalculator gasCalculator) {
    registerFrontierOpcodes(registry, gasCalculator);
    registry.put(new DelegateCallOperation(gasCalculator));
  }

  private static void registerByzantiumOpcodes(
      final OperationRegistry registry, final GasCalculator gasCalculator) {
    registerHomesteadOpcodes(registry, gasCalculator);
    registry.put(new ReturnDataCopyOperation(gasCalculator));
    registry.put(new ReturnDataSizeOperation(gasCalculator));
    registry.put(new RevertOperation(gasCalculator));
    registry.put(new StaticCallOperation(gasCalculator));
  }

  private static void registerConstantinopleOpcodes(
      final OperationRegistry registry, final GasCalculator gasCalculator) {
    registerByzantiumOpcodes(registry, gasCalculator);
    registry.put(new Create2Operation(gasCalculator));
    registry.put(new SarOperation(gasCalculator));
    registry.put(new ShlOperation(gasCalculator));
    registry.put(new ShrOperation(gasCalculator));
    registry.put(new ExtCodeHashOperation(gasCalculator));
  }

  private static void registerIstanbulOpcodes(
      final OperationRegistry registry,
      final GasCalculator gasCalculator,
      final BigInteger chainId) {
    registerConstantinopleOpcodes(registry, gasCalculator);
    registry.put(
        new ChainIdOperation(gasCalculator, Bytes32.leftPad(Bytes.of(chainId.toByteArray()))));
    registry.put(new SelfBalanceOperation(gasCalculator));
    registry.put(new SStoreOperation(gasCalculator, SStoreOperation.EIP_1706_MINIMUM));
  }

  private static void registerLondonOpcodes(
      final OperationRegistry registry,
      final GasCalculator gasCalculator,
      final BigInteger chainId) {
    registerIstanbulOpcodes(registry, gasCalculator, chainId);
    registry.put(new BaseFeeOperation(gasCalculator));
  }
}
