/*
 * Copyright 2019 ConsenSys AG.
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
package org.hyperledger.besu.tests.acceptance.crosschain.viewtxcall.generated;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;

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
public class Bar2Ctrt extends CrosschainContract {
  private static final String BINARY =
      "6080604052348015600f57600080fd5b50600080556099806100226000396000f3fe6080604052348015600f57600080fd5b506004361060325760003560e01c80631d8557d7146037578063e9f6392314603f575b600080fd5b603d6057565b005b6045605e565b60408051918252519081900360200190f35b6001600055565b6000548156fea265627a7a7231582054e779e43c3c9f78b7e49904adec8872942767045d7281bc1a8e3d2eb7f0eae364736f6c634300050c0032";

  public static final String FUNC_TTVFLAG = "ttvflag";

  public static final String FUNC_UPDATESTATE = "updateState";

  @Deprecated
  protected Bar2Ctrt(
      String contractAddress,
      Besu besu,
      CrosschainTransactionManager crosschainTransactionManager,
      BigInteger gasPrice,
      BigInteger gasLimit) {
    super(BINARY, contractAddress, besu, crosschainTransactionManager, gasPrice, gasLimit);
  }

  protected Bar2Ctrt(
      String contractAddress,
      Besu besu,
      CrosschainTransactionManager crosschainTransactionManager,
      ContractGasProvider contractGasProvider) {
    super(BINARY, contractAddress, besu, crosschainTransactionManager, contractGasProvider);
  }

  public RemoteFunctionCall<BigInteger> ttvflag() {
    final Function function =
        new Function(
            FUNC_TTVFLAG,
            Arrays.<Type>asList(),
            Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
    return executeRemoteCallSingleValueReturn(function, BigInteger.class);
  }

  public byte[] ttvflag_AsSignedCrosschainSubordinateView(final CrosschainContext crosschainContext)
      throws IOException {
    final Function function =
        new Function(
            FUNC_TTVFLAG,
            Arrays.<Type>asList(),
            Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
    return createSignedSubordinateView(function, crosschainContext);
  }

  public RemoteFunctionCall<TransactionReceipt> updateState() {
    final Function function =
        new Function(
            FUNC_UPDATESTATE, Arrays.<Type>asList(), Collections.<TypeReference<?>>emptyList());
    return executeRemoteCallTransaction(function);
  }

  public byte[] updateState_AsSignedCrosschainSubordinateTransaction(
      final CrosschainContext crosschainContext) throws IOException {
    final Function function =
        new Function(
            FUNC_UPDATESTATE, Arrays.<Type>asList(), Collections.<TypeReference<?>>emptyList());
    return createSignedSubordinateTransaction(function, crosschainContext);
  }

  public RemoteFunctionCall<TransactionReceipt> updateState_AsCrosschainTransaction(
      final CrosschainContext crosschainContext) {
    final Function function =
        new Function(
            FUNC_UPDATESTATE, Arrays.<Type>asList(), Collections.<TypeReference<?>>emptyList());
    return executeRemoteCallCrosschainTransaction(function, crosschainContext);
  }

  @Deprecated
  public static Bar2Ctrt load(
      String contractAddress,
      Besu besu,
      CrosschainTransactionManager crosschainTransactionManager,
      BigInteger gasPrice,
      BigInteger gasLimit) {
    return new Bar2Ctrt(contractAddress, besu, crosschainTransactionManager, gasPrice, gasLimit);
  }

  public static Bar2Ctrt load(
      String contractAddress,
      Besu besu,
      CrosschainTransactionManager crosschainTransactionManager,
      ContractGasProvider contractGasProvider) {
    return new Bar2Ctrt(contractAddress, besu, crosschainTransactionManager, contractGasProvider);
  }

  public static RemoteCall<Bar2Ctrt> deployLockable(
      Besu besu,
      CrosschainTransactionManager transactionManager,
      ContractGasProvider contractGasProvider) {
    CrosschainContext crosschainContext = null;
    return deployLockableContractRemoteCall(
        Bar2Ctrt.class,
        besu,
        transactionManager,
        contractGasProvider,
        BINARY,
        "",
        crosschainContext);
  }

  @Deprecated
  public static RemoteCall<Bar2Ctrt> deployLockable(
      Besu besu,
      CrosschainTransactionManager transactionManager,
      BigInteger gasPrice,
      BigInteger gasLimit) {
    CrosschainContext crosschainContext = null;
    return deployLockableContractRemoteCall(
        Bar2Ctrt.class,
        besu,
        transactionManager,
        gasPrice,
        gasLimit,
        BINARY,
        "",
        crosschainContext);
  }

  public static RemoteCall<Bar2Ctrt> deployLockable(
      Besu besu,
      CrosschainTransactionManager transactionManager,
      ContractGasProvider contractGasProvider,
      final CrosschainContext crosschainContext) {
    return deployLockableContractRemoteCall(
        Bar2Ctrt.class,
        besu,
        transactionManager,
        contractGasProvider,
        BINARY,
        "",
        crosschainContext);
  }

  @Deprecated
  public static RemoteCall<Bar2Ctrt> deployLockable(
      Besu besu,
      CrosschainTransactionManager transactionManager,
      BigInteger gasPrice,
      BigInteger gasLimit,
      final CrosschainContext crosschainContext) {
    return deployLockableContractRemoteCall(
        Bar2Ctrt.class,
        besu,
        transactionManager,
        gasPrice,
        gasLimit,
        BINARY,
        "",
        crosschainContext);
  }
}
