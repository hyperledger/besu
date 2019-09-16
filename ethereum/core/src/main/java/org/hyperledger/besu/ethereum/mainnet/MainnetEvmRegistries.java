/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.hyperledger.besu.ethereum.mainnet;

import org.hyperledger.besu.ethereum.core.Account;
import org.hyperledger.besu.ethereum.vm.EVM;
import org.hyperledger.besu.ethereum.vm.GasCalculator;
import org.hyperledger.besu.ethereum.vm.OperationRegistry;
import org.hyperledger.besu.ethereum.vm.operations.AddModOperation;
import org.hyperledger.besu.ethereum.vm.operations.AddOperation;
import org.hyperledger.besu.ethereum.vm.operations.AddressOperation;
import org.hyperledger.besu.ethereum.vm.operations.AndOperation;
import org.hyperledger.besu.ethereum.vm.operations.BalanceOperation;
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
import org.hyperledger.besu.util.bytes.Bytes32;
import org.hyperledger.besu.util.bytes.BytesValue;

import java.math.BigInteger;

/** Provides EVMs supporting the appropriate operations for mainnet hard forks. */
abstract class MainnetEvmRegistries {

  static EVM frontier(final GasCalculator gasCalculator) {
    final OperationRegistry registry = new OperationRegistry();

    registerFrontierOpcodes(registry, gasCalculator, Account.DEFAULT_VERSION);

    return new EVM(registry, new InvalidOperation(gasCalculator));
  }

  static EVM homestead(final GasCalculator gasCalculator) {
    final OperationRegistry registry = new OperationRegistry();

    registerHomesteadOpcodes(registry, gasCalculator, Account.DEFAULT_VERSION);

    return new EVM(registry, new InvalidOperation(gasCalculator));
  }

  static EVM byzantium(final GasCalculator gasCalculator) {
    final OperationRegistry registry = new OperationRegistry();

    registerByzantiumOpcodes(registry, gasCalculator, Account.DEFAULT_VERSION);

    return new EVM(registry, new InvalidOperation(gasCalculator));
  }

  static EVM constantinople(final GasCalculator gasCalculator) {
    final OperationRegistry registry = new OperationRegistry();

    registerConstantinopleOpcodes(registry, gasCalculator, Account.DEFAULT_VERSION);

    return new EVM(registry, new InvalidOperation(gasCalculator));
  }

  static EVM istanbul(final GasCalculator gasCalculator, final BigInteger chainId) {
    final OperationRegistry registry = new OperationRegistry();

    registerIstanbulOpcodes(registry, gasCalculator, Account.DEFAULT_VERSION, chainId);

    return new EVM(registry, new InvalidOperation(gasCalculator));
  }

  private static void registerFrontierOpcodes(
      final OperationRegistry registry,
      final GasCalculator gasCalculator,
      final int accountVersion) {
    registry.put(new AddOperation(gasCalculator), accountVersion);
    registry.put(new AddOperation(gasCalculator), accountVersion);
    registry.put(new MulOperation(gasCalculator), accountVersion);
    registry.put(new SubOperation(gasCalculator), accountVersion);
    registry.put(new DivOperation(gasCalculator), accountVersion);
    registry.put(new SDivOperation(gasCalculator), accountVersion);
    registry.put(new ModOperation(gasCalculator), accountVersion);
    registry.put(new SModOperation(gasCalculator), accountVersion);
    registry.put(new ExpOperation(gasCalculator), accountVersion);
    registry.put(new AddModOperation(gasCalculator), accountVersion);
    registry.put(new MulModOperation(gasCalculator), accountVersion);
    registry.put(new SignExtendOperation(gasCalculator), accountVersion);
    registry.put(new LtOperation(gasCalculator), accountVersion);
    registry.put(new GtOperation(gasCalculator), accountVersion);
    registry.put(new SLtOperation(gasCalculator), accountVersion);
    registry.put(new SGtOperation(gasCalculator), accountVersion);
    registry.put(new EqOperation(gasCalculator), accountVersion);
    registry.put(new IsZeroOperation(gasCalculator), accountVersion);
    registry.put(new AndOperation(gasCalculator), accountVersion);
    registry.put(new OrOperation(gasCalculator), accountVersion);
    registry.put(new XorOperation(gasCalculator), accountVersion);
    registry.put(new NotOperation(gasCalculator), accountVersion);
    registry.put(new ByteOperation(gasCalculator), accountVersion);
    registry.put(new Sha3Operation(gasCalculator), accountVersion);
    registry.put(new AddressOperation(gasCalculator), accountVersion);
    registry.put(new BalanceOperation(gasCalculator), accountVersion);
    registry.put(new OriginOperation(gasCalculator), accountVersion);
    registry.put(new CallerOperation(gasCalculator), accountVersion);
    registry.put(new CallValueOperation(gasCalculator), accountVersion);
    registry.put(new CallDataLoadOperation(gasCalculator), accountVersion);
    registry.put(new CallDataSizeOperation(gasCalculator), accountVersion);
    registry.put(new CallDataCopyOperation(gasCalculator), accountVersion);
    registry.put(new CodeSizeOperation(gasCalculator), accountVersion);
    registry.put(new CodeCopyOperation(gasCalculator), accountVersion);
    registry.put(new GasPriceOperation(gasCalculator), accountVersion);
    registry.put(new ExtCodeCopyOperation(gasCalculator), accountVersion);
    registry.put(new ExtCodeSizeOperation(gasCalculator), accountVersion);
    registry.put(new BlockHashOperation(gasCalculator), accountVersion);
    registry.put(new CoinbaseOperation(gasCalculator), accountVersion);
    registry.put(new TimestampOperation(gasCalculator), accountVersion);
    registry.put(new NumberOperation(gasCalculator), accountVersion);
    registry.put(new DifficultyOperation(gasCalculator), accountVersion);
    registry.put(new GasLimitOperation(gasCalculator), accountVersion);
    registry.put(new PopOperation(gasCalculator), accountVersion);
    registry.put(new MLoadOperation(gasCalculator), accountVersion);
    registry.put(new MStoreOperation(gasCalculator), accountVersion);
    registry.put(new MStore8Operation(gasCalculator), accountVersion);
    registry.put(new SLoadOperation(gasCalculator), accountVersion);
    registry.put(
        new SStoreOperation(gasCalculator, SStoreOperation.FRONTIER_MINIMUM), accountVersion);
    registry.put(new JumpOperation(gasCalculator), accountVersion);
    registry.put(new JumpiOperation(gasCalculator), accountVersion);
    registry.put(new PCOperation(gasCalculator), accountVersion);
    registry.put(new MSizeOperation(gasCalculator), accountVersion);
    registry.put(new GasOperation(gasCalculator), accountVersion);
    registry.put(new JumpDestOperation(gasCalculator), accountVersion);
    registry.put(new ReturnOperation(gasCalculator), accountVersion);
    registry.put(new InvalidOperation(gasCalculator), accountVersion);
    registry.put(new StopOperation(gasCalculator), accountVersion);
    registry.put(new SelfDestructOperation(gasCalculator), accountVersion);
    registry.put(new CreateOperation(gasCalculator), accountVersion);
    registry.put(new CallOperation(gasCalculator), accountVersion);
    registry.put(new CallCodeOperation(gasCalculator), accountVersion);

    // Register the PUSH1, PUSH2, ..., PUSH32 operations.
    for (int i = 1; i <= 32; ++i) {
      registry.put(new PushOperation(i, gasCalculator), accountVersion);
    }

    // Register the DUP1, DUP2, ..., DUP16 operations.
    for (int i = 1; i <= 16; ++i) {
      registry.put(new DupOperation(i, gasCalculator), accountVersion);
    }

    // Register the SWAP1, SWAP2, ..., SWAP16 operations.
    for (int i = 1; i <= 16; ++i) {
      registry.put(new SwapOperation(i, gasCalculator), accountVersion);
    }

    // Register the LOG0, LOG1, ..., LOG4 operations.
    for (int i = 0; i < 5; ++i) {
      registry.put(new LogOperation(i, gasCalculator), accountVersion);
    }
  }

  private static void registerHomesteadOpcodes(
      final OperationRegistry registry,
      final GasCalculator gasCalculator,
      final int accountVersion) {
    registerFrontierOpcodes(registry, gasCalculator, accountVersion);
    registry.put(new DelegateCallOperation(gasCalculator), accountVersion);
  }

  private static void registerByzantiumOpcodes(
      final OperationRegistry registry,
      final GasCalculator gasCalculator,
      final int accountVersion) {
    registerHomesteadOpcodes(registry, gasCalculator, accountVersion);
    registry.put(new ReturnDataCopyOperation(gasCalculator), accountVersion);
    registry.put(new ReturnDataSizeOperation(gasCalculator), accountVersion);
    registry.put(new RevertOperation(gasCalculator), accountVersion);
    registry.put(new StaticCallOperation(gasCalculator), accountVersion);
  }

  private static void registerConstantinopleOpcodes(
      final OperationRegistry registry,
      final GasCalculator gasCalculator,
      final int accountVersion) {
    registerByzantiumOpcodes(registry, gasCalculator, accountVersion);
    registry.put(new Create2Operation(gasCalculator), accountVersion);
    registry.put(new SarOperation(gasCalculator), accountVersion);
    registry.put(new ShlOperation(gasCalculator), accountVersion);
    registry.put(new ShrOperation(gasCalculator), accountVersion);
    registry.put(new ExtCodeHashOperation(gasCalculator), accountVersion);
  }

  private static void registerIstanbulOpcodes(
      final OperationRegistry registry,
      final GasCalculator gasCalculator,
      final int accountVersion,
      final BigInteger chainId) {
    registerConstantinopleOpcodes(registry, gasCalculator, accountVersion);
    registry.put(
        new ChainIdOperation(gasCalculator, Bytes32.leftPad(BytesValue.of(chainId.toByteArray()))),
        Account.DEFAULT_VERSION);
    registry.put(new SelfBalanceOperation(gasCalculator), Account.DEFAULT_VERSION);
    registry.put(
        new SStoreOperation(gasCalculator, SStoreOperation.EIP_1706_MINIMUM),
        Account.DEFAULT_VERSION);
  }
}
