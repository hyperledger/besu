/*
 * Copyright ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.privacy.contracts.generated;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;

import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Bool;
import org.web3j.abi.datatypes.DynamicArray;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Bytes32;
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
 * <p>Generated with web3j version 4.5.16.
 */
@SuppressWarnings("rawtypes")
public class OnChainPrivacyGroupManagementProxy extends Contract {
  public static final String BINARY =
      "608060405234801561001057600080fd5b50604051610e99380380610e998339818101604052602081101561003357600080fd5b8101908080519060200190929190505050806000806101000a81548173ffffffffffffffffffffffffffffffffffffffff021916908373ffffffffffffffffffffffffffffffffffffffff16021790555050610e05806100946000396000f3fe608060405234801561001057600080fd5b506004361061009e5760003560e01c80639738968c116100665780639738968c146101d0578063a69df4b5146101f2578063b4926e25146101fc578063f83d08ba146102cc578063fd017797146102d65761009e565b80630d8e6e2c146100a35780633659cfe6146100c15780635aa68ac0146101055780635c60da1b1461016457806378b90337146101ae575b600080fd5b6100ab61031c565b6040518082815260200191505060405180910390f35b610103600480360360208110156100d757600080fd5b81019080803573ffffffffffffffffffffffffffffffffffffffff1690602001909291905050506103ca565b005b61010d61076b565b6040518080602001828103825283818151815260200191508051906020019060200280838360005b83811015610150578082015181840152602081019050610135565b505050509050019250505060405180910390f35b61016c6108b4565b604051808273ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff16815260200191505060405180910390f35b6101b66108d9565b604051808215151515815260200191505060405180910390f35b6101d8610987565b604051808215151515815260200191505060405180910390f35b6101fa610a37565b005b6102b26004803603602081101561021257600080fd5b810190808035906020019064010000000081111561022f57600080fd5b82018360208201111561024157600080fd5b8035906020019184602083028401116401000000008311171561026357600080fd5b919080806020026020016040519081016040528093929190818152602001838360200280828437600081840152601f19601f820116905080830192505050505050509192919290505050610ac0565b604051808215151515815260200191505060405180910390f35b6102d4610bbe565b005b610302600480360360208110156102ec57600080fd5b8101908080359060200190929190505050610c47565b604051808215151515815260200191505060405180910390f35b6000806000809054906101000a900473ffffffffffffffffffffffffffffffffffffffff1690508073ffffffffffffffffffffffffffffffffffffffff16630d8e6e2c6040518163ffffffff1660e01b815260040160206040518083038186803b15801561038957600080fd5b505afa15801561039d573d6000803e3d6000fd5b505050506040513d60208110156103b357600080fd5b810190808051906020019092919050505091505090565b8073ffffffffffffffffffffffffffffffffffffffff166000809054906101000a900473ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff161415610470576040517f08c379a0000000000000000000000000000000000000000000000000000000008152600401808060200182810382526053815260200180610d486053913960600191505060405180910390fd5b3073ffffffffffffffffffffffffffffffffffffffff16639738968c6040518163ffffffff1660e01b8152600401602060405180830381600087803b1580156104b857600080fd5b505af11580156104cc573d6000803e3d6000fd5b505050506040513d60208110156104e257600080fd5b8101908080519060200190929190505050610548576040517f08c379a0000000000000000000000000000000000000000000000000000000008152600401808060200182810382526036815260200180610d9b6036913960400191505060405180910390fd5b60603073ffffffffffffffffffffffffffffffffffffffff16635aa68ac06040518163ffffffff1660e01b815260040160006040518083038186803b15801561059057600080fd5b505afa1580156105a4573d6000803e3d6000fd5b505050506040513d6000823e3d601f19601f8201168201806040525060208110156105ce57600080fd5b81019080805160405193929190846401000000008211156105ee57600080fd5b8382019150602082018581111561060457600080fd5b825186602082028301116401000000008211171561062157600080fd5b8083526020830192505050908051906020019060200280838360005b8381101561065857808201518184015260208101905061063d565b50505050905001604052505050905061067082610d04565b60008060009054906101000a900473ffffffffffffffffffffffffffffffffffffffff1690508073ffffffffffffffffffffffffffffffffffffffff1663b4926e25836040518263ffffffff1660e01b81526004018080602001828103825283818151815260200191508051906020019060200280838360005b838110156107055780820151818401526020810190506106ea565b5050505090500192505050602060405180830381600087803b15801561072a57600080fd5b505af115801561073e573d6000803e3d6000fd5b505050506040513d602081101561075457600080fd5b810190808051906020019092919050505050505050565b606060008060009054906101000a900473ffffffffffffffffffffffffffffffffffffffff1690508073ffffffffffffffffffffffffffffffffffffffff16635aa68ac06040518163ffffffff1660e01b815260040160006040518083038186803b1580156107d957600080fd5b505afa1580156107ed573d6000803e3d6000fd5b505050506040513d6000823e3d601f19601f82011682018060405250602081101561081757600080fd5b810190808051604051939291908464010000000082111561083757600080fd5b8382019150602082018581111561084d57600080fd5b825186602082028301116401000000008211171561086a57600080fd5b8083526020830192505050908051906020019060200280838360005b838110156108a1578082015181840152602081019050610886565b5050505090500160405250505091505090565b6000809054906101000a900473ffffffffffffffffffffffffffffffffffffffff1681565b6000806000809054906101000a900473ffffffffffffffffffffffffffffffffffffffff1690508073ffffffffffffffffffffffffffffffffffffffff166378b903376040518163ffffffff1660e01b815260040160206040518083038186803b15801561094657600080fd5b505afa15801561095a573d6000803e3d6000fd5b505050506040513d602081101561097057600080fd5b810190808051906020019092919050505091505090565b6000806000809054906101000a900473ffffffffffffffffffffffffffffffffffffffff1690508073ffffffffffffffffffffffffffffffffffffffff16639738968c6040518163ffffffff1660e01b8152600401602060405180830381600087803b1580156109f657600080fd5b505af1158015610a0a573d6000803e3d6000fd5b505050506040513d6020811015610a2057600080fd5b810190808051906020019092919050505091505090565b60008060009054906101000a900473ffffffffffffffffffffffffffffffffffffffff1690508073ffffffffffffffffffffffffffffffffffffffff1663a69df4b56040518163ffffffff1660e01b8152600401600060405180830381600087803b158015610aa557600080fd5b505af1158015610ab9573d6000803e3d6000fd5b5050505050565b6000806000809054906101000a900473ffffffffffffffffffffffffffffffffffffffff1690508073ffffffffffffffffffffffffffffffffffffffff1663b4926e25846040518263ffffffff1660e01b81526004018080602001828103825283818151815260200191508051906020019060200280838360005b83811015610b56578082015181840152602081019050610b3b565b5050505090500192505050602060405180830381600087803b158015610b7b57600080fd5b505af1158015610b8f573d6000803e3d6000fd5b505050506040513d6020811015610ba557600080fd5b8101908080519060200190929190505050915050919050565b60008060009054906101000a900473ffffffffffffffffffffffffffffffffffffffff1690508073ffffffffffffffffffffffffffffffffffffffff1663f83d08ba6040518163ffffffff1660e01b8152600401600060405180830381600087803b158015610c2c57600080fd5b505af1158015610c40573d6000803e3d6000fd5b5050505050565b6000806000809054906101000a900473ffffffffffffffffffffffffffffffffffffffff1690508073ffffffffffffffffffffffffffffffffffffffff1663fd017797846040518263ffffffff1660e01b815260040180828152602001915050602060405180830381600087803b158015610cc157600080fd5b505af1158015610cd5573d6000803e3d6000fd5b505050506040513d6020811015610ceb57600080fd5b8101908080519060200190929190505050915050919050565b806000806101000a81548173ffffffffffffffffffffffffffffffffffffffff021916908373ffffffffffffffffffffffffffffffffffffffff1602179055505056fe54686520636f6e747261637420746f207570677261646520746f2068617320746f20626520646966666572656e742066726f6d207468652063757272656e74206d616e6167656d656e7420636f6e74726163744f726967696e206e6f7420616c6c6f77656420746f207570677261646520746865206d616e6167656d656e7420636f6e74726163742ea265627a7a72315820eccbe5d9c65b506e707c1d9970e14e6e9904d0c12d028f296b65da1da8864f5b64736f6c63430005110032";

  public static final String FUNC_ADDPARTICIPANTS = "addParticipants";

  public static final String FUNC_CANEXECUTE = "canExecute";

  public static final String FUNC_CANUPGRADE = "canUpgrade";

  public static final String FUNC_GETPARTICIPANTS = "getParticipants";

  public static final String FUNC_GETVERSION = "getVersion";

  public static final String FUNC_IMPLEMENTATION = "implementation";

  public static final String FUNC_LOCK = "lock";

  public static final String FUNC_REMOVEPARTICIPANT = "removeParticipant";

  public static final String FUNC_UNLOCK = "unlock";

  public static final String FUNC_UPGRADETO = "upgradeTo";

  @Deprecated
  protected OnChainPrivacyGroupManagementProxy(
      String contractAddress,
      Web3j web3j,
      Credentials credentials,
      BigInteger gasPrice,
      BigInteger gasLimit) {
    super(BINARY, contractAddress, web3j, credentials, gasPrice, gasLimit);
  }

  protected OnChainPrivacyGroupManagementProxy(
      String contractAddress,
      Web3j web3j,
      Credentials credentials,
      ContractGasProvider contractGasProvider) {
    super(BINARY, contractAddress, web3j, credentials, contractGasProvider);
  }

  @Deprecated
  protected OnChainPrivacyGroupManagementProxy(
      String contractAddress,
      Web3j web3j,
      TransactionManager transactionManager,
      BigInteger gasPrice,
      BigInteger gasLimit) {
    super(BINARY, contractAddress, web3j, transactionManager, gasPrice, gasLimit);
  }

  protected OnChainPrivacyGroupManagementProxy(
      String contractAddress,
      Web3j web3j,
      TransactionManager transactionManager,
      ContractGasProvider contractGasProvider) {
    super(BINARY, contractAddress, web3j, transactionManager, contractGasProvider);
  }

  public RemoteFunctionCall<TransactionReceipt> addParticipants(List<byte[]> participants) {
    final Function function =
        new Function(
            FUNC_ADDPARTICIPANTS,
            Arrays.<Type>asList(
                new org.web3j.abi.datatypes.DynamicArray<org.web3j.abi.datatypes.generated.Bytes32>(
                    org.web3j.abi.datatypes.generated.Bytes32.class,
                    org.web3j.abi.Utils.typeMap(
                        participants, org.web3j.abi.datatypes.generated.Bytes32.class))),
            Collections.<TypeReference<?>>emptyList());
    return executeRemoteCallTransaction(function);
  }

  public RemoteFunctionCall<Boolean> canExecute() {
    final Function function =
        new Function(
            FUNC_CANEXECUTE,
            Arrays.<Type>asList(),
            Arrays.<TypeReference<?>>asList(new TypeReference<Bool>() {}));
    return executeRemoteCallSingleValueReturn(function, Boolean.class);
  }

  public RemoteFunctionCall<TransactionReceipt> canUpgrade() {
    final Function function =
        new Function(
            FUNC_CANUPGRADE, Arrays.<Type>asList(), Collections.<TypeReference<?>>emptyList());
    return executeRemoteCallTransaction(function);
  }

  public RemoteFunctionCall<List> getParticipants() {
    final Function function =
        new Function(
            FUNC_GETPARTICIPANTS,
            Arrays.<Type>asList(),
            Arrays.<TypeReference<?>>asList(new TypeReference<DynamicArray<Bytes32>>() {}));
    return new RemoteFunctionCall<List>(
        function,
        new Callable<List>() {
          @Override
          @SuppressWarnings("unchecked")
          public List call() throws Exception {
            List<Type> result = (List<Type>) executeCallSingleValueReturn(function, List.class);
            return convertToNative(result);
          }
        });
  }

  public RemoteFunctionCall<byte[]> getVersion() {
    final Function function =
        new Function(
            FUNC_GETVERSION,
            Arrays.<Type>asList(),
            Arrays.<TypeReference<?>>asList(new TypeReference<Bytes32>() {}));
    return executeRemoteCallSingleValueReturn(function, byte[].class);
  }

  public RemoteFunctionCall<String> implementation() {
    final Function function =
        new Function(
            FUNC_IMPLEMENTATION,
            Arrays.<Type>asList(),
            Arrays.<TypeReference<?>>asList(new TypeReference<Address>() {}));
    return executeRemoteCallSingleValueReturn(function, String.class);
  }

  public RemoteFunctionCall<TransactionReceipt> lock() {
    final Function function =
        new Function(FUNC_LOCK, Arrays.<Type>asList(), Collections.<TypeReference<?>>emptyList());
    return executeRemoteCallTransaction(function);
  }

  public RemoteFunctionCall<TransactionReceipt> removeParticipant(byte[] account) {
    final Function function =
        new Function(
            FUNC_REMOVEPARTICIPANT,
            Arrays.<Type>asList(new org.web3j.abi.datatypes.generated.Bytes32(account)),
            Collections.<TypeReference<?>>emptyList());
    return executeRemoteCallTransaction(function);
  }

  public RemoteFunctionCall<TransactionReceipt> unlock() {
    final Function function =
        new Function(FUNC_UNLOCK, Arrays.<Type>asList(), Collections.<TypeReference<?>>emptyList());
    return executeRemoteCallTransaction(function);
  }

  public RemoteFunctionCall<TransactionReceipt> upgradeTo(String _newImplementation) {
    final Function function =
        new Function(
            FUNC_UPGRADETO,
            Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(160, _newImplementation)),
            Collections.<TypeReference<?>>emptyList());
    return executeRemoteCallTransaction(function);
  }

  @Deprecated
  public static OnChainPrivacyGroupManagementProxy load(
      String contractAddress,
      Web3j web3j,
      Credentials credentials,
      BigInteger gasPrice,
      BigInteger gasLimit) {
    return new OnChainPrivacyGroupManagementProxy(
        contractAddress, web3j, credentials, gasPrice, gasLimit);
  }

  @Deprecated
  public static OnChainPrivacyGroupManagementProxy load(
      String contractAddress,
      Web3j web3j,
      TransactionManager transactionManager,
      BigInteger gasPrice,
      BigInteger gasLimit) {
    return new OnChainPrivacyGroupManagementProxy(
        contractAddress, web3j, transactionManager, gasPrice, gasLimit);
  }

  public static OnChainPrivacyGroupManagementProxy load(
      String contractAddress,
      Web3j web3j,
      Credentials credentials,
      ContractGasProvider contractGasProvider) {
    return new OnChainPrivacyGroupManagementProxy(
        contractAddress, web3j, credentials, contractGasProvider);
  }

  public static OnChainPrivacyGroupManagementProxy load(
      String contractAddress,
      Web3j web3j,
      TransactionManager transactionManager,
      ContractGasProvider contractGasProvider) {
    return new OnChainPrivacyGroupManagementProxy(
        contractAddress, web3j, transactionManager, contractGasProvider);
  }

  public static RemoteCall<OnChainPrivacyGroupManagementProxy> deploy(
      Web3j web3j,
      Credentials credentials,
      ContractGasProvider contractGasProvider,
      String _implementation) {
    String encodedConstructor =
        FunctionEncoder.encodeConstructor(
            Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(160, _implementation)));
    return deployRemoteCall(
        OnChainPrivacyGroupManagementProxy.class,
        web3j,
        credentials,
        contractGasProvider,
        BINARY,
        encodedConstructor);
  }

  public static RemoteCall<OnChainPrivacyGroupManagementProxy> deploy(
      Web3j web3j,
      TransactionManager transactionManager,
      ContractGasProvider contractGasProvider,
      String _implementation) {
    String encodedConstructor =
        FunctionEncoder.encodeConstructor(
            Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(160, _implementation)));
    return deployRemoteCall(
        OnChainPrivacyGroupManagementProxy.class,
        web3j,
        transactionManager,
        contractGasProvider,
        BINARY,
        encodedConstructor);
  }

  @Deprecated
  public static RemoteCall<OnChainPrivacyGroupManagementProxy> deploy(
      Web3j web3j,
      Credentials credentials,
      BigInteger gasPrice,
      BigInteger gasLimit,
      String _implementation) {
    String encodedConstructor =
        FunctionEncoder.encodeConstructor(
            Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(160, _implementation)));
    return deployRemoteCall(
        OnChainPrivacyGroupManagementProxy.class,
        web3j,
        credentials,
        gasPrice,
        gasLimit,
        BINARY,
        encodedConstructor);
  }

  @Deprecated
  public static RemoteCall<OnChainPrivacyGroupManagementProxy> deploy(
      Web3j web3j,
      TransactionManager transactionManager,
      BigInteger gasPrice,
      BigInteger gasLimit,
      String _implementation) {
    String encodedConstructor =
        FunctionEncoder.encodeConstructor(
            Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(160, _implementation)));
    return deployRemoteCall(
        OnChainPrivacyGroupManagementProxy.class,
        web3j,
        transactionManager,
        gasPrice,
        gasLimit,
        BINARY,
        encodedConstructor);
  }
}
