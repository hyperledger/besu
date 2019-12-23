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
public class Ctrt1 extends CrosschainContract {
  private static final String BINARY =
      "608060405234801561001057600080fd5b506100226001600160e01b0361002a16565b60085561007e565b60006020610036610060565b60008152610042610060565b6020808285856078600019fa61005757600080fd5b50519392505050565b60405180602001604052806001906020820280388339509192915050565b61064a8061008d6000396000f3fe608060405234801561001057600080fd5b50600436106100f55760003560e01c80637bf8febd11610097578063bc7fb65111610066578063bc7fb65114610197578063d34675aa1461019f578063e6fdb7ea146101c5578063f41c38a1146101cd576100f5565b80637bf8febd146101775780637e3907181461017f5780639f8d7a0914610187578063a7ded5351461018f576100f5565b80633fba5162116100d35780633fba51621461014057806346c9da561461015f5780634873a4111461016757806379fe365f1461016f576100f5565b806301605925146100fa5780630a56e1a0146101145780633e2ae2f814610138575b600080fd5b6101026101d5565b60408051918252519081900360200190f35b61011c6101db565b604080516001600160a01b039092168252519081900360200190f35b6101026101ea565b61015d6004803603602081101561015657600080fd5b50356101f0565b005b61010261020e565b610102610214565b61010261021a565b61015d610220565b61010261033d565b610102610343565b610102610349565b61010261034f565b61015d600480360360208110156101b557600080fd5b50356001600160a01b0316610355565b61011c610377565b610102610386565b60065481565b600c546001600160a01b031681565b60055481565b60008190556101fd61038c565b600b556102086103c2565b60075550565b60095481565b600b5481565b60035481565b6000546001546040805160048152602481019091526020810180516001600160e01b031663055443d760e41b17905261026392916001600160a01b0316906103d3565b6000546001546040805160048152602481019091526020810180516001600160e01b031663484f579b60e01b1790526102a692916001600160a01b0316906104a1565b600a556102b16103c2565b6002556102bc61057d565b6003556102c7610589565b600c80546001600160a01b0319166001600160a01b03929092169190911790556102ef61038c565b6009556102fa610595565b6004556103056105a1565b600d80546001600160a01b0319166001600160a01b039290921691909117905561032d6105ad565b6005556103386105b9565b600655565b60075481565b600a5481565b60025481565b60045481565b600180546001600160a01b0319166001600160a01b0392909216919091179055565b600d546001600160a01b031681565b60085481565b600060206103986105f7565b600081526103a46105f7565b6020808285856078600019fa6103b957600080fd5b50519392505050565b60006103ce60016105c1565b905090565b606083838360405160200180848152602001836001600160a01b03166001600160a01b0316815260200180602001828103825283818151815260200191508051906020019080838360005b8381101561043657818101518382015260200161041e565b50505050905090810190601f1680156104635780820380516001836020036101000a031916815260200191505b50945050505050604051602081830303815290604052905060006004825101905060008082846000600a600019f161049a57600080fd5b5050505050565b6000606084848460405160200180848152602001836001600160a01b03166001600160a01b0316815260200180602001828103825283818151815260200191508051906020019080838360005b838110156105065781810151838201526020016104ee565b50505050905090810190601f1680156105335780820380516001836020036101000a031916815260200191505b50945050505050604051602081830303815290604052905060006004825101905061055c6105f7565b602080828486600b600019fa61057157600080fd5b50519695505050505050565b60006103ce60026105c1565b60006103ce60036105c1565b60006103ce60076105c1565b60006103ce60066105c1565b60006103ce60056105c1565b60006103ce60045b600060206105cd6105f7565b8381526105d86105f7565b6020808285856078600019fa6105ed57600080fd5b5051949350505050565b6040518060200160405280600190602082028038833950919291505056fea265627a7a72315820fd2741e4e973b4298d198ac84e97023c4acddd273d9a2f4c40dd672118e2311264736f6c634300050c0032";

  public static final String FUNC_CALLCTRT2 = "callCtrt2";

  public static final String FUNC_CONSTXTYPE = "consTxType";

  public static final String FUNC_COORDCHAINID = "coordChainId";

  public static final String FUNC_COORDCTRTADDR = "coordCtrtAddr";

  public static final String FUNC_FROMADDR = "fromAddr";

  public static final String FUNC_FROMCHAINID = "fromChainId";

  public static final String FUNC_MYCHAINID = "myChainId";

  public static final String FUNC_MYTXID = "myTxId";

  public static final String FUNC_MYTXTYPE = "myTxType";

  public static final String FUNC_NONCROSSCHAINID = "nonCrossChainId";

  public static final String FUNC_ORIGCHAINID = "origChainId";

  public static final String FUNC_SETCTRT2 = "setCtrt2";

  public static final String FUNC_SETCTRT2CHAINID = "setCtrt2ChainId";

  public static final String FUNC_TESTTXTYPE = "testTxType";

  public static final String FUNC_VIEWTXTYPE = "viewTxType";

  @Deprecated
  protected Ctrt1(
      String contractAddress,
      Besu besu,
      CrosschainTransactionManager crosschainTransactionManager,
      BigInteger gasPrice,
      BigInteger gasLimit) {
    super(BINARY, contractAddress, besu, crosschainTransactionManager, gasPrice, gasLimit);
  }

  protected Ctrt1(
      String contractAddress,
      Besu besu,
      CrosschainTransactionManager crosschainTransactionManager,
      ContractGasProvider contractGasProvider) {
    super(BINARY, contractAddress, besu, crosschainTransactionManager, contractGasProvider);
  }

  public RemoteFunctionCall<TransactionReceipt> callCtrt2() {
    final Function function =
        new Function(
            FUNC_CALLCTRT2, Arrays.<Type>asList(), Collections.<TypeReference<?>>emptyList());
    return executeRemoteCallTransaction(function);
  }

  public byte[] callCtrt2_AsSignedCrosschainSubordinateTransaction(
      final CrosschainContext crosschainContext) throws IOException {
    final Function function =
        new Function(
            FUNC_CALLCTRT2, Arrays.<Type>asList(), Collections.<TypeReference<?>>emptyList());
    return createSignedSubordinateTransaction(function, crosschainContext);
  }

  public RemoteFunctionCall<TransactionReceipt> callCtrt2_AsCrosschainTransaction(
      final CrosschainContext crosschainContext) {
    final Function function =
        new Function(
            FUNC_CALLCTRT2, Arrays.<Type>asList(), Collections.<TypeReference<?>>emptyList());
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

  public RemoteFunctionCall<BigInteger> myTxId() {
    final Function function =
        new Function(
            FUNC_MYTXID,
            Arrays.<Type>asList(),
            Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
    return executeRemoteCallSingleValueReturn(function, BigInteger.class);
  }

  public byte[] myTxId_AsSignedCrosschainSubordinateView(final CrosschainContext crosschainContext)
      throws IOException {
    final Function function =
        new Function(
            FUNC_MYTXID,
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

  public RemoteFunctionCall<BigInteger> nonCrossChainId() {
    final Function function =
        new Function(
            FUNC_NONCROSSCHAINID,
            Arrays.<Type>asList(),
            Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
    return executeRemoteCallSingleValueReturn(function, BigInteger.class);
  }

  public byte[] nonCrossChainId_AsSignedCrosschainSubordinateView(
      final CrosschainContext crosschainContext) throws IOException {
    final Function function =
        new Function(
            FUNC_NONCROSSCHAINID,
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

  public RemoteFunctionCall<TransactionReceipt> setCtrt2(String _ctrt2Addr) {
    final Function function =
        new Function(
            FUNC_SETCTRT2,
            Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(160, _ctrt2Addr)),
            Collections.<TypeReference<?>>emptyList());
    return executeRemoteCallTransaction(function);
  }

  public byte[] setCtrt2_AsSignedCrosschainSubordinateTransaction(
      String _ctrt2Addr, final CrosschainContext crosschainContext) throws IOException {
    final Function function =
        new Function(
            FUNC_SETCTRT2,
            Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(160, _ctrt2Addr)),
            Collections.<TypeReference<?>>emptyList());
    return createSignedSubordinateTransaction(function, crosschainContext);
  }

  public RemoteFunctionCall<TransactionReceipt> setCtrt2_AsCrosschainTransaction(
      String _ctrt2Addr, final CrosschainContext crosschainContext) {
    final Function function =
        new Function(
            FUNC_SETCTRT2,
            Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(160, _ctrt2Addr)),
            Collections.<TypeReference<?>>emptyList());
    return executeRemoteCallCrosschainTransaction(function, crosschainContext);
  }

  public RemoteFunctionCall<TransactionReceipt> setCtrt2ChainId(BigInteger _ctrt2ChainId) {
    final Function function =
        new Function(
            FUNC_SETCTRT2CHAINID,
            Arrays.<Type>asList(new org.web3j.abi.datatypes.generated.Uint256(_ctrt2ChainId)),
            Collections.<TypeReference<?>>emptyList());
    return executeRemoteCallTransaction(function);
  }

  public byte[] setCtrt2ChainId_AsSignedCrosschainSubordinateTransaction(
      BigInteger _ctrt2ChainId, final CrosschainContext crosschainContext) throws IOException {
    final Function function =
        new Function(
            FUNC_SETCTRT2CHAINID,
            Arrays.<Type>asList(new org.web3j.abi.datatypes.generated.Uint256(_ctrt2ChainId)),
            Collections.<TypeReference<?>>emptyList());
    return createSignedSubordinateTransaction(function, crosschainContext);
  }

  public RemoteFunctionCall<TransactionReceipt> setCtrt2ChainId_AsCrosschainTransaction(
      BigInteger _ctrt2ChainId, final CrosschainContext crosschainContext) {
    final Function function =
        new Function(
            FUNC_SETCTRT2CHAINID,
            Arrays.<Type>asList(new org.web3j.abi.datatypes.generated.Uint256(_ctrt2ChainId)),
            Collections.<TypeReference<?>>emptyList());
    return executeRemoteCallCrosschainTransaction(function, crosschainContext);
  }

  public RemoteFunctionCall<BigInteger> testTxType() {
    final Function function =
        new Function(
            FUNC_TESTTXTYPE,
            Arrays.<Type>asList(),
            Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
    return executeRemoteCallSingleValueReturn(function, BigInteger.class);
  }

  public byte[] testTxType_AsSignedCrosschainSubordinateView(
      final CrosschainContext crosschainContext) throws IOException {
    final Function function =
        new Function(
            FUNC_TESTTXTYPE,
            Arrays.<Type>asList(),
            Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
    return createSignedSubordinateView(function, crosschainContext);
  }

  public RemoteFunctionCall<BigInteger> viewTxType() {
    final Function function =
        new Function(
            FUNC_VIEWTXTYPE,
            Arrays.<Type>asList(),
            Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
    return executeRemoteCallSingleValueReturn(function, BigInteger.class);
  }

  public byte[] viewTxType_AsSignedCrosschainSubordinateView(
      final CrosschainContext crosschainContext) throws IOException {
    final Function function =
        new Function(
            FUNC_VIEWTXTYPE,
            Arrays.<Type>asList(),
            Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
    return createSignedSubordinateView(function, crosschainContext);
  }

  @Deprecated
  public static Ctrt1 load(
      String contractAddress,
      Besu besu,
      CrosschainTransactionManager crosschainTransactionManager,
      BigInteger gasPrice,
      BigInteger gasLimit) {
    return new Ctrt1(contractAddress, besu, crosschainTransactionManager, gasPrice, gasLimit);
  }

  public static Ctrt1 load(
      String contractAddress,
      Besu besu,
      CrosschainTransactionManager crosschainTransactionManager,
      ContractGasProvider contractGasProvider) {
    return new Ctrt1(contractAddress, besu, crosschainTransactionManager, contractGasProvider);
  }

  public static RemoteCall<Ctrt1> deployLockable(
      Besu besu,
      CrosschainTransactionManager transactionManager,
      ContractGasProvider contractGasProvider) {
    CrosschainContext crosschainContext = null;
    return deployLockableContractRemoteCall(
        Ctrt1.class, besu, transactionManager, contractGasProvider, BINARY, "", crosschainContext);
  }

  @Deprecated
  public static RemoteCall<Ctrt1> deployLockable(
      Besu besu,
      CrosschainTransactionManager transactionManager,
      BigInteger gasPrice,
      BigInteger gasLimit) {
    CrosschainContext crosschainContext = null;
    return deployLockableContractRemoteCall(
        Ctrt1.class, besu, transactionManager, gasPrice, gasLimit, BINARY, "", crosschainContext);
  }

  public static RemoteCall<Ctrt1> deployLockable(
      Besu besu,
      CrosschainTransactionManager transactionManager,
      ContractGasProvider contractGasProvider,
      final CrosschainContext crosschainContext) {
    return deployLockableContractRemoteCall(
        Ctrt1.class, besu, transactionManager, contractGasProvider, BINARY, "", crosschainContext);
  }

  @Deprecated
  public static RemoteCall<Ctrt1> deployLockable(
      Besu besu,
      CrosschainTransactionManager transactionManager,
      BigInteger gasPrice,
      BigInteger gasLimit,
      final CrosschainContext crosschainContext) {
    return deployLockableContractRemoteCall(
        Ctrt1.class, besu, transactionManager, gasPrice, gasLimit, BINARY, "", crosschainContext);
  }
}
