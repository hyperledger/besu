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

import tech.pegasys.pantheon.ethereum.vm.EVM;
import tech.pegasys.pantheon.ethereum.vm.GasCalculator;
import tech.pegasys.pantheon.ethereum.vm.Operation;
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

import java.util.List;
import java.util.function.Function;

import com.google.common.collect.ImmutableList;

/** Provides EVMs supporting the appropriate operations for mainnet hard forks. */
public abstract class MainnetEvmRegistries {

  private interface OperationFactory extends Function<GasCalculator, Operation> {}

  private static final List<OperationFactory> FRONTIER_OPERATION_FACTORIES;
  private static final List<OperationFactory> HOMESTEAD_OPERATION_FACTORIES;
  private static final List<OperationFactory> BYZANTIUM_OPERATION_FACTORIES;
  private static final List<OperationFactory> CONSTANTINOPLE_OPERATION_FACTORIES;

  static {
    FRONTIER_OPERATION_FACTORIES = buildFrontierFactories();
    HOMESTEAD_OPERATION_FACTORIES = buildHomesteadFactories(FRONTIER_OPERATION_FACTORIES);
    BYZANTIUM_OPERATION_FACTORIES = buildByzantiumFactories(HOMESTEAD_OPERATION_FACTORIES);
    CONSTANTINOPLE_OPERATION_FACTORIES =
        buildConstantinopleFactories(BYZANTIUM_OPERATION_FACTORIES);
  }

  private static EVM createAndPopulate(
      final List<OperationFactory> factories, final GasCalculator gasCalculator) {
    final OperationRegistry registry = new OperationRegistry();

    for (final OperationFactory factory : factories) {
      final Operation operation = factory.apply(gasCalculator);
      registry.put(operation.getOpcode(), operation);
    }

    return new EVM(registry, new InvalidOperation(gasCalculator));
  }

  public static EVM frontier(final GasCalculator gasCalculator) {
    return createAndPopulate(FRONTIER_OPERATION_FACTORIES, gasCalculator);
  }

  public static EVM homestead(final GasCalculator gasCalculator) {
    return createAndPopulate(HOMESTEAD_OPERATION_FACTORIES, gasCalculator);
  }

  public static EVM byzantium(final GasCalculator gasCalculator) {
    return createAndPopulate(BYZANTIUM_OPERATION_FACTORIES, gasCalculator);
  }

  public static EVM constantinople(final GasCalculator gasCalculator) {
    return createAndPopulate(CONSTANTINOPLE_OPERATION_FACTORIES, gasCalculator);
  }

  private static List<OperationFactory> buildFrontierFactories() {
    final ImmutableList.Builder<OperationFactory> builder = ImmutableList.builder();

    builder.add(AddOperation::new);
    builder.add(AddOperation::new);
    builder.add(MulOperation::new);
    builder.add(SubOperation::new);
    builder.add(DivOperation::new);
    builder.add(SDivOperation::new);
    builder.add(ModOperation::new);
    builder.add(SModOperation::new);
    builder.add(ExpOperation::new);
    builder.add(AddModOperation::new);
    builder.add(MulModOperation::new);
    builder.add(SignExtendOperation::new);
    builder.add(LtOperation::new);
    builder.add(GtOperation::new);
    builder.add(SLtOperation::new);
    builder.add(SGtOperation::new);
    builder.add(EqOperation::new);
    builder.add(IsZeroOperation::new);
    builder.add(AndOperation::new);
    builder.add(OrOperation::new);
    builder.add(XorOperation::new);
    builder.add(NotOperation::new);
    builder.add(ByteOperation::new);
    builder.add(Sha3Operation::new);
    builder.add(AddressOperation::new);
    builder.add(BalanceOperation::new);
    builder.add(OriginOperation::new);
    builder.add(CallerOperation::new);
    builder.add(CallValueOperation::new);
    builder.add(CallDataLoadOperation::new);
    builder.add(CallDataSizeOperation::new);
    builder.add(CallDataCopyOperation::new);
    builder.add(CodeSizeOperation::new);
    builder.add(CodeCopyOperation::new);
    builder.add(GasPriceOperation::new);
    builder.add(ExtCodeCopyOperation::new);
    builder.add(ExtCodeSizeOperation::new);
    builder.add(BlockHashOperation::new);
    builder.add(CoinbaseOperation::new);
    builder.add(TimestampOperation::new);
    builder.add(NumberOperation::new);
    builder.add(DifficultyOperation::new);
    builder.add(GasLimitOperation::new);
    builder.add(PopOperation::new);
    builder.add(MLoadOperation::new);
    builder.add(MStoreOperation::new);
    builder.add(MStore8Operation::new);
    builder.add(SLoadOperation::new);
    builder.add(SStoreOperation::new);
    builder.add(JumpOperation::new);
    builder.add(JumpiOperation::new);
    builder.add(PCOperation::new);
    builder.add(MSizeOperation::new);
    builder.add(GasOperation::new);
    builder.add(JumpDestOperation::new);
    builder.add(ReturnOperation::new);
    builder.add(InvalidOperation::new);
    builder.add(StopOperation::new);
    builder.add(SelfDestructOperation::new);
    builder.add(CreateOperation::new);
    builder.add(CallOperation::new);
    builder.add(CallCodeOperation::new);

    // Register the PUSH1, PUSH2, ..., PUSH32 operations.
    for (int i = 1; i <= 32; ++i) {
      final int n = i;
      builder.add(f -> new PushOperation(n, f));
    }

    // Register the DUP1, DUP2, ..., DUP16 operations.
    for (int i = 1; i <= 16; ++i) {
      final int n = i;
      builder.add(f -> new DupOperation(n, f));
    }

    // Register the SWAP1, SWAP2, ..., SWAP16 operations.
    for (int i = 1; i <= 16; ++i) {
      final int n = i;
      builder.add(f -> new SwapOperation(n, f));
    }

    // Register the LOG0, LOG1, ..., LOG4 operations.
    for (int i = 0; i < 5; ++i) {
      final int n = i;
      builder.add(f -> new LogOperation(n, f));
    }

    return builder.build();
  }

  private static List<OperationFactory> buildHomesteadFactories(
      final List<OperationFactory> factories) {
    final ImmutableList.Builder<OperationFactory> builder = ImmutableList.builder();

    builder.addAll(factories);
    builder.add(DelegateCallOperation::new);

    return builder.build();
  }

  private static List<OperationFactory> buildByzantiumFactories(
      final List<OperationFactory> factories) {
    final ImmutableList.Builder<OperationFactory> builder = ImmutableList.builder();

    builder.addAll(factories);
    builder.add(ReturnDataCopyOperation::new);
    builder.add(ReturnDataSizeOperation::new);
    builder.add(RevertOperation::new);
    builder.add(StaticCallOperation::new);

    return builder.build();
  }

  private static List<OperationFactory> buildConstantinopleFactories(
      final List<OperationFactory> factories) {

    final ImmutableList.Builder<OperationFactory> builder = ImmutableList.builder();

    builder.addAll(factories);
    builder.add(Create2Operation::new);
    builder.add(SarOperation::new);
    builder.add(ShlOperation::new);
    builder.add(ShrOperation::new);
    builder.add(ExtCodeHashOperation::new);

    return builder.build();
  }
}
