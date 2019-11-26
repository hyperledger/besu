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
package org.hyperledger.besu.tests.acceptance.crosschain.lockability.generated;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;

import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.RemoteCall;
import org.web3j.protocol.core.RemoteFunctionCall;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.tx.Contract;
import org.web3j.tx.TransactionManager;
import org.web3j.tx.gas.ContractGasProvider;

/**
 * Auto generated code.
 *
 * <p><strong>Do not modify!</strong>
 *
 * <p>Please use the <a href="https://docs.web3j.io/command_line.html">web3j command line tools</a>,
 * or the org.web3j.codegen.SolidityFunctionWrapperGenerator in the <a
 * href="https://github.com/web3j/web3j/tree/master/codegen">codegen module</a> to update.
 *
 * <p>Generated with web3j version 4.6.0-SNAPSHOT.
 */
@SuppressWarnings("rawtypes")
public class SimpleIsLockable extends Contract {
  private static final String BINARY =
      "608060405234801561001057600080fd5b5060ab8061001f6000396000f3fe6080604052348015600f57600080fd5b506004361060325760003560e01c80633fa4f2451460375780635524107714604f575b600080fd5b603d606b565b60408051918252519081900360200190f35b606960048036036020811015606357600080fd5b50356071565b005b60005481565b60005556fea265627a7a72305820c4808e009aa805380415222f9a4b7a2104c14551c284abf7e322ec02a76eddfa64736f6c634300050a0032";

  public static final String FUNC_VALUE = "value";

  public static final String FUNC_SETVALUE = "setValue";

  @Deprecated
  protected SimpleIsLockable(
      String contractAddress,
      Web3j web3j,
      Credentials credentials,
      BigInteger gasPrice,
      BigInteger gasLimit) {
    super(BINARY, contractAddress, web3j, credentials, gasPrice, gasLimit);
  }

  protected SimpleIsLockable(
      String contractAddress,
      Web3j web3j,
      Credentials credentials,
      ContractGasProvider contractGasProvider) {
    super(BINARY, contractAddress, web3j, credentials, contractGasProvider);
  }

  @Deprecated
  protected SimpleIsLockable(
      String contractAddress,
      Web3j web3j,
      TransactionManager transactionManager,
      BigInteger gasPrice,
      BigInteger gasLimit) {
    super(BINARY, contractAddress, web3j, transactionManager, gasPrice, gasLimit);
  }

  protected SimpleIsLockable(
      String contractAddress,
      Web3j web3j,
      TransactionManager transactionManager,
      ContractGasProvider contractGasProvider) {
    super(BINARY, contractAddress, web3j, transactionManager, contractGasProvider);
  }

  public RemoteFunctionCall<BigInteger> value() {
    final Function function =
        new Function(
            FUNC_VALUE,
            Arrays.<Type>asList(),
            Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
    return executeRemoteCallSingleValueReturn(function, BigInteger.class);
  }

  public RemoteFunctionCall<TransactionReceipt> setValue(BigInteger _val) {
    final Function function =
        new Function(
            FUNC_SETVALUE,
            Arrays.<Type>asList(new org.web3j.abi.datatypes.generated.Uint256(_val)),
            Collections.<TypeReference<?>>emptyList());
    return executeRemoteCallTransaction(function);
  }

  @Deprecated
  public static org.hyperledger.besu.tests.acceptance.crosschain.lockability.generated
          .SimpleIsLockable
      load(
          String contractAddress,
          Web3j web3j,
          Credentials credentials,
          BigInteger gasPrice,
          BigInteger gasLimit) {
    return new org.hyperledger.besu.tests.acceptance.crosschain.lockability.generated
        .SimpleIsLockable(contractAddress, web3j, credentials, gasPrice, gasLimit);
  }

  @Deprecated
  public static org.hyperledger.besu.tests.acceptance.crosschain.lockability.generated
          .SimpleIsLockable
      load(
          String contractAddress,
          Web3j web3j,
          TransactionManager transactionManager,
          BigInteger gasPrice,
          BigInteger gasLimit) {
    return new org.hyperledger.besu.tests.acceptance.crosschain.lockability.generated
        .SimpleIsLockable(contractAddress, web3j, transactionManager, gasPrice, gasLimit);
  }

  public static org.hyperledger.besu.tests.acceptance.crosschain.lockability.generated
          .SimpleIsLockable
      load(
          String contractAddress,
          Web3j web3j,
          Credentials credentials,
          ContractGasProvider contractGasProvider) {
    return new org.hyperledger.besu.tests.acceptance.crosschain.lockability.generated
        .SimpleIsLockable(contractAddress, web3j, credentials, contractGasProvider);
  }

  public static org.hyperledger.besu.tests.acceptance.crosschain.lockability.generated
          .SimpleIsLockable
      load(
          String contractAddress,
          Web3j web3j,
          TransactionManager transactionManager,
          ContractGasProvider contractGasProvider) {
    return new org.hyperledger.besu.tests.acceptance.crosschain.lockability.generated
        .SimpleIsLockable(contractAddress, web3j, transactionManager, contractGasProvider);
  }

  public static RemoteCall<
          org.hyperledger.besu.tests.acceptance.crosschain.lockability.generated.SimpleIsLockable>
      deploy(Web3j web3j, Credentials credentials, ContractGasProvider contractGasProvider) {
    return deployRemoteCall(
        org.hyperledger.besu.tests.acceptance.crosschain.lockability.generated.SimpleIsLockable
            .class,
        web3j,
        credentials,
        contractGasProvider,
        BINARY,
        "");
  }

  @Deprecated
  public static RemoteCall<
          org.hyperledger.besu.tests.acceptance.crosschain.lockability.generated.SimpleIsLockable>
      deploy(Web3j web3j, Credentials credentials, BigInteger gasPrice, BigInteger gasLimit) {
    return deployRemoteCall(
        org.hyperledger.besu.tests.acceptance.crosschain.lockability.generated.SimpleIsLockable
            .class,
        web3j,
        credentials,
        gasPrice,
        gasLimit,
        BINARY,
        "");
  }

  public static RemoteCall<
          org.hyperledger.besu.tests.acceptance.crosschain.lockability.generated.SimpleIsLockable>
      deploy(
          Web3j web3j,
          TransactionManager transactionManager,
          ContractGasProvider contractGasProvider) {
    return deployRemoteCall(
        org.hyperledger.besu.tests.acceptance.crosschain.lockability.generated.SimpleIsLockable
            .class,
        web3j,
        transactionManager,
        contractGasProvider,
        BINARY,
        "");
  }

  @Deprecated
  public static RemoteCall<
          org.hyperledger.besu.tests.acceptance.crosschain.lockability.generated.SimpleIsLockable>
      deploy(
          Web3j web3j,
          TransactionManager transactionManager,
          BigInteger gasPrice,
          BigInteger gasLimit) {
    return deployRemoteCall(
        org.hyperledger.besu.tests.acceptance.crosschain.lockability.generated.SimpleIsLockable
            .class,
        web3j,
        transactionManager,
        gasPrice,
        gasLimit,
        BINARY,
        "");
  }
}
