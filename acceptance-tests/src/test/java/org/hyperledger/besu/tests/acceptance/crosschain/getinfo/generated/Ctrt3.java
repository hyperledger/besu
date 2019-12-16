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
import org.web3j.abi.datatypes.generated.Uint32;
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
public class Ctrt3 extends CrosschainContract {
  private static final String BINARY =
      "608060405234801561001057600080fd5b506100226001600160e01b0361004716565b600260046101000a81548163ffffffff021916908363ffffffff16021790555061009b565b6000600461005361007d565b6000815261005f61007d565b60048082858560fa600019fa61007457600080fd5b50519392505050565b60405180602001604052806001906020820280388339509192915050565b610357806100aa6000396000f3fe608060405234801561001057600080fd5b506004361061009e5760003560e01c8063a4a1ab5811610066578063a4a1ab5814610112578063a7ded5351461011c578063cd568d1f14610124578063e6fdb7ea1461012c578063f41c38a1146101345761009e565b806301605925146100a35780630a56e1a0146100bd5780633e2ae2f8146100e157806346c9da56146100e957806379fe365f1461010a575b600080fd5b6100ab61013c565b60408051918252519081900360200190f35b6100c5610142565b604080516001600160a01b039092168252519081900360200190f35b6100ab610151565b6100f1610157565b6040805163ffffffff9092168252519081900360200190f35b6100ab610163565b61011a610169565b005b6100ab610214565b6100ab61021a565b6100c5610220565b6100f161022f565b60045481565b6006546001600160a01b031681565b60015481565b60025463ffffffff1681565b60035481565b610171610243565b60005561017c610254565b6002805463ffffffff191663ffffffff9290921691909117905561019e61028a565b6003556101a9610296565b6004556101b46102a2565b6001556101bf6102ae565b6005556101ca6102ba565b600680546001600160a01b0319166001600160a01b03929092169190911790556101f26102c6565b600780546001600160a01b0319166001600160a01b0392909216919091179055565b60005481565b60055481565b6007546001600160a01b031681565b600254640100000000900463ffffffff1681565b600061024f60016102ce565b905090565b60006004610260610304565b6000815261026c610304565b60048082858560fa600019fa61028157600080fd5b50519392505050565b600061024f60026102ce565b600061024f60046102ce565b600061024f60056102ce565b600061024f60076102ce565b600061024f60036102ce565b600061024f60065b600060206102da610304565b8381526102e5610304565b6020808285856078600019fa6102fa57600080fd5b5051949350505050565b6040518060200160405280600190602082028038833950919291505056fea265627a7a72315820e9a00603c1c868df7ac54741e7539ce611891f0c40e5e4a763ad713c8757e53164736f6c634300050c0032";

  public static final String FUNC_CONSTXTYPE = "consTxType";

  public static final String FUNC_COORDCHAINID = "coordChainId";

  public static final String FUNC_COORDCTRTADDR = "coordCtrtAddr";

  public static final String FUNC_FROMADDR = "fromAddr";

  public static final String FUNC_FROMCHAINID = "fromChainId";

  public static final String FUNC_MYCHAINID = "myChainId";

  public static final String FUNC_MYTXTYPE = "myTxType";

  public static final String FUNC_ORIGCHAINID = "origChainId";

  public static final String FUNC_TXID = "txId";

  public static final String FUNC_TXFN = "txfn";

  @Deprecated
  protected Ctrt3(
      String contractAddress,
      Besu besu,
      CrosschainTransactionManager crosschainTransactionManager,
      BigInteger gasPrice,
      BigInteger gasLimit) {
    super(BINARY, contractAddress, besu, crosschainTransactionManager, gasPrice, gasLimit);
  }

  protected Ctrt3(
      String contractAddress,
      Besu besu,
      CrosschainTransactionManager crosschainTransactionManager,
      ContractGasProvider contractGasProvider) {
    super(BINARY, contractAddress, besu, crosschainTransactionManager, contractGasProvider);
  }

  public RemoteFunctionCall<BigInteger> consTxType() {
    final Function function =
        new Function(
            FUNC_CONSTXTYPE,
            Arrays.<Type>asList(),
            Arrays.<TypeReference<?>>asList(new TypeReference<Uint32>() {}));
    return executeRemoteCallSingleValueReturn(function, BigInteger.class);
  }

  public byte[] consTxType_AsSignedCrosschainSubordinateView(
      final CrosschainContext crosschainContext) throws IOException {
    final Function function =
        new Function(
            FUNC_CONSTXTYPE,
            Arrays.<Type>asList(),
            Arrays.<TypeReference<?>>asList(new TypeReference<Uint32>() {}));
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
            Arrays.<TypeReference<?>>asList(new TypeReference<Uint32>() {}));
    return executeRemoteCallSingleValueReturn(function, BigInteger.class);
  }

  public byte[] myTxType_AsSignedCrosschainSubordinateView(
      final CrosschainContext crosschainContext) throws IOException {
    final Function function =
        new Function(
            FUNC_MYTXTYPE,
            Arrays.<Type>asList(),
            Arrays.<TypeReference<?>>asList(new TypeReference<Uint32>() {}));
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

  public RemoteFunctionCall<TransactionReceipt> txfn() {
    final Function function =
        new Function(FUNC_TXFN, Arrays.<Type>asList(), Collections.<TypeReference<?>>emptyList());
    return executeRemoteCallTransaction(function);
  }

  public byte[] txfn_AsSignedCrosschainSubordinateTransaction(
      final CrosschainContext crosschainContext) throws IOException {
    final Function function =
        new Function(FUNC_TXFN, Arrays.<Type>asList(), Collections.<TypeReference<?>>emptyList());
    return createSignedSubordinateTransaction(function, crosschainContext);
  }

  public RemoteFunctionCall<TransactionReceipt> txfn_AsCrosschainTransaction(
      final CrosschainContext crosschainContext) {
    final Function function =
        new Function(FUNC_TXFN, Arrays.<Type>asList(), Collections.<TypeReference<?>>emptyList());
    return executeRemoteCallCrosschainTransaction(function, crosschainContext);
  }

  @Deprecated
  public static Ctrt3 load(
      String contractAddress,
      Besu besu,
      CrosschainTransactionManager crosschainTransactionManager,
      BigInteger gasPrice,
      BigInteger gasLimit) {
    return new Ctrt3(contractAddress, besu, crosschainTransactionManager, gasPrice, gasLimit);
  }

  public static Ctrt3 load(
      String contractAddress,
      Besu besu,
      CrosschainTransactionManager crosschainTransactionManager,
      ContractGasProvider contractGasProvider) {
    return new Ctrt3(contractAddress, besu, crosschainTransactionManager, contractGasProvider);
  }

  public static RemoteCall<Ctrt3> deployLockable(
      Besu besu,
      CrosschainTransactionManager transactionManager,
      ContractGasProvider contractGasProvider) {
    CrosschainContext crosschainContext = null;
    return deployLockableContractRemoteCall(
        Ctrt3.class, besu, transactionManager, contractGasProvider, BINARY, "", crosschainContext);
  }

  @Deprecated
  public static RemoteCall<Ctrt3> deployLockable(
      Besu besu,
      CrosschainTransactionManager transactionManager,
      BigInteger gasPrice,
      BigInteger gasLimit) {
    CrosschainContext crosschainContext = null;
    return deployLockableContractRemoteCall(
        Ctrt3.class, besu, transactionManager, gasPrice, gasLimit, BINARY, "", crosschainContext);
  }

  public static RemoteCall<Ctrt3> deployLockable(
      Besu besu,
      CrosschainTransactionManager transactionManager,
      ContractGasProvider contractGasProvider,
      final CrosschainContext crosschainContext) {
    return deployLockableContractRemoteCall(
        Ctrt3.class, besu, transactionManager, contractGasProvider, BINARY, "", crosschainContext);
  }

  @Deprecated
  public static RemoteCall<Ctrt3> deployLockable(
      Besu besu,
      CrosschainTransactionManager transactionManager,
      BigInteger gasPrice,
      BigInteger gasLimit,
      final CrosschainContext crosschainContext) {
    return deployLockableContractRemoteCall(
        Ctrt3.class, besu, transactionManager, gasPrice, gasLimit, BINARY, "", crosschainContext);
  }
}
