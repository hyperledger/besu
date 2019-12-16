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
      "608060405234801561001057600080fd5b50600060048190556005819055600681905560078190556008556107c5806100396000396000f3fe608060405234801561001057600080fd5b506004361061014d5760003560e01c8063966aaf80116100c3578063d2b1ad5c1161007c578063d2b1ad5c1461024a578063db9785b514610252578063e132f82f1461025a578063e9f6392314610262578063fc344e991461026a578063febb0f7e146102725761014d565b8063966aaf80146102225780639de16f741461022a578063b15b10da146101aa578063b36484af14610232578063be7003d51461023a578063c50cee14146102425761014d565b806356ed192b1161011557806356ed192b146101b257806360d10428146101ba578063641b76f7146101e657806380b1a7f3146101ee57806384a13fba146101f6578063890eba681461021a5761014d565b80631c431cab14610152578063276728b91461016c5780632e9350461461019a57806336447de7146101a2578063484f579b146101aa575b600080fd5b61015a61027a565b60408051918252519081900360200190f35b6101986004803603604081101561018257600080fd5b50803590602001356001600160a01b0316610280565b005b6101986102a7565b61015a6102ec565b61015a6102f2565b6101986102f7565b610198600480360360408110156101d057600080fd5b50803590602001356001600160a01b031661033a565b61015a610361565b610198610367565b6101fe6103aa565b604080516001600160a01b039092168252519081900360200190f35b61015a6103b9565b6101986103bf565b610198610407565b61019861044e565b610198610491565b6101986104d9565b6101fe610521565b61015a610530565b61015a610536565b61015a61053c565b610198610542565b610198610585565b60085481565b600191909155600380546001600160a01b0319166001600160a01b03909216919091179055565b6000546002546040805160048152602481019091526020810180516001600160e01b03166328efb58160e21b1790526102ea92916001600160a01b0316906105c8565b565b60005481565b600190565b6001546003546040805160048152602481019091526020810180516001600160e01b0316631d8557d760e01b1790526102ea92916001600160a01b0316906105c8565b600091909155600280546001600160a01b0319166001600160a01b03909216919091179055565b60065481565b6000546002546040805160048152602481019091526020810180516001600160e01b0316632da6632b60e11b1790526102ea92916001600160a01b0316906105c8565b6002546001600160a01b031681565b60045481565b6000546002546040805160048152602481019091526020810180516001600160e01b0316636880d21b60e01b17905261040292916001600160a01b031690610696565b600555565b6000546002546040805160048152602481019091526020810180516001600160e01b031662230c3f60e61b17905261044992916001600160a01b031690610696565b600455565b6000546002546040805160048152602481019091526020810180516001600160e01b0316634543882160e01b1790526102ea92916001600160a01b0316906105c8565b6000546002546040805160048152602481019091526020810180516001600160e01b0316637c5ab21360e01b1790526104d492916001600160a01b031690610696565b600655565b6001546003546040805160048152602481019091526020810180516001600160e01b031663484f579b60e01b17905261051c92916001600160a01b031690610696565b600855565b6003546001600160a01b031681565b60015481565b60055481565b60075481565b6000546002546040805160048152602481019091526020810180516001600160e01b0316631d8557d760e01b1790526102ea92916001600160a01b0316906105c8565b6000546002546040805160048152602481019091526020810180516001600160e01b03166318530aaf60e31b17905261044992916001600160a01b031690610696565b606083838360405160200180848152602001836001600160a01b03166001600160a01b0316815260200180602001828103825283818151815260200191508051906020019080838360005b8381101561062b578181015183820152602001610613565b50505050905090810190601f1680156106585780820380516001836020036101000a031916815260200191505b50945050505050604051602081830303815290604052905060006004825101905060008082846000600a600019f161068f57600080fd5b5050505050565b6000606084848460405160200180848152602001836001600160a01b03166001600160a01b0316815260200180602001828103825283818151815260200191508051906020019080838360005b838110156106fb5781810151838201526020016106e3565b50505050905090810190601f1680156107285780820380516001836020036101000a031916815260200191505b509450505050506040516020818303038152906040529050600060048251019050610751610772565b602080828486600b600019fa61076657600080fd5b50519695505050505050565b6040518060200160405280600190602082028038833950919291505056fea265627a7a723158207a5ae9c646889749d8ede17fea16fbe3e6c3e3bdfe93dda7abb53c62e406de0a64736f6c634300050c0032";

  public static final String FUNC_BAR = "bar";

  public static final String FUNC_BARUPDATESTATE = "barUpdateState";

  public static final String FUNC_BARTP = "bartp";

  public static final String FUNC_BARTTV = "barttv";

  public static final String FUNC_BARTV = "bartv";

  public static final String FUNC_BARVP = "barvp";

  public static final String FUNC_BARVV = "barvv";

  public static final String FUNC_CALLNONLOCKABLECTRTTX = "callNonLockableCtrtTx";

  public static final String FUNC_CALLNONLOCKABLECTRTVIEW = "callNonLockableCtrtView";

  public static final String FUNC_FLAG = "flag";

  public static final String FUNC_FOOCHAINID = "fooChainId";

  public static final String FUNC_FOOCTRT = "fooCtrt";

  public static final String FUNC_NONLOCKABLECTRT = "nonLockableCtrt";

  public static final String FUNC_NONLOCKABLECTRTCHAINID = "nonLockableCtrtChainId";

  public static final String FUNC_NONLOCKABLEVIEWFLAG = "nonLockableViewFlag";

  public static final String FUNC_PUREBAR = "pureBar";

  public static final String FUNC_PUREFN = "purefn";

  public static final String FUNC_SETPROPERTIES = "setProperties";

  public static final String FUNC_SETPROPERTIESFORNONLOCKABLECTRT =
      "setPropertiesForNonLockableCtrt";

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

  public RemoteFunctionCall<TransactionReceipt> callNonLockableCtrtTx() {
    final Function function =
        new Function(
            FUNC_CALLNONLOCKABLECTRTTX,
            Arrays.<Type>asList(),
            Collections.<TypeReference<?>>emptyList());
    return executeRemoteCallTransaction(function);
  }

  public byte[] callNonLockableCtrtTx_AsSignedCrosschainSubordinateTransaction(
      final CrosschainContext crosschainContext) throws IOException {
    final Function function =
        new Function(
            FUNC_CALLNONLOCKABLECTRTTX,
            Arrays.<Type>asList(),
            Collections.<TypeReference<?>>emptyList());
    return createSignedSubordinateTransaction(function, crosschainContext);
  }

  public RemoteFunctionCall<TransactionReceipt> callNonLockableCtrtTx_AsCrosschainTransaction(
      final CrosschainContext crosschainContext) {
    final Function function =
        new Function(
            FUNC_CALLNONLOCKABLECTRTTX,
            Arrays.<Type>asList(),
            Collections.<TypeReference<?>>emptyList());
    return executeRemoteCallCrosschainTransaction(function, crosschainContext);
  }

  public RemoteFunctionCall<TransactionReceipt> callNonLockableCtrtView() {
    final Function function =
        new Function(
            FUNC_CALLNONLOCKABLECTRTVIEW,
            Arrays.<Type>asList(),
            Collections.<TypeReference<?>>emptyList());
    return executeRemoteCallTransaction(function);
  }

  public byte[] callNonLockableCtrtView_AsSignedCrosschainSubordinateTransaction(
      final CrosschainContext crosschainContext) throws IOException {
    final Function function =
        new Function(
            FUNC_CALLNONLOCKABLECTRTVIEW,
            Arrays.<Type>asList(),
            Collections.<TypeReference<?>>emptyList());
    return createSignedSubordinateTransaction(function, crosschainContext);
  }

  public RemoteFunctionCall<TransactionReceipt> callNonLockableCtrtView_AsCrosschainTransaction(
      final CrosschainContext crosschainContext) {
    final Function function =
        new Function(
            FUNC_CALLNONLOCKABLECTRTVIEW,
            Arrays.<Type>asList(),
            Collections.<TypeReference<?>>emptyList());
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

  public RemoteFunctionCall<BigInteger> fooChainId() {
    final Function function =
        new Function(
            FUNC_FOOCHAINID,
            Arrays.<Type>asList(),
            Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
    return executeRemoteCallSingleValueReturn(function, BigInteger.class);
  }

  public byte[] fooChainId_AsSignedCrosschainSubordinateView(
      final CrosschainContext crosschainContext) throws IOException {
    final Function function =
        new Function(
            FUNC_FOOCHAINID,
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

  public RemoteFunctionCall<String> nonLockableCtrt() {
    final Function function =
        new Function(
            FUNC_NONLOCKABLECTRT,
            Arrays.<Type>asList(),
            Arrays.<TypeReference<?>>asList(new TypeReference<Address>() {}));
    return executeRemoteCallSingleValueReturn(function, String.class);
  }

  public byte[] nonLockableCtrt_AsSignedCrosschainSubordinateView(
      final CrosschainContext crosschainContext) throws IOException {
    final Function function =
        new Function(
            FUNC_NONLOCKABLECTRT,
            Arrays.<Type>asList(),
            Arrays.<TypeReference<?>>asList(new TypeReference<Address>() {}));
    return createSignedSubordinateView(function, crosschainContext);
  }

  public RemoteFunctionCall<BigInteger> nonLockableCtrtChainId() {
    final Function function =
        new Function(
            FUNC_NONLOCKABLECTRTCHAINID,
            Arrays.<Type>asList(),
            Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
    return executeRemoteCallSingleValueReturn(function, BigInteger.class);
  }

  public byte[] nonLockableCtrtChainId_AsSignedCrosschainSubordinateView(
      final CrosschainContext crosschainContext) throws IOException {
    final Function function =
        new Function(
            FUNC_NONLOCKABLECTRTCHAINID,
            Arrays.<Type>asList(),
            Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
    return createSignedSubordinateView(function, crosschainContext);
  }

  public RemoteFunctionCall<BigInteger> nonLockableViewFlag() {
    final Function function =
        new Function(
            FUNC_NONLOCKABLEVIEWFLAG,
            Arrays.<Type>asList(),
            Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
    return executeRemoteCallSingleValueReturn(function, BigInteger.class);
  }

  public byte[] nonLockableViewFlag_AsSignedCrosschainSubordinateView(
      final CrosschainContext crosschainContext) throws IOException {
    final Function function =
        new Function(
            FUNC_NONLOCKABLEVIEWFLAG,
            Arrays.<Type>asList(),
            Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
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

  public RemoteFunctionCall<TransactionReceipt> setPropertiesForNonLockableCtrt(
      BigInteger _nonLockableCtrtChainId, String _nonLockableCtrtAddr) {
    final Function function =
        new Function(
            FUNC_SETPROPERTIESFORNONLOCKABLECTRT,
            Arrays.<Type>asList(
                new org.web3j.abi.datatypes.generated.Uint256(_nonLockableCtrtChainId),
                new org.web3j.abi.datatypes.Address(160, _nonLockableCtrtAddr)),
            Collections.<TypeReference<?>>emptyList());
    return executeRemoteCallTransaction(function);
  }

  public byte[] setPropertiesForNonLockableCtrt_AsSignedCrosschainSubordinateTransaction(
      BigInteger _nonLockableCtrtChainId,
      String _nonLockableCtrtAddr,
      final CrosschainContext crosschainContext)
      throws IOException {
    final Function function =
        new Function(
            FUNC_SETPROPERTIESFORNONLOCKABLECTRT,
            Arrays.<Type>asList(
                new org.web3j.abi.datatypes.generated.Uint256(_nonLockableCtrtChainId),
                new org.web3j.abi.datatypes.Address(160, _nonLockableCtrtAddr)),
            Collections.<TypeReference<?>>emptyList());
    return createSignedSubordinateTransaction(function, crosschainContext);
  }

  public RemoteFunctionCall<TransactionReceipt>
      setPropertiesForNonLockableCtrt_AsCrosschainTransaction(
          BigInteger _nonLockableCtrtChainId,
          String _nonLockableCtrtAddr,
          final CrosschainContext crosschainContext) {
    final Function function =
        new Function(
            FUNC_SETPROPERTIESFORNONLOCKABLECTRT,
            Arrays.<Type>asList(
                new org.web3j.abi.datatypes.generated.Uint256(_nonLockableCtrtChainId),
                new org.web3j.abi.datatypes.Address(160, _nonLockableCtrtAddr)),
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
