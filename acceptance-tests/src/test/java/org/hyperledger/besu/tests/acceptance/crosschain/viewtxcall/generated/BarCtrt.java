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
import org.web3j.abi.datatypes.Address;
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
public class BarCtrt extends CrosschainContract {
  private static final String BINARY =
      "608060405234801561001057600080fd5b5060006002819055600381905560045561050a8061002f6000396000f3fe608060405234801561001057600080fd5b50600436106100b45760003560e01c80639de16f74116100715780639de16f741461013d578063b15b10da146100b9578063be7003d514610145578063e132f82f1461014d578063fc344e9914610155578063febb0f7e1461015d576100b4565b8063484f579b146100b957806360d10428146100d3578063641b76f71461010157806384a13fba14610109578063890eba681461012d578063966aaf8014610135575b600080fd5b6100c1610165565b60408051918252519081900360200190f35b6100ff600480360360408110156100e957600080fd5b50803590602001356001600160a01b031661016a565b005b6100c1610191565b610111610197565b604080516001600160a01b039092168252519081900360200190f35b6100c16101a6565b6100ff6101ac565b6100ff6101f4565b6100ff61023b565b6100c1610283565b6100ff610289565b6100ff6102ce565b600190565b600091909155600180546001600160a01b0319166001600160a01b03909216919091179055565b60045481565b6001546001600160a01b031681565b60025481565b6000546001546040805160048152602481019091526020810180516001600160e01b0316636880d21b60e01b1790526101ef92916001600160a01b03169061030d565b600355565b6000546001546040805160048152602481019091526020810180516001600160e01b031662230c3f60e61b17905261023692916001600160a01b03169061030d565b600255565b6000546001546040805160048152602481019091526020810180516001600160e01b0316637c5ab21360e01b17905261027e92916001600160a01b03169061030d565b600455565b60035481565b6000546001546040805160048152602481019091526020810180516001600160e01b0316631d8557d760e01b1790526102cc92916001600160a01b0316906103e9565b565b6000546001546040805160048152602481019091526020810180516001600160e01b03166318530aaf60e31b17905261023692916001600160a01b0316905b6000606084848460405160200180848152602001836001600160a01b03166001600160a01b0316815260200180602001828103825283818151815260200191508051906020019080838360005b8381101561037257818101518382015260200161035a565b50505050905090810190601f16801561039f5780820380516001836020036101000a031916815260200191505b5094505050505060405160208183030381529060405290506000600482510190506103c86104b7565b602080828486600b600019fa6103dd57600080fd5b50519695505050505050565b606083838360405160200180848152602001836001600160a01b03166001600160a01b0316815260200180602001828103825283818151815260200191508051906020019080838360005b8381101561044c578181015183820152602001610434565b50505050905090810190601f1680156104795780820380516001836020036101000a031916815260200191505b50945050505050604051602081830303815290604052905060006004825101905060008082846000600a600019f16104b057600080fd5b5050505050565b6040518060200160405280600190602082028038833950919291505056fea265627a7a72315820d45c307e5cc2a5c7c964cad1f805d19e10df2607791aeee8e177d4cb2784856a64736f6c634300050c0032";

  public static final String FUNC_BAR = "bar";

  public static final String FUNC_BARUPDATESTATE = "barUpdateState";

  public static final String FUNC_BARVP = "barvp";

  public static final String FUNC_BARVV = "barvv";

  public static final String FUNC_FLAG = "flag";

  public static final String FUNC_FOOCTRT = "fooCtrt";

  public static final String FUNC_PUREBAR = "pureBar";

  public static final String FUNC_PUREFN = "purefn";

  public static final String FUNC_SETPROPERTIES = "setProperties";

  public static final String FUNC_VIEWFN = "viewfn";

  public static final String FUNC_VPFLAG = "vpflag";

  public static final String FUNC_VVFLAG = "vvflag";

  @Deprecated
  protected BarCtrt(
      String contractAddress,
      Besu besu,
      CrosschainTransactionManager crosschainTransactionManager,
      BigInteger gasPrice,
      BigInteger gasLimit) {
    super(BINARY, contractAddress, besu, crosschainTransactionManager, gasPrice, gasLimit);
  }

  protected BarCtrt(
      String contractAddress,
      Besu besu,
      CrosschainTransactionManager crosschainTransactionManager,
      ContractGasProvider contractGasProvider) {
    super(BINARY, contractAddress, besu, crosschainTransactionManager, contractGasProvider);
  }

  public RemoteFunctionCall<TransactionReceipt> bar() {
    final Function function =
        new Function(FUNC_BAR, Arrays.<Type>asList(), Collections.<TypeReference<?>>emptyList());
    return executeRemoteCallTransaction(function);
  }

  public byte[] bar_AsSignedCrosschainSubordinateTransaction(
      final CrosschainContext crosschainContext) throws IOException {
    final Function function =
        new Function(FUNC_BAR, Arrays.<Type>asList(), Collections.<TypeReference<?>>emptyList());
    return createSignedSubordinateTransaction(function, crosschainContext);
  }

  public RemoteFunctionCall<TransactionReceipt> bar_AsCrosschainTransaction(
      final CrosschainContext crosschainContext) {
    final Function function =
        new Function(FUNC_BAR, Arrays.<Type>asList(), Collections.<TypeReference<?>>emptyList());
    return executeRemoteCallCrosschainTransaction(function, crosschainContext);
  }

  public RemoteFunctionCall<TransactionReceipt> barUpdateState() {
    final Function function =
        new Function(
            FUNC_BARUPDATESTATE, Arrays.<Type>asList(), Collections.<TypeReference<?>>emptyList());
    return executeRemoteCallTransaction(function);
  }

  public byte[] barUpdateState_AsSignedCrosschainSubordinateTransaction(
      final CrosschainContext crosschainContext) throws IOException {
    final Function function =
        new Function(
            FUNC_BARUPDATESTATE, Arrays.<Type>asList(), Collections.<TypeReference<?>>emptyList());
    return createSignedSubordinateTransaction(function, crosschainContext);
  }

  public RemoteFunctionCall<TransactionReceipt> barUpdateState_AsCrosschainTransaction(
      final CrosschainContext crosschainContext) {
    final Function function =
        new Function(
            FUNC_BARUPDATESTATE, Arrays.<Type>asList(), Collections.<TypeReference<?>>emptyList());
    return executeRemoteCallCrosschainTransaction(function, crosschainContext);
  }

  public RemoteFunctionCall<TransactionReceipt> barvp() {
    final Function function =
        new Function(FUNC_BARVP, Arrays.<Type>asList(), Collections.<TypeReference<?>>emptyList());
    return executeRemoteCallTransaction(function);
  }

  public byte[] barvp_AsSignedCrosschainSubordinateTransaction(
      final CrosschainContext crosschainContext) throws IOException {
    final Function function =
        new Function(FUNC_BARVP, Arrays.<Type>asList(), Collections.<TypeReference<?>>emptyList());
    return createSignedSubordinateTransaction(function, crosschainContext);
  }

  public RemoteFunctionCall<TransactionReceipt> barvp_AsCrosschainTransaction(
      final CrosschainContext crosschainContext) {
    final Function function =
        new Function(FUNC_BARVP, Arrays.<Type>asList(), Collections.<TypeReference<?>>emptyList());
    return executeRemoteCallCrosschainTransaction(function, crosschainContext);
  }

  public RemoteFunctionCall<TransactionReceipt> barvv() {
    final Function function =
        new Function(FUNC_BARVV, Arrays.<Type>asList(), Collections.<TypeReference<?>>emptyList());
    return executeRemoteCallTransaction(function);
  }

  public byte[] barvv_AsSignedCrosschainSubordinateTransaction(
      final CrosschainContext crosschainContext) throws IOException {
    final Function function =
        new Function(FUNC_BARVV, Arrays.<Type>asList(), Collections.<TypeReference<?>>emptyList());
    return createSignedSubordinateTransaction(function, crosschainContext);
  }

  public RemoteFunctionCall<TransactionReceipt> barvv_AsCrosschainTransaction(
      final CrosschainContext crosschainContext) {
    final Function function =
        new Function(FUNC_BARVV, Arrays.<Type>asList(), Collections.<TypeReference<?>>emptyList());
    return executeRemoteCallCrosschainTransaction(function, crosschainContext);
  }

  public RemoteFunctionCall<BigInteger> flag() {
    final Function function =
        new Function(
            FUNC_FLAG,
            Arrays.<Type>asList(),
            Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
    return executeRemoteCallSingleValueReturn(function, BigInteger.class);
  }

  public byte[] flag_AsSignedCrosschainSubordinateView(final CrosschainContext crosschainContext)
      throws IOException {
    final Function function =
        new Function(
            FUNC_FLAG,
            Arrays.<Type>asList(),
            Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
    return createSignedSubordinateView(function, crosschainContext);
  }

  public RemoteFunctionCall<String> fooCtrt() {
    final Function function =
        new Function(
            FUNC_FOOCTRT,
            Arrays.<Type>asList(),
            Arrays.<TypeReference<?>>asList(new TypeReference<Address>() {}));
    return executeRemoteCallSingleValueReturn(function, String.class);
  }

  public byte[] fooCtrt_AsSignedCrosschainSubordinateView(final CrosschainContext crosschainContext)
      throws IOException {
    final Function function =
        new Function(
            FUNC_FOOCTRT,
            Arrays.<Type>asList(),
            Arrays.<TypeReference<?>>asList(new TypeReference<Address>() {}));
    return createSignedSubordinateView(function, crosschainContext);
  }

  public RemoteFunctionCall<TransactionReceipt> pureBar() {
    final Function function =
        new Function(
            FUNC_PUREBAR, Arrays.<Type>asList(), Collections.<TypeReference<?>>emptyList());
    return executeRemoteCallTransaction(function);
  }

  public byte[] pureBar_AsSignedCrosschainSubordinateTransaction(
      final CrosschainContext crosschainContext) throws IOException {
    final Function function =
        new Function(
            FUNC_PUREBAR, Arrays.<Type>asList(), Collections.<TypeReference<?>>emptyList());
    return createSignedSubordinateTransaction(function, crosschainContext);
  }

  public RemoteFunctionCall<TransactionReceipt> pureBar_AsCrosschainTransaction(
      final CrosschainContext crosschainContext) {
    final Function function =
        new Function(
            FUNC_PUREBAR, Arrays.<Type>asList(), Collections.<TypeReference<?>>emptyList());
    return executeRemoteCallCrosschainTransaction(function, crosschainContext);
  }

  public RemoteFunctionCall<BigInteger> purefn() {
    final Function function =
        new Function(
            FUNC_PUREFN,
            Arrays.<Type>asList(),
            Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
    return executeRemoteCallSingleValueReturn(function, BigInteger.class);
  }

  public byte[] purefn_AsSignedCrosschainSubordinateView(final CrosschainContext crosschainContext)
      throws IOException {
    final Function function =
        new Function(
            FUNC_PUREFN,
            Arrays.<Type>asList(),
            Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
    return createSignedSubordinateView(function, crosschainContext);
  }

  public RemoteFunctionCall<TransactionReceipt> setProperties(
      BigInteger _fooChainId, String _fooCtrtAaddr) {
    final Function function =
        new Function(
            FUNC_SETPROPERTIES,
            Arrays.<Type>asList(
                new org.web3j.abi.datatypes.generated.Uint256(_fooChainId),
                new org.web3j.abi.datatypes.Address(160, _fooCtrtAaddr)),
            Collections.<TypeReference<?>>emptyList());
    return executeRemoteCallTransaction(function);
  }

  public byte[] setProperties_AsSignedCrosschainSubordinateTransaction(
      BigInteger _fooChainId, String _fooCtrtAaddr, final CrosschainContext crosschainContext)
      throws IOException {
    final Function function =
        new Function(
            FUNC_SETPROPERTIES,
            Arrays.<Type>asList(
                new org.web3j.abi.datatypes.generated.Uint256(_fooChainId),
                new org.web3j.abi.datatypes.Address(160, _fooCtrtAaddr)),
            Collections.<TypeReference<?>>emptyList());
    return createSignedSubordinateTransaction(function, crosschainContext);
  }

  public RemoteFunctionCall<TransactionReceipt> setProperties_AsCrosschainTransaction(
      BigInteger _fooChainId, String _fooCtrtAaddr, final CrosschainContext crosschainContext) {
    final Function function =
        new Function(
            FUNC_SETPROPERTIES,
            Arrays.<Type>asList(
                new org.web3j.abi.datatypes.generated.Uint256(_fooChainId),
                new org.web3j.abi.datatypes.Address(160, _fooCtrtAaddr)),
            Collections.<TypeReference<?>>emptyList());
    return executeRemoteCallCrosschainTransaction(function, crosschainContext);
  }

  public RemoteFunctionCall<BigInteger> viewfn() {
    final Function function =
        new Function(
            FUNC_VIEWFN,
            Arrays.<Type>asList(),
            Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
    return executeRemoteCallSingleValueReturn(function, BigInteger.class);
  }

  public byte[] viewfn_AsSignedCrosschainSubordinateView(final CrosschainContext crosschainContext)
      throws IOException {
    final Function function =
        new Function(
            FUNC_VIEWFN,
            Arrays.<Type>asList(),
            Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
    return createSignedSubordinateView(function, crosschainContext);
  }

  public RemoteFunctionCall<BigInteger> vpflag() {
    final Function function =
        new Function(
            FUNC_VPFLAG,
            Arrays.<Type>asList(),
            Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
    return executeRemoteCallSingleValueReturn(function, BigInteger.class);
  }

  public byte[] vpflag_AsSignedCrosschainSubordinateView(final CrosschainContext crosschainContext)
      throws IOException {
    final Function function =
        new Function(
            FUNC_VPFLAG,
            Arrays.<Type>asList(),
            Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
    return createSignedSubordinateView(function, crosschainContext);
  }

  public RemoteFunctionCall<BigInteger> vvflag() {
    final Function function =
        new Function(
            FUNC_VVFLAG,
            Arrays.<Type>asList(),
            Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
    return executeRemoteCallSingleValueReturn(function, BigInteger.class);
  }

  public byte[] vvflag_AsSignedCrosschainSubordinateView(final CrosschainContext crosschainContext)
      throws IOException {
    final Function function =
        new Function(
            FUNC_VVFLAG,
            Arrays.<Type>asList(),
            Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
    return createSignedSubordinateView(function, crosschainContext);
  }

  @Deprecated
  public static BarCtrt load(
      String contractAddress,
      Besu besu,
      CrosschainTransactionManager crosschainTransactionManager,
      BigInteger gasPrice,
      BigInteger gasLimit) {
    return new BarCtrt(contractAddress, besu, crosschainTransactionManager, gasPrice, gasLimit);
  }

  public static BarCtrt load(
      String contractAddress,
      Besu besu,
      CrosschainTransactionManager crosschainTransactionManager,
      ContractGasProvider contractGasProvider) {
    return new BarCtrt(contractAddress, besu, crosschainTransactionManager, contractGasProvider);
  }

  public static RemoteCall<BarCtrt> deployLockable(
      Besu besu,
      CrosschainTransactionManager transactionManager,
      ContractGasProvider contractGasProvider) {
    CrosschainContext crosschainContext = null;
    return deployLockableContractRemoteCall(
        BarCtrt.class,
        besu,
        transactionManager,
        contractGasProvider,
        BINARY,
        "",
        crosschainContext);
  }

  @Deprecated
  public static RemoteCall<BarCtrt> deployLockable(
      Besu besu,
      CrosschainTransactionManager transactionManager,
      BigInteger gasPrice,
      BigInteger gasLimit) {
    CrosschainContext crosschainContext = null;
    return deployLockableContractRemoteCall(
        BarCtrt.class, besu, transactionManager, gasPrice, gasLimit, BINARY, "", crosschainContext);
  }

  public static RemoteCall<BarCtrt> deployLockable(
      Besu besu,
      CrosschainTransactionManager transactionManager,
      ContractGasProvider contractGasProvider,
      final CrosschainContext crosschainContext) {
    return deployLockableContractRemoteCall(
        BarCtrt.class,
        besu,
        transactionManager,
        contractGasProvider,
        BINARY,
        "",
        crosschainContext);
  }

  @Deprecated
  public static RemoteCall<BarCtrt> deployLockable(
      Besu besu,
      CrosschainTransactionManager transactionManager,
      BigInteger gasPrice,
      BigInteger gasLimit,
      final CrosschainContext crosschainContext) {
    return deployLockableContractRemoteCall(
        BarCtrt.class, besu, transactionManager, gasPrice, gasLimit, BINARY, "", crosschainContext);
  }
}
