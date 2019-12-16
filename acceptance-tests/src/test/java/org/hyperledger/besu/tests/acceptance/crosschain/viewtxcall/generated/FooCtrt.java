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
public class FooCtrt extends CrosschainContract {
  private static final String BINARY =
      "608060405234801561001057600080fd5b506000808055600181905560028190556003556105d9806100326000396000f3fe608060405234801561001057600080fd5b50600436106100ea5760003560e01c80636880d21b1161008c578063b54e32fa11610066578063b54e32fa14610153578063c29855781461017f578063c8ba414214610187578063fd470b2d1461018f576100ea565b80636880d21b1461013b5780637c5ab21314610143578063a3bed6041461014b576100ea565b8063296d77b7116100c8578063296d77b71461011b57806341b62a3614610123578063454388211461012b5780635b4cc65614610133576100ea565b806308c30fc0146100ef5780631c0e1571146101095780631d8557d714610111575b600080fd5b6100f76101bb565b60408051918252519081900360200190f35b6100f76101c0565b6101196101c6565b005b6100f76101cd565b6100f76101d3565b6101196101d9565b610119610264565b6100f76102ac565b6100f76102f6565b61011961033b565b6101196004803603604081101561016957600080fd5b50803590602001356001600160a01b0316610383565b6100f76103aa565b6100f76103af565b610119600480360360408110156101a557600080fd5b50803590602001356001600160a01b03166103b5565b600290565b60035481565b6001600055565b60025481565b60015481565b60048054600654604080519384526024840190526020830180516001600160e01b031663484f579b60e01b17905261021c926001600160a01b03909116906103dc565b6003556005546007546040805160048152602481019091526020810180516001600160e01b0316631d8557d760e01b17905261026292916001600160a01b0316906104b8565b565b60048054600654604080519384526024840190526020830180516001600160e01b03166358ad886d60e11b1790526102a7926001600160a01b03909116906103dc565b600255565b60048054600654604080519384526024840190526020830180516001600160e01b031663484f579b60e01b1790526000926102f192916001600160a01b0316906103dc565b905090565b60048054600654604080519384526024840190526020830180516001600160e01b03166358ad886d60e11b1790526000926102f192916001600160a01b0316906103dc565b60048054600654604080519384526024840190526020830180516001600160e01b031663484f579b60e01b17905261037e926001600160a01b03909116906103dc565b600155565b600591909155600780546001600160a01b0319166001600160a01b03909216919091179055565b600190565b60005481565b600491909155600680546001600160a01b0319166001600160a01b03909216919091179055565b6000606084848460405160200180848152602001836001600160a01b03166001600160a01b0316815260200180602001828103825283818151815260200191508051906020019080838360005b83811015610441578181015183820152602001610429565b50505050905090810190601f16801561046e5780820380516001836020036101000a031916815260200191505b509450505050506040516020818303038152906040529050600060048251019050610497610586565b602080828486600b600019fa6104ac57600080fd5b50519695505050505050565b606083838360405160200180848152602001836001600160a01b03166001600160a01b0316815260200180602001828103825283818151815260200191508051906020019080838360005b8381101561051b578181015183820152602001610503565b50505050905090810190601f1680156105485780820380516001836020036101000a031916815260200191505b50945050505050604051602081830303815290604052905060006004825101905060008082846000600a600019f161057f57600080fd5b5050505050565b6040518060200160405280600190602082028038833950919291505056fea265627a7a72315820eccd4425cd3fdfbd107d66c6882bd333bec6b1e729f05b380fd61ade03eff19d64736f6c634300050c0032";

  public static final String FUNC_FOO = "foo";

  public static final String FUNC_FOOFLAG = "fooFlag";

  public static final String FUNC_FOOVP = "foovp";

  public static final String FUNC_FOOVV = "foovv";

  public static final String FUNC_PUREFOO = "pureFoo";

  public static final String FUNC_SETPROPERTIESFORBAR = "setPropertiesForBar";

  public static final String FUNC_SETPROPERTIESFORBAR2 = "setPropertiesForBar2";

  public static final String FUNC_TPFLAG = "tpFlag";

  public static final String FUNC_TTVFLAG = "ttvFlag";

  public static final String FUNC_TVFLAG = "tvFlag";

  public static final String FUNC_UPDATESTATE = "updateState";

  public static final String FUNC_UPDATESTATEFROMPURE = "updateStateFromPure";

  public static final String FUNC_UPDATESTATEFROMTXVIEW = "updateStateFromTxView";

  public static final String FUNC_UPDATESTATEFROMVIEW = "updateStateFromView";

  @Deprecated
  protected FooCtrt(
      String contractAddress,
      Besu besu,
      CrosschainTransactionManager crosschainTransactionManager,
      BigInteger gasPrice,
      BigInteger gasLimit) {
    super(BINARY, contractAddress, besu, crosschainTransactionManager, gasPrice, gasLimit);
  }

  protected FooCtrt(
      String contractAddress,
      Besu besu,
      CrosschainTransactionManager crosschainTransactionManager,
      ContractGasProvider contractGasProvider) {
    super(BINARY, contractAddress, besu, crosschainTransactionManager, contractGasProvider);
  }

  public RemoteFunctionCall<BigInteger> foo() {
    final Function function =
        new Function(
            FUNC_FOO,
            Arrays.<Type>asList(),
            Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
    return executeRemoteCallSingleValueReturn(function, BigInteger.class);
  }

  public byte[] foo_AsSignedCrosschainSubordinateView(final CrosschainContext crosschainContext)
      throws IOException {
    final Function function =
        new Function(
            FUNC_FOO,
            Arrays.<Type>asList(),
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

  public RemoteFunctionCall<BigInteger> foovp() {
    final Function function =
        new Function(
            FUNC_FOOVP,
            Arrays.<Type>asList(),
            Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
    return executeRemoteCallSingleValueReturn(function, BigInteger.class);
  }

  public byte[] foovp_AsSignedCrosschainSubordinateView(final CrosschainContext crosschainContext)
      throws IOException {
    final Function function =
        new Function(
            FUNC_FOOVP,
            Arrays.<Type>asList(),
            Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
    return createSignedSubordinateView(function, crosschainContext);
  }

  public RemoteFunctionCall<BigInteger> foovv() {
    final Function function =
        new Function(
            FUNC_FOOVV,
            Arrays.<Type>asList(),
            Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
    return executeRemoteCallSingleValueReturn(function, BigInteger.class);
  }

  public byte[] foovv_AsSignedCrosschainSubordinateView(final CrosschainContext crosschainContext)
      throws IOException {
    final Function function =
        new Function(
            FUNC_FOOVV,
            Arrays.<Type>asList(),
            Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
    return createSignedSubordinateView(function, crosschainContext);
  }

  public RemoteFunctionCall<BigInteger> pureFoo() {
    final Function function =
        new Function(
            FUNC_PUREFOO,
            Arrays.<Type>asList(),
            Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
    return executeRemoteCallSingleValueReturn(function, BigInteger.class);
  }

  public byte[] pureFoo_AsSignedCrosschainSubordinateView(final CrosschainContext crosschainContext)
      throws IOException {
    final Function function =
        new Function(
            FUNC_PUREFOO,
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

  public RemoteFunctionCall<TransactionReceipt> setPropertiesForBar2(
      BigInteger _bar2ChainId, String _bar2CtrtAddress) {
    final Function function =
        new Function(
            FUNC_SETPROPERTIESFORBAR2,
            Arrays.<Type>asList(
                new org.web3j.abi.datatypes.generated.Uint256(_bar2ChainId),
                new org.web3j.abi.datatypes.Address(160, _bar2CtrtAddress)),
            Collections.<TypeReference<?>>emptyList());
    return executeRemoteCallTransaction(function);
  }

  public byte[] setPropertiesForBar2_AsSignedCrosschainSubordinateTransaction(
      BigInteger _bar2ChainId, String _bar2CtrtAddress, final CrosschainContext crosschainContext)
      throws IOException {
    final Function function =
        new Function(
            FUNC_SETPROPERTIESFORBAR2,
            Arrays.<Type>asList(
                new org.web3j.abi.datatypes.generated.Uint256(_bar2ChainId),
                new org.web3j.abi.datatypes.Address(160, _bar2CtrtAddress)),
            Collections.<TypeReference<?>>emptyList());
    return createSignedSubordinateTransaction(function, crosschainContext);
  }

  public RemoteFunctionCall<TransactionReceipt> setPropertiesForBar2_AsCrosschainTransaction(
      BigInteger _bar2ChainId, String _bar2CtrtAddress, final CrosschainContext crosschainContext) {
    final Function function =
        new Function(
            FUNC_SETPROPERTIESFORBAR2,
            Arrays.<Type>asList(
                new org.web3j.abi.datatypes.generated.Uint256(_bar2ChainId),
                new org.web3j.abi.datatypes.Address(160, _bar2CtrtAddress)),
            Collections.<TypeReference<?>>emptyList());
    return executeRemoteCallCrosschainTransaction(function, crosschainContext);
  }

  public RemoteFunctionCall<BigInteger> tpFlag() {
    final Function function =
        new Function(
            FUNC_TPFLAG,
            Arrays.<Type>asList(),
            Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
    return executeRemoteCallSingleValueReturn(function, BigInteger.class);
  }

  public byte[] tpFlag_AsSignedCrosschainSubordinateView(final CrosschainContext crosschainContext)
      throws IOException {
    final Function function =
        new Function(
            FUNC_TPFLAG,
            Arrays.<Type>asList(),
            Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
    return createSignedSubordinateView(function, crosschainContext);
  }

  public RemoteFunctionCall<BigInteger> ttvFlag() {
    final Function function =
        new Function(
            FUNC_TTVFLAG,
            Arrays.<Type>asList(),
            Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
    return executeRemoteCallSingleValueReturn(function, BigInteger.class);
  }

  public byte[] ttvFlag_AsSignedCrosschainSubordinateView(final CrosschainContext crosschainContext)
      throws IOException {
    final Function function =
        new Function(
            FUNC_TTVFLAG,
            Arrays.<Type>asList(),
            Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
    return createSignedSubordinateView(function, crosschainContext);
  }

  public RemoteFunctionCall<BigInteger> tvFlag() {
    final Function function =
        new Function(
            FUNC_TVFLAG,
            Arrays.<Type>asList(),
            Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
    return executeRemoteCallSingleValueReturn(function, BigInteger.class);
  }

  public byte[] tvFlag_AsSignedCrosschainSubordinateView(final CrosschainContext crosschainContext)
      throws IOException {
    final Function function =
        new Function(
            FUNC_TVFLAG,
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

  public RemoteFunctionCall<TransactionReceipt> updateStateFromPure() {
    final Function function =
        new Function(
            FUNC_UPDATESTATEFROMPURE,
            Arrays.<Type>asList(),
            Collections.<TypeReference<?>>emptyList());
    return executeRemoteCallTransaction(function);
  }

  public byte[] updateStateFromPure_AsSignedCrosschainSubordinateTransaction(
      final CrosschainContext crosschainContext) throws IOException {
    final Function function =
        new Function(
            FUNC_UPDATESTATEFROMPURE,
            Arrays.<Type>asList(),
            Collections.<TypeReference<?>>emptyList());
    return createSignedSubordinateTransaction(function, crosschainContext);
  }

  public RemoteFunctionCall<TransactionReceipt> updateStateFromPure_AsCrosschainTransaction(
      final CrosschainContext crosschainContext) {
    final Function function =
        new Function(
            FUNC_UPDATESTATEFROMPURE,
            Arrays.<Type>asList(),
            Collections.<TypeReference<?>>emptyList());
    return executeRemoteCallCrosschainTransaction(function, crosschainContext);
  }

  public RemoteFunctionCall<TransactionReceipt> updateStateFromTxView() {
    final Function function =
        new Function(
            FUNC_UPDATESTATEFROMTXVIEW,
            Arrays.<Type>asList(),
            Collections.<TypeReference<?>>emptyList());
    return executeRemoteCallTransaction(function);
  }

  public byte[] updateStateFromTxView_AsSignedCrosschainSubordinateTransaction(
      final CrosschainContext crosschainContext) throws IOException {
    final Function function =
        new Function(
            FUNC_UPDATESTATEFROMTXVIEW,
            Arrays.<Type>asList(),
            Collections.<TypeReference<?>>emptyList());
    return createSignedSubordinateTransaction(function, crosschainContext);
  }

  public RemoteFunctionCall<TransactionReceipt> updateStateFromTxView_AsCrosschainTransaction(
      final CrosschainContext crosschainContext) {
    final Function function =
        new Function(
            FUNC_UPDATESTATEFROMTXVIEW,
            Arrays.<Type>asList(),
            Collections.<TypeReference<?>>emptyList());
    return executeRemoteCallCrosschainTransaction(function, crosschainContext);
  }

  public RemoteFunctionCall<TransactionReceipt> updateStateFromView() {
    final Function function =
        new Function(
            FUNC_UPDATESTATEFROMVIEW,
            Arrays.<Type>asList(),
            Collections.<TypeReference<?>>emptyList());
    return executeRemoteCallTransaction(function);
  }

  public byte[] updateStateFromView_AsSignedCrosschainSubordinateTransaction(
      final CrosschainContext crosschainContext) throws IOException {
    final Function function =
        new Function(
            FUNC_UPDATESTATEFROMVIEW,
            Arrays.<Type>asList(),
            Collections.<TypeReference<?>>emptyList());
    return createSignedSubordinateTransaction(function, crosschainContext);
  }

  public RemoteFunctionCall<TransactionReceipt> updateStateFromView_AsCrosschainTransaction(
      final CrosschainContext crosschainContext) {
    final Function function =
        new Function(
            FUNC_UPDATESTATEFROMVIEW,
            Arrays.<Type>asList(),
            Collections.<TypeReference<?>>emptyList());
    return executeRemoteCallCrosschainTransaction(function, crosschainContext);
  }

  @Deprecated
  public static FooCtrt load(
      String contractAddress,
      Besu besu,
      CrosschainTransactionManager crosschainTransactionManager,
      BigInteger gasPrice,
      BigInteger gasLimit) {
    return new FooCtrt(contractAddress, besu, crosschainTransactionManager, gasPrice, gasLimit);
  }

  public static FooCtrt load(
      String contractAddress,
      Besu besu,
      CrosschainTransactionManager crosschainTransactionManager,
      ContractGasProvider contractGasProvider) {
    return new FooCtrt(contractAddress, besu, crosschainTransactionManager, contractGasProvider);
  }

  public static RemoteCall<FooCtrt> deployLockable(
      Besu besu,
      CrosschainTransactionManager transactionManager,
      ContractGasProvider contractGasProvider) {
    CrosschainContext crosschainContext = null;
    return deployLockableContractRemoteCall(
        FooCtrt.class,
        besu,
        transactionManager,
        contractGasProvider,
        BINARY,
        "",
        crosschainContext);
  }

  @Deprecated
  public static RemoteCall<FooCtrt> deployLockable(
      Besu besu,
      CrosschainTransactionManager transactionManager,
      BigInteger gasPrice,
      BigInteger gasLimit) {
    CrosschainContext crosschainContext = null;
    return deployLockableContractRemoteCall(
        FooCtrt.class, besu, transactionManager, gasPrice, gasLimit, BINARY, "", crosschainContext);
  }

  public static RemoteCall<FooCtrt> deployLockable(
      Besu besu,
      CrosschainTransactionManager transactionManager,
      ContractGasProvider contractGasProvider,
      final CrosschainContext crosschainContext) {
    return deployLockableContractRemoteCall(
        FooCtrt.class,
        besu,
        transactionManager,
        contractGasProvider,
        BINARY,
        "",
        crosschainContext);
  }

  @Deprecated
  public static RemoteCall<FooCtrt> deployLockable(
      Besu besu,
      CrosschainTransactionManager transactionManager,
      BigInteger gasPrice,
      BigInteger gasLimit,
      final CrosschainContext crosschainContext) {
    return deployLockableContractRemoteCall(
        FooCtrt.class, besu, transactionManager, gasPrice, gasLimit, BINARY, "", crosschainContext);
  }
}
