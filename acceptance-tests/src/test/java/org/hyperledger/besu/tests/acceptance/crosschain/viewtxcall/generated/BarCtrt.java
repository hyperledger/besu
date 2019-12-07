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
      "608060405234801561001057600080fd5b506000600281905560038190556004819055600555610649806100346000396000f3fe608060405234801561001057600080fd5b50600436106101005760003560e01c80639de16f7411610097578063e132f82f11610066578063e132f82f146101b1578063e9f63923146101b9578063fc344e99146101c1578063febb0f7e146101c957610100565b80639de16f7414610199578063b15b10da1461010f578063b36484af146101a1578063be7003d5146101a957610100565b806380b1a7f3116100d357806380b1a7f31461015d57806384a13fba14610165578063890eba6814610189578063966aaf801461019157610100565b80632e93504614610105578063484f579b1461010f57806360d1042814610129578063641b76f714610155575b600080fd5b61010d6101d1565b005b610117610216565b60408051918252519081900360200190f35b61010d6004803603604081101561013f57600080fd5b50803590602001356001600160a01b031661021b565b610117610242565b61010d610248565b61016d61028b565b604080516001600160a01b039092168252519081900360200190f35b61011761029a565b61010d6102a0565b61010d6102e8565b61010d61032f565b61010d610372565b6101176103ba565b6101176103c0565b61010d6103c6565b61010d610409565b6000546001546040805160048152602481019091526020810180516001600160e01b03166328efb58160e21b17905261021492916001600160a01b03169061044c565b565b600190565b600091909155600180546001600160a01b0319166001600160a01b03909216919091179055565b60045481565b6000546001546040805160048152602481019091526020810180516001600160e01b0316632da6632b60e11b17905261021492916001600160a01b03169061044c565b6001546001600160a01b031681565b60025481565b6000546001546040805160048152602481019091526020810180516001600160e01b0316636880d21b60e01b1790526102e392916001600160a01b03169061051a565b600355565b6000546001546040805160048152602481019091526020810180516001600160e01b031662230c3f60e61b17905261032a92916001600160a01b03169061051a565b600255565b6000546001546040805160048152602481019091526020810180516001600160e01b0316634543882160e01b17905261021492916001600160a01b03169061044c565b6000546001546040805160048152602481019091526020810180516001600160e01b0316637c5ab21360e01b1790526103b592916001600160a01b03169061051a565b600455565b60035481565b60055481565b6000546001546040805160048152602481019091526020810180516001600160e01b0316631d8557d760e01b17905261021492916001600160a01b03169061044c565b6000546001546040805160048152602481019091526020810180516001600160e01b03166318530aaf60e31b17905261032a92916001600160a01b03169061051a565b606083838360405160200180848152602001836001600160a01b03166001600160a01b0316815260200180602001828103825283818151815260200191508051906020019080838360005b838110156104af578181015183820152602001610497565b50505050905090810190601f1680156104dc5780820380516001836020036101000a031916815260200191505b50945050505050604051602081830303815290604052905060006004825101905060008082846000600a600019f161051357600080fd5b5050505050565b6000606084848460405160200180848152602001836001600160a01b03166001600160a01b0316815260200180602001828103825283818151815260200191508051906020019080838360005b8381101561057f578181015183820152602001610567565b50505050905090810190601f1680156105ac5780820380516001836020036101000a031916815260200191505b5094505050505060405160208183030381529060405290506000600482510190506105d56105f6565b602080828486600b600019fa6105ea57600080fd5b50519695505050505050565b6040518060200160405280600190602082028038833950919291505056fea265627a7a72315820be50d90e5c7d52bcb3d12986e2468bfae2e807ee5899bed0478c3a5448c9d1d164736f6c634300050c0032";

  public static final String FUNC_BAR = "bar";

  public static final String FUNC_BARUPDATESTATE = "barUpdateState";

  public static final String FUNC_BARTP = "bartp";

  public static final String FUNC_BARTTV = "barttv";

  public static final String FUNC_BARTV = "bartv";

  public static final String FUNC_BARVP = "barvp";

  public static final String FUNC_BARVV = "barvv";

  public static final String FUNC_FLAG = "flag";

  public static final String FUNC_FOOCTRT = "fooCtrt";

  public static final String FUNC_PUREBAR = "pureBar";

  public static final String FUNC_PUREFN = "purefn";

  public static final String FUNC_SETPROPERTIES = "setProperties";

  public static final String FUNC_TTVFLAG = "ttvflag";

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

  public RemoteFunctionCall<TransactionReceipt> bartp() {
    final Function function =
        new Function(FUNC_BARTP, Arrays.<Type>asList(), Collections.<TypeReference<?>>emptyList());
    return executeRemoteCallTransaction(function);
  }

  public byte[] bartp_AsSignedCrosschainSubordinateTransaction(
      final CrosschainContext crosschainContext) throws IOException {
    final Function function =
        new Function(FUNC_BARTP, Arrays.<Type>asList(), Collections.<TypeReference<?>>emptyList());
    return createSignedSubordinateTransaction(function, crosschainContext);
  }

  public RemoteFunctionCall<TransactionReceipt> bartp_AsCrosschainTransaction(
      final CrosschainContext crosschainContext) {
    final Function function =
        new Function(FUNC_BARTP, Arrays.<Type>asList(), Collections.<TypeReference<?>>emptyList());
    return executeRemoteCallCrosschainTransaction(function, crosschainContext);
  }

  public RemoteFunctionCall<TransactionReceipt> barttv() {
    final Function function =
        new Function(FUNC_BARTTV, Arrays.<Type>asList(), Collections.<TypeReference<?>>emptyList());
    return executeRemoteCallTransaction(function);
  }

  public byte[] barttv_AsSignedCrosschainSubordinateTransaction(
      final CrosschainContext crosschainContext) throws IOException {
    final Function function =
        new Function(FUNC_BARTTV, Arrays.<Type>asList(), Collections.<TypeReference<?>>emptyList());
    return createSignedSubordinateTransaction(function, crosschainContext);
  }

  public RemoteFunctionCall<TransactionReceipt> barttv_AsCrosschainTransaction(
      final CrosschainContext crosschainContext) {
    final Function function =
        new Function(FUNC_BARTTV, Arrays.<Type>asList(), Collections.<TypeReference<?>>emptyList());
    return executeRemoteCallCrosschainTransaction(function, crosschainContext);
  }

  public RemoteFunctionCall<TransactionReceipt> bartv() {
    final Function function =
        new Function(FUNC_BARTV, Arrays.<Type>asList(), Collections.<TypeReference<?>>emptyList());
    return executeRemoteCallTransaction(function);
  }

  public byte[] bartv_AsSignedCrosschainSubordinateTransaction(
      final CrosschainContext crosschainContext) throws IOException {
    final Function function =
        new Function(FUNC_BARTV, Arrays.<Type>asList(), Collections.<TypeReference<?>>emptyList());
    return createSignedSubordinateTransaction(function, crosschainContext);
  }

  public RemoteFunctionCall<TransactionReceipt> bartv_AsCrosschainTransaction(
      final CrosschainContext crosschainContext) {
    final Function function =
        new Function(FUNC_BARTV, Arrays.<Type>asList(), Collections.<TypeReference<?>>emptyList());
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
