/*
 * Copyright 2020 ConsenSys AG.
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
package org.hyperledger.besu.tests.acceptance.crosschain.viewtxcallArgs.generated;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.protocol.besu.Besu;
import org.web3j.protocol.core.RemoteCall;
import org.web3j.protocol.core.RemoteFunctionCall;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.tx.CrosschainContext;
import org.web3j.tx.CrosschainContract;
import org.web3j.tx.CrosschainTransactionManager;
import org.web3j.tx.gas.ContractGasProvider;

/**
 * Auto generated code.
 *
 * <p><strong>Do not modify!</strong>
 *
 * <p>Please use the org.web3j.codegen.CrosschainSolidityFunctionWrapperGenerator in the <a
 * href="https://github.com/PegaSysEng/sidechains-web3j/tree/master/besucodegen">codegen module</a>
 * to update.
 *
 * <p>Generated with web3j version 4.6.0-SNAPSHOT.
 */
@SuppressWarnings("rawtypes")
public class FooArgsCtrt extends CrosschainContract {
  private static final String BINARY =
      "608060405234801561001057600080fd5b50600080556102c3806100246000396000f3fe608060405234801561001057600080fd5b506004361061004c5760003560e01c80636adda13f146100515780637e4ab77a14610111578063c8ba4142146101e5578063fd470b2d146101ed575b600080fd5b61010f6004803603604081101561006757600080fd5b810190602081018135600160201b81111561008157600080fd5b82018360208201111561009357600080fd5b803590602001918460208302840111600160201b831117156100b457600080fd5b919390929091602081019035600160201b8111156100d157600080fd5b8201836020820111156100e357600080fd5b803590602001918460018302840111600160201b8311171561010457600080fd5b509092509050610219565b005b6101d36004803603606081101561012757600080fd5b810190602081018135600160201b81111561014157600080fd5b82018360208201111561015357600080fd5b803590602001918460208302840111600160201b8311171561017457600080fd5b91939092823592604081019060200135600160201b81111561019557600080fd5b8201836020820111156101a757600080fd5b803590602001918460018302840111600160201b831117156101c857600080fd5b509092509050610239565b60408051918252519081900360200190f35b6101d3610261565b61010f6004803603604081101561020357600080fd5b50803590602001356001600160a01b0316610267565b80848460008161022557fe5b905060200201350160008190555050505050565b600081848787600019810181811061024d57fe5b905060200201350101905095945050505050565b60005481565b600191909155600280546001600160a01b0319166001600160a01b0390921691909117905556fea265627a7a72315820afa849719c86e728ecbd9b318dd0919958d2d81fe9c5e184c6f1f9bfae689cbd64736f6c634300050c0032";

  public static final String FUNC_FOO = "foo";

  public static final String FUNC_FOOFLAG = "fooFlag";

  public static final String FUNC_SETPROPERTIESFORBAR = "setPropertiesForBar";

  public static final String FUNC_UPDATESTATE = "updateState";

  @Deprecated
  protected FooArgsCtrt(
      String contractAddress,
      Besu besu,
      CrosschainTransactionManager crosschainTransactionManager,
      BigInteger gasPrice,
      BigInteger gasLimit) {
    super(BINARY, contractAddress, besu, crosschainTransactionManager, gasPrice, gasLimit);
  }

  protected FooArgsCtrt(
      String contractAddress,
      Besu besu,
      CrosschainTransactionManager crosschainTransactionManager,
      ContractGasProvider contractGasProvider) {
    super(BINARY, contractAddress, besu, crosschainTransactionManager, contractGasProvider);
  }

  public RemoteFunctionCall<BigInteger> foo(List<BigInteger> arg1, byte[] a, String str) {
    final Function function =
        new Function(
            FUNC_FOO,
            Arrays.<Type>asList(
                new org.web3j.abi.datatypes.DynamicArray<org.web3j.abi.datatypes.generated.Uint256>(
                    org.web3j.abi.datatypes.generated.Uint256.class,
                    org.web3j.abi.Utils.typeMap(
                        arg1, org.web3j.abi.datatypes.generated.Uint256.class)),
                new org.web3j.abi.datatypes.generated.Bytes32(a),
                new org.web3j.abi.datatypes.Utf8String(str)),
            Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
    return executeRemoteCallSingleValueReturn(function, BigInteger.class);
  }

  public byte[] foo_AsSignedCrosschainSubordinateView(
      List<BigInteger> arg1, byte[] a, String str, final CrosschainContext crosschainContext)
      throws IOException {
    final Function function =
        new Function(
            FUNC_FOO,
            Arrays.<Type>asList(
                new org.web3j.abi.datatypes.DynamicArray<org.web3j.abi.datatypes.generated.Uint256>(
                    org.web3j.abi.datatypes.generated.Uint256.class,
                    org.web3j.abi.Utils.typeMap(
                        arg1, org.web3j.abi.datatypes.generated.Uint256.class)),
                new org.web3j.abi.datatypes.generated.Bytes32(a),
                new org.web3j.abi.datatypes.Utf8String(str)),
            Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
    return createSignedSubordinateView(function, crosschainContext);
  }

  public RemoteFunctionCall<BigInteger> fooFlag() {
    final Function function =
        new Function(
            FUNC_FOOFLAG,
            Arrays.<Type>asList(),
            Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
    return executeRemoteCallSingleValueReturn(function, BigInteger.class);
  }

  public byte[] fooFlag_AsSignedCrosschainSubordinateView(final CrosschainContext crosschainContext)
      throws IOException {
    final Function function =
        new Function(
            FUNC_FOOFLAG,
            Arrays.<Type>asList(),
            Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
    return createSignedSubordinateView(function, crosschainContext);
  }

  public RemoteFunctionCall<TransactionReceipt> setPropertiesForBar(
      BigInteger _barChainId, String _barCtrtAddress) {
    final Function function =
        new Function(
            FUNC_SETPROPERTIESFORBAR,
            Arrays.<Type>asList(
                new org.web3j.abi.datatypes.generated.Uint256(_barChainId),
                new org.web3j.abi.datatypes.Address(160, _barCtrtAddress)),
            Collections.<TypeReference<?>>emptyList());
    return executeRemoteCallTransaction(function);
  }

  public byte[] setPropertiesForBar_AsSignedCrosschainSubordinateTransaction(
      BigInteger _barChainId, String _barCtrtAddress, final CrosschainContext crosschainContext)
      throws IOException {
    final Function function =
        new Function(
            FUNC_SETPROPERTIESFORBAR,
            Arrays.<Type>asList(
                new org.web3j.abi.datatypes.generated.Uint256(_barChainId),
                new org.web3j.abi.datatypes.Address(160, _barCtrtAddress)),
            Collections.<TypeReference<?>>emptyList());
    return createSignedSubordinateTransaction(function, crosschainContext);
  }

  public RemoteFunctionCall<TransactionReceipt> setPropertiesForBar_AsCrosschainTransaction(
      BigInteger _barChainId, String _barCtrtAddress, final CrosschainContext crosschainContext) {
    final Function function =
        new Function(
            FUNC_SETPROPERTIESFORBAR,
            Arrays.<Type>asList(
                new org.web3j.abi.datatypes.generated.Uint256(_barChainId),
                new org.web3j.abi.datatypes.Address(160, _barCtrtAddress)),
            Collections.<TypeReference<?>>emptyList());
    return executeRemoteCallCrosschainTransaction(function, crosschainContext);
  }

  public RemoteFunctionCall<TransactionReceipt> updateState(
      List<BigInteger> magicNumArr, String str) {
    final Function function =
        new Function(
            FUNC_UPDATESTATE,
            Arrays.<Type>asList(
                new org.web3j.abi.datatypes.DynamicArray<org.web3j.abi.datatypes.generated.Uint256>(
                    org.web3j.abi.datatypes.generated.Uint256.class,
                    org.web3j.abi.Utils.typeMap(
                        magicNumArr, org.web3j.abi.datatypes.generated.Uint256.class)),
                new org.web3j.abi.datatypes.Utf8String(str)),
            Collections.<TypeReference<?>>emptyList());
    return executeRemoteCallTransaction(function);
  }

  public byte[] updateState_AsSignedCrosschainSubordinateTransaction(
      List<BigInteger> magicNumArr, String str, final CrosschainContext crosschainContext)
      throws IOException {
    final Function function =
        new Function(
            FUNC_UPDATESTATE,
            Arrays.<Type>asList(
                new org.web3j.abi.datatypes.DynamicArray<org.web3j.abi.datatypes.generated.Uint256>(
                    org.web3j.abi.datatypes.generated.Uint256.class,
                    org.web3j.abi.Utils.typeMap(
                        magicNumArr, org.web3j.abi.datatypes.generated.Uint256.class)),
                new org.web3j.abi.datatypes.Utf8String(str)),
            Collections.<TypeReference<?>>emptyList());
    return createSignedSubordinateTransaction(function, crosschainContext);
  }

  public RemoteFunctionCall<TransactionReceipt> updateState_AsCrosschainTransaction(
      List<BigInteger> magicNumArr, String str, final CrosschainContext crosschainContext) {
    final Function function =
        new Function(
            FUNC_UPDATESTATE,
            Arrays.<Type>asList(
                new org.web3j.abi.datatypes.DynamicArray<org.web3j.abi.datatypes.generated.Uint256>(
                    org.web3j.abi.datatypes.generated.Uint256.class,
                    org.web3j.abi.Utils.typeMap(
                        magicNumArr, org.web3j.abi.datatypes.generated.Uint256.class)),
                new org.web3j.abi.datatypes.Utf8String(str)),
            Collections.<TypeReference<?>>emptyList());
    return executeRemoteCallCrosschainTransaction(function, crosschainContext);
  }

  @Deprecated
  public static FooArgsCtrt load(
      String contractAddress,
      Besu besu,
      CrosschainTransactionManager crosschainTransactionManager,
      BigInteger gasPrice,
      BigInteger gasLimit) {
    return new FooArgsCtrt(contractAddress, besu, crosschainTransactionManager, gasPrice, gasLimit);
  }

  public static FooArgsCtrt load(
      String contractAddress,
      Besu besu,
      CrosschainTransactionManager crosschainTransactionManager,
      ContractGasProvider contractGasProvider) {
    return new FooArgsCtrt(
        contractAddress, besu, crosschainTransactionManager, contractGasProvider);
  }

  public static RemoteCall<FooArgsCtrt> deployLockable(
      Besu besu,
      CrosschainTransactionManager transactionManager,
      ContractGasProvider contractGasProvider) {
    CrosschainContext crosschainContext = null;
    return deployLockableContractRemoteCall(
        FooArgsCtrt.class,
        besu,
        transactionManager,
        contractGasProvider,
        BINARY,
        "",
        crosschainContext);
  }

  @Deprecated
  public static RemoteCall<FooArgsCtrt> deployLockable(
      Besu besu,
      CrosschainTransactionManager transactionManager,
      BigInteger gasPrice,
      BigInteger gasLimit) {
    CrosschainContext crosschainContext = null;
    return deployLockableContractRemoteCall(
        FooArgsCtrt.class,
        besu,
        transactionManager,
        gasPrice,
        gasLimit,
        BINARY,
        "",
        crosschainContext);
  }

  public static RemoteCall<FooArgsCtrt> deployLockable(
      Besu besu,
      CrosschainTransactionManager transactionManager,
      ContractGasProvider contractGasProvider,
      final CrosschainContext crosschainContext) {
    return deployLockableContractRemoteCall(
        FooArgsCtrt.class,
        besu,
        transactionManager,
        contractGasProvider,
        BINARY,
        "",
        crosschainContext);
  }

  @Deprecated
  public static RemoteCall<FooArgsCtrt> deployLockable(
      Besu besu,
      CrosschainTransactionManager transactionManager,
      BigInteger gasPrice,
      BigInteger gasLimit,
      final CrosschainContext crosschainContext) {
    return deployLockableContractRemoteCall(
        FooArgsCtrt.class,
        besu,
        transactionManager,
        gasPrice,
        gasLimit,
        BINARY,
        "",
        crosschainContext);
  }
}
