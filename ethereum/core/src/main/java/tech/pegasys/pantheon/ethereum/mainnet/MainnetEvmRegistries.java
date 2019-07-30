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
package tech.pegasys.pantheon.ethereum.mainnet;

import tech.pegasys.pantheon.ethereum.core.Account;
import tech.pegasys.pantheon.ethereum.vm.EVM;
import tech.pegasys.pantheon.ethereum.vm.GasCalculator;
import tech.pegasys.pantheon.ethereum.vm.OperationRegistry;
import tech.pegasys.pantheon.ethereum.vm.operations.AddModOperation;
import tech.pegasys.pantheon.ethereum.vm.operations.AddOperation;
import tech.pegasys.pantheon.ethereum.vm.operations.AddressOperation;
import tech.pegasys.pantheon.ethereum.vm.operations.AndOperation;
import tech.pegasys.pantheon.ethereum.vm.operations.BalanceOperation;
import tech.pegasys.pantheon.ethereum.vm.operations.BlockHashOperation;
import tech.pegasys.pantheon.ethereum.vm.operations.ByteOperation;
import tech.pegasys.pantheon.ethereum.vm.operations.CallCodeOperation;
import tech.pegasys.pantheon.ethereum.vm.operations.CallDataCopyOperation;
import tech.pegasys.pantheon.ethereum.vm.operations.CallDataLoadOperation;
import tech.pegasys.pantheon.ethereum.vm.operations.CallDataSizeOperation;
import tech.pegasys.pantheon.ethereum.vm.operations.CallOperation;
import tech.pegasys.pantheon.ethereum.vm.operations.CallValueOperation;
import tech.pegasys.pantheon.ethereum.vm.operations.CallerOperation;
import tech.pegasys.pantheon.ethereum.vm.operations.ChainIdOperation;
import tech.pegasys.pantheon.ethereum.vm.operations.CodeCopyOperation;
import tech.pegasys.pantheon.ethereum.vm.operations.CodeSizeOperation;
import tech.pegasys.pantheon.ethereum.vm.operations.CoinbaseOperation;
import tech.pegasys.pantheon.ethereum.vm.operations.Create2Operation;
import tech.pegasys.pantheon.ethereum.vm.operations.CreateOperation;
import tech.pegasys.pantheon.ethereum.vm.operations.DelegateCallOperation;
import tech.pegasys.pantheon.ethereum.vm.operations.DifficultyOperation;
import tech.pegasys.pantheon.ethereum.vm.operations.DivOperation;
import tech.pegasys.pantheon.ethereum.vm.operations.DupOperation;
import tech.pegasys.pantheon.ethereum.vm.operations.EqOperation;
import tech.pegasys.pantheon.ethereum.vm.operations.ExpOperation;
import tech.pegasys.pantheon.ethereum.vm.operations.ExtCodeCopyOperation;
import tech.pegasys.pantheon.ethereum.vm.operations.ExtCodeHashOperation;
import tech.pegasys.pantheon.ethereum.vm.operations.ExtCodeSizeOperation;
import tech.pegasys.pantheon.ethereum.vm.operations.GasLimitOperation;
import tech.pegasys.pantheon.ethereum.vm.operations.GasOperation;
import tech.pegasys.pantheon.ethereum.vm.operations.GasPriceOperation;
import tech.pegasys.pantheon.ethereum.vm.operations.GtOperation;
import tech.pegasys.pantheon.ethereum.vm.operations.InvalidOperation;
import tech.pegasys.pantheon.ethereum.vm.operations.IsZeroOperation;
import tech.pegasys.pantheon.ethereum.vm.operations.JumpDestOperation;
import tech.pegasys.pantheon.ethereum.vm.operations.JumpOperation;
import tech.pegasys.pantheon.ethereum.vm.operations.JumpiOperation;
import tech.pegasys.pantheon.ethereum.vm.operations.LogOperation;
import tech.pegasys.pantheon.ethereum.vm.operations.LtOperation;
import tech.pegasys.pantheon.ethereum.vm.operations.MLoadOperation;
import tech.pegasys.pantheon.ethereum.vm.operations.MSizeOperation;
import tech.pegasys.pantheon.ethereum.vm.operations.MStore8Operation;
import tech.pegasys.pantheon.ethereum.vm.operations.MStoreOperation;
import tech.pegasys.pantheon.ethereum.vm.operations.ModOperation;
import tech.pegasys.pantheon.ethereum.vm.operations.MulModOperation;
import tech.pegasys.pantheon.ethereum.vm.operations.MulOperation;
import tech.pegasys.pantheon.ethereum.vm.operations.NotOperation;
import tech.pegasys.pantheon.ethereum.vm.operations.NumberOperation;
import tech.pegasys.pantheon.ethereum.vm.operations.OrOperation;
import tech.pegasys.pantheon.ethereum.vm.operations.OriginOperation;
import tech.pegasys.pantheon.ethereum.vm.operations.PCOperation;
import tech.pegasys.pantheon.ethereum.vm.operations.PopOperation;
import tech.pegasys.pantheon.ethereum.vm.operations.PushOperation;
import tech.pegasys.pantheon.ethereum.vm.operations.ReturnDataCopyOperation;
import tech.pegasys.pantheon.ethereum.vm.operations.ReturnDataSizeOperation;
import tech.pegasys.pantheon.ethereum.vm.operations.ReturnOperation;
import tech.pegasys.pantheon.ethereum.vm.operations.RevertOperation;
import tech.pegasys.pantheon.ethereum.vm.operations.SDivOperation;
import tech.pegasys.pantheon.ethereum.vm.operations.SGtOperation;
import tech.pegasys.pantheon.ethereum.vm.operations.SLoadOperation;
import tech.pegasys.pantheon.ethereum.vm.operations.SLtOperation;
import tech.pegasys.pantheon.ethereum.vm.operations.SModOperation;
import tech.pegasys.pantheon.ethereum.vm.operations.SStoreOperation;
import tech.pegasys.pantheon.ethereum.vm.operations.SarOperation;
import tech.pegasys.pantheon.ethereum.vm.operations.SelfDestructOperation;
import tech.pegasys.pantheon.ethereum.vm.operations.Sha3Operation;
import tech.pegasys.pantheon.ethereum.vm.operations.ShlOperation;
import tech.pegasys.pantheon.ethereum.vm.operations.ShrOperation;
import tech.pegasys.pantheon.ethereum.vm.operations.SignExtendOperation;
import tech.pegasys.pantheon.ethereum.vm.operations.StaticCallOperation;
import tech.pegasys.pantheon.ethereum.vm.operations.StopOperation;
import tech.pegasys.pantheon.ethereum.vm.operations.SubOperation;
import tech.pegasys.pantheon.ethereum.vm.operations.SwapOperation;
import tech.pegasys.pantheon.ethereum.vm.operations.TimestampOperation;
import tech.pegasys.pantheon.ethereum.vm.operations.XorOperation;
import tech.pegasys.pantheon.util.bytes.Bytes32;
import tech.pegasys.pantheon.util.bytes.BytesValue;

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
        new SStoreOperation(gasCalculator, SStoreOperation.EIP_1706_MINIMUM),
        Account.DEFAULT_VERSION);
    registry.put(
        new ChainIdOperation(gasCalculator, Bytes32.leftPad(BytesValue.of(chainId.toByteArray()))),
        Account.DEFAULT_VERSION);
    registry.put(
        new SStoreOperation(gasCalculator, SStoreOperation.EIP_1706_MINIMUM),
        Account.DEFAULT_VERSION);
  }
}
