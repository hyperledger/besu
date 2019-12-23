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
package org.hyperledger.besu.tests.acceptance.crosschain.getinfo.generated;

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
public class Ctrt2 extends CrosschainContract {
  private static final String BINARY =
      "608060405234801561001057600080fd5b5060006002556100276001600160e01b0361002f16565b600655610083565b6000602061003b610065565b60008152610047610065565b6020808285856078600019fa61005c57600080fd5b50519392505050565b60405180602001604052806001906020820280388339509192915050565b6104d1806100926000396000f3fe608060405234801561001057600080fd5b50600436106100cf5760003560e01c806379fe365f1161008c578063e669439b11610066578063e669439b1461014c578063e6fdb7ea14610172578063f41c38a11461017a578063f4ac469314610182576100cf565b806379fe365f14610134578063a7ded5351461013c578063cd568d1f14610144576100cf565b806301605925146100d45780630a56e1a0146100ee5780633e2ae2f81461011257806346c9da561461011a578063484f579b1461012257806355443d701461012a575b600080fd5b6100dc61019f565b60408051918252519081900360200190f35b6100f66101a5565b604080516001600160a01b039092168252519081900360200190f35b6100dc6101b4565b6100dc6101ba565b6100dc6101c0565b6101326101cf565b005b6100dc6102a6565b6100dc6102ac565b6100dc6102b2565b6101326004803603602081101561016257600080fd5b50356001600160a01b03166102b8565b6100f66102da565b6100dc6102e9565b6101326004803603602081101561019857600080fd5b50356102ef565b60085481565b600a546001600160a01b031681565b60045481565b60055481565b60006101ca6102f4565b905090565b6000546001546040805160048152602481019091526020810180516001600160e01b0316631494356b60e31b17905261021292916001600160a01b03169061032a565b61021a6103f8565b6003556102256102f4565b600555610230610404565b60075561023b610410565b60085561024661041c565b600455610251610428565b60095561025c610434565b600a80546001600160a01b0319166001600160a01b0392909216919091179055610284610440565b600b80546001600160a01b0319166001600160a01b0392909216919091179055565b60075481565b60035481565b60095481565b600180546001600160a01b0319166001600160a01b0392909216919091179055565b600b546001600160a01b031681565b60065481565b600055565b6000602061030061047e565b6000815261030c61047e565b6020808285856078600019fa61032157600080fd5b50519392505050565b606083838360405160200180848152602001836001600160a01b03166001600160a01b0316815260200180602001828103825283818151815260200191508051906020019080838360005b8381101561038d578181015183820152602001610375565b50505050905090810190601f1680156103ba5780820380516001836020036101000a031916815260200191505b50945050505050604051602081830303815290604052905060006004825101905060008082846000600a600019f16103f157600080fd5b5050505050565b60006101ca6001610448565b60006101ca6002610448565b60006101ca6004610448565b60006101ca6005610448565b60006101ca6007610448565b60006101ca6003610448565b60006101ca60065b6000602061045461047e565b83815261045f61047e565b6020808285856078600019fa61047457600080fd5b5051949350505050565b6040518060200160405280600190602082028038833950919291505056fea265627a7a723158205c9b6560f56acbaee819d88bf8c4d64080e532f4fa0be6cf0ded47deebd8be7364736f6c634300050c0032";

  public static final String FUNC_CALLCTRT3 = "callCtrt3";

  public static final String FUNC_CONSTXTYPE = "consTxType";

  public static final String FUNC_COORDCHAINID = "coordChainId";

  public static final String FUNC_COORDCTRTADDR = "coordCtrtAddr";

  public static final String FUNC_FROMADDR = "fromAddr";

  public static final String FUNC_FROMCHAINID = "fromChainId";

  public static final String FUNC_MYCHAINID = "myChainId";

  public static final String FUNC_MYTXTYPE = "myTxType";

  public static final String FUNC_ORIGCHAINID = "origChainId";

  public static final String FUNC_SETCTRT3 = "setCtrt3";

  public static final String FUNC_SETCTRT3CHAINID = "setCtrt3ChainId";

  public static final String FUNC_TXID = "txId";

  public static final String FUNC_VIEWFN = "viewfn";

  @Deprecated
  protected Ctrt2(
      String contractAddress,
      Besu besu,
      CrosschainTransactionManager crosschainTransactionManager,
      BigInteger gasPrice,
      BigInteger gasLimit) {
    super(BINARY, contractAddress, besu, crosschainTransactionManager, gasPrice, gasLimit);
  }

  protected Ctrt2(
      String contractAddress,
      Besu besu,
      CrosschainTransactionManager crosschainTransactionManager,
      ContractGasProvider contractGasProvider) {
    super(BINARY, contractAddress, besu, crosschainTransactionManager, contractGasProvider);
  }

  public RemoteFunctionCall<TransactionReceipt> callCtrt3() {
    final Function function =
        new Function(
            FUNC_CALLCTRT3, Arrays.<Type>asList(), Collections.<TypeReference<?>>emptyList());
    return executeRemoteCallTransaction(function);
  }

  public byte[] callCtrt3_AsSignedCrosschainSubordinateTransaction(
      final CrosschainContext crosschainContext) throws IOException {
    final Function function =
        new Function(
            FUNC_CALLCTRT3, Arrays.<Type>asList(), Collections.<TypeReference<?>>emptyList());
    return createSignedSubordinateTransaction(function, crosschainContext);
  }

  public RemoteFunctionCall<TransactionReceipt> callCtrt3_AsCrosschainTransaction(
      final CrosschainContext crosschainContext) {
    final Function function =
        new Function(
            FUNC_CALLCTRT3, Arrays.<Type>asList(), Collections.<TypeReference<?>>emptyList());
    return executeRemoteCallCrosschainTransaction(function, crosschainContext);
  }

  public RemoteFunctionCall<BigInteger> consTxType() {
    final Function function =
        new Function(
            FUNC_CONSTXTYPE,
            Arrays.<Type>asList(),
            Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
    return executeRemoteCallSingleValueReturn(function, BigInteger.class);
  }

  public byte[] consTxType_AsSignedCrosschainSubordinateView(
      final CrosschainContext crosschainContext) throws IOException {
    final Function function =
        new Function(
            FUNC_CONSTXTYPE,
            Arrays.<Type>asList(),
            Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
    return createSignedSubordinateView(function, crosschainContext);
  }

  public RemoteFunctionCall<BigInteger> coordChainId() {
    final Function function =
        new Function(
            FUNC_COORDCHAINID,
            Arrays.<Type>asList(),
            Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
    return executeRemoteCallSingleValueReturn(function, BigInteger.class);
  }

  public byte[] coordChainId_AsSignedCrosschainSubordinateView(
      final CrosschainContext crosschainContext) throws IOException {
    final Function function =
        new Function(
            FUNC_COORDCHAINID,
            Arrays.<Type>asList(),
            Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
    return createSignedSubordinateView(function, crosschainContext);
  }

  public RemoteFunctionCall<String> coordCtrtAddr() {
    final Function function =
        new Function(
            FUNC_COORDCTRTADDR,
            Arrays.<Type>asList(),
            Arrays.<TypeReference<?>>asList(new TypeReference<Address>() {}));
    return executeRemoteCallSingleValueReturn(function, String.class);
  }

  public byte[] coordCtrtAddr_AsSignedCrosschainSubordinateView(
      final CrosschainContext crosschainContext) throws IOException {
    final Function function =
        new Function(
            FUNC_COORDCTRTADDR,
            Arrays.<Type>asList(),
            Arrays.<TypeReference<?>>asList(new TypeReference<Address>() {}));
    return createSignedSubordinateView(function, crosschainContext);
  }

  public RemoteFunctionCall<String> fromAddr() {
    final Function function =
        new Function(
            FUNC_FROMADDR,
            Arrays.<Type>asList(),
            Arrays.<TypeReference<?>>asList(new TypeReference<Address>() {}));
    return executeRemoteCallSingleValueReturn(function, String.class);
  }

  public byte[] fromAddr_AsSignedCrosschainSubordinateView(
      final CrosschainContext crosschainContext) throws IOException {
    final Function function =
        new Function(
            FUNC_FROMADDR,
            Arrays.<Type>asList(),
            Arrays.<TypeReference<?>>asList(new TypeReference<Address>() {}));
    return createSignedSubordinateView(function, crosschainContext);
  }

  public RemoteFunctionCall<BigInteger> fromChainId() {
    final Function function =
        new Function(
            FUNC_FROMCHAINID,
            Arrays.<Type>asList(),
            Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
    return executeRemoteCallSingleValueReturn(function, BigInteger.class);
  }

  public byte[] fromChainId_AsSignedCrosschainSubordinateView(
      final CrosschainContext crosschainContext) throws IOException {
    final Function function =
        new Function(
            FUNC_FROMCHAINID,
            Arrays.<Type>asList(),
            Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
    return createSignedSubordinateView(function, crosschainContext);
  }

  public RemoteFunctionCall<BigInteger> myChainId() {
    final Function function =
        new Function(
            FUNC_MYCHAINID,
            Arrays.<Type>asList(),
            Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
    return executeRemoteCallSingleValueReturn(function, BigInteger.class);
  }

  public byte[] myChainId_AsSignedCrosschainSubordinateView(
      final CrosschainContext crosschainContext) throws IOException {
    final Function function =
        new Function(
            FUNC_MYCHAINID,
            Arrays.<Type>asList(),
            Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
    return createSignedSubordinateView(function, crosschainContext);
  }

  public RemoteFunctionCall<BigInteger> myTxType() {
    final Function function =
        new Function(
            FUNC_MYTXTYPE,
            Arrays.<Type>asList(),
            Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
    return executeRemoteCallSingleValueReturn(function, BigInteger.class);
  }

  public byte[] myTxType_AsSignedCrosschainSubordinateView(
      final CrosschainContext crosschainContext) throws IOException {
    final Function function =
        new Function(
            FUNC_MYTXTYPE,
            Arrays.<Type>asList(),
            Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
    return createSignedSubordinateView(function, crosschainContext);
  }

  public RemoteFunctionCall<BigInteger> origChainId() {
    final Function function =
        new Function(
            FUNC_ORIGCHAINID,
            Arrays.<Type>asList(),
            Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
    return executeRemoteCallSingleValueReturn(function, BigInteger.class);
  }

  public byte[] origChainId_AsSignedCrosschainSubordinateView(
      final CrosschainContext crosschainContext) throws IOException {
    final Function function =
        new Function(
            FUNC_ORIGCHAINID,
            Arrays.<Type>asList(),
            Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
    return createSignedSubordinateView(function, crosschainContext);
  }

  public RemoteFunctionCall<TransactionReceipt> setCtrt3(String _ctrt3Addr) {
    final Function function =
        new Function(
            FUNC_SETCTRT3,
            Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(160, _ctrt3Addr)),
            Collections.<TypeReference<?>>emptyList());
    return executeRemoteCallTransaction(function);
  }

  public byte[] setCtrt3_AsSignedCrosschainSubordinateTransaction(
      String _ctrt3Addr, final CrosschainContext crosschainContext) throws IOException {
    final Function function =
        new Function(
            FUNC_SETCTRT3,
            Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(160, _ctrt3Addr)),
            Collections.<TypeReference<?>>emptyList());
    return createSignedSubordinateTransaction(function, crosschainContext);
  }

  public RemoteFunctionCall<TransactionReceipt> setCtrt3_AsCrosschainTransaction(
      String _ctrt3Addr, final CrosschainContext crosschainContext) {
    final Function function =
        new Function(
            FUNC_SETCTRT3,
            Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(160, _ctrt3Addr)),
            Collections.<TypeReference<?>>emptyList());
    return executeRemoteCallCrosschainTransaction(function, crosschainContext);
  }

  public RemoteFunctionCall<TransactionReceipt> setCtrt3ChainId(BigInteger _ctrt3ChainId) {
    final Function function =
        new Function(
            FUNC_SETCTRT3CHAINID,
            Arrays.<Type>asList(new org.web3j.abi.datatypes.generated.Uint256(_ctrt3ChainId)),
            Collections.<TypeReference<?>>emptyList());
    return executeRemoteCallTransaction(function);
  }

  public byte[] setCtrt3ChainId_AsSignedCrosschainSubordinateTransaction(
      BigInteger _ctrt3ChainId, final CrosschainContext crosschainContext) throws IOException {
    final Function function =
        new Function(
            FUNC_SETCTRT3CHAINID,
            Arrays.<Type>asList(new org.web3j.abi.datatypes.generated.Uint256(_ctrt3ChainId)),
            Collections.<TypeReference<?>>emptyList());
    return createSignedSubordinateTransaction(function, crosschainContext);
  }

  public RemoteFunctionCall<TransactionReceipt> setCtrt3ChainId_AsCrosschainTransaction(
      BigInteger _ctrt3ChainId, final CrosschainContext crosschainContext) {
    final Function function =
        new Function(
            FUNC_SETCTRT3CHAINID,
            Arrays.<Type>asList(new org.web3j.abi.datatypes.generated.Uint256(_ctrt3ChainId)),
            Collections.<TypeReference<?>>emptyList());
    return executeRemoteCallCrosschainTransaction(function, crosschainContext);
  }

  public RemoteFunctionCall<BigInteger> txId() {
    final Function function =
        new Function(
            FUNC_TXID,
            Arrays.<Type>asList(),
            Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
    return executeRemoteCallSingleValueReturn(function, BigInteger.class);
  }

  public byte[] txId_AsSignedCrosschainSubordinateView(final CrosschainContext crosschainContext)
      throws IOException {
    final Function function =
        new Function(
            FUNC_TXID,
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

  @Deprecated
  public static Ctrt2 load(
      String contractAddress,
      Besu besu,
      CrosschainTransactionManager crosschainTransactionManager,
      BigInteger gasPrice,
      BigInteger gasLimit) {
    return new Ctrt2(contractAddress, besu, crosschainTransactionManager, gasPrice, gasLimit);
  }

  public static Ctrt2 load(
      String contractAddress,
      Besu besu,
      CrosschainTransactionManager crosschainTransactionManager,
      ContractGasProvider contractGasProvider) {
    return new Ctrt2(contractAddress, besu, crosschainTransactionManager, contractGasProvider);
  }

  public static RemoteCall<Ctrt2> deployLockable(
      Besu besu,
      CrosschainTransactionManager transactionManager,
      ContractGasProvider contractGasProvider) {
    CrosschainContext crosschainContext = null;
    return deployLockableContractRemoteCall(
        Ctrt2.class, besu, transactionManager, contractGasProvider, BINARY, "", crosschainContext);
  }

  @Deprecated
  public static RemoteCall<Ctrt2> deployLockable(
      Besu besu,
      CrosschainTransactionManager transactionManager,
      BigInteger gasPrice,
      BigInteger gasLimit) {
    CrosschainContext crosschainContext = null;
    return deployLockableContractRemoteCall(
        Ctrt2.class, besu, transactionManager, gasPrice, gasLimit, BINARY, "", crosschainContext);
  }

  public static RemoteCall<Ctrt2> deployLockable(
      Besu besu,
      CrosschainTransactionManager transactionManager,
      ContractGasProvider contractGasProvider,
      final CrosschainContext crosschainContext) {
    return deployLockableContractRemoteCall(
        Ctrt2.class, besu, transactionManager, contractGasProvider, BINARY, "", crosschainContext);
  }

  @Deprecated
  public static RemoteCall<Ctrt2> deployLockable(
      Besu besu,
      CrosschainTransactionManager transactionManager,
      BigInteger gasPrice,
      BigInteger gasLimit,
      final CrosschainContext crosschainContext) {
    return deployLockableContractRemoteCall(
        Ctrt2.class, besu, transactionManager, gasPrice, gasLimit, BINARY, "", crosschainContext);
  }
}
