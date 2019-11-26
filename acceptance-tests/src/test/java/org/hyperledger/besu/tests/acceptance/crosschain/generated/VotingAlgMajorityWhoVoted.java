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
package org.hyperledger.besu.tests.acceptance.crosschain.generated;

import java.math.BigInteger;
import java.util.Arrays;

import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Bool;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.RemoteCall;
import org.web3j.protocol.core.RemoteFunctionCall;
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
public class VotingAlgMajorityWhoVoted extends Contract {
  private static final String BINARY =
      "608060405234801561001057600080fd5b5060c08061001f6000396000f3fe6080604052348015600f57600080fd5b506004361060285760003560e01c8063a81ce84714602d575b600080fd5b606360048036036060811015604157600080fd5b5067ffffffffffffffff81358116916020810135821691604090910135166077565b604080519115158252519081900360200190f35b67ffffffffffffffff90811691161191905056fea265627a7a72305820e013688f798f304869775af1d4b6c7efc369626dca96b46903f4e247a1e1949664736f6c634300050a0032";

  public static final String FUNC_ASSESS = "assess";

  @Deprecated
  protected VotingAlgMajorityWhoVoted(
      String contractAddress,
      Web3j web3j,
      Credentials credentials,
      BigInteger gasPrice,
      BigInteger gasLimit) {
    super(BINARY, contractAddress, web3j, credentials, gasPrice, gasLimit);
  }

  protected VotingAlgMajorityWhoVoted(
      String contractAddress,
      Web3j web3j,
      Credentials credentials,
      ContractGasProvider contractGasProvider) {
    super(BINARY, contractAddress, web3j, credentials, contractGasProvider);
  }

  @Deprecated
  protected VotingAlgMajorityWhoVoted(
      String contractAddress,
      Web3j web3j,
      TransactionManager transactionManager,
      BigInteger gasPrice,
      BigInteger gasLimit) {
    super(BINARY, contractAddress, web3j, transactionManager, gasPrice, gasLimit);
  }

  protected VotingAlgMajorityWhoVoted(
      String contractAddress,
      Web3j web3j,
      TransactionManager transactionManager,
      ContractGasProvider contractGasProvider) {
    super(BINARY, contractAddress, web3j, transactionManager, contractGasProvider);
  }

  public RemoteFunctionCall<Boolean> assess(
      BigInteger param0, BigInteger numVotedFor, BigInteger numVotedAgainst) {
    final Function function =
        new Function(
            FUNC_ASSESS,
            Arrays.<Type>asList(
                new org.web3j.abi.datatypes.generated.Uint64(param0),
                new org.web3j.abi.datatypes.generated.Uint64(numVotedFor),
                new org.web3j.abi.datatypes.generated.Uint64(numVotedAgainst)),
            Arrays.<TypeReference<?>>asList(new TypeReference<Bool>() {}));
    return executeRemoteCallSingleValueReturn(function, Boolean.class);
  }

  @Deprecated
  public static VotingAlgMajorityWhoVoted load(
      String contractAddress,
      Web3j web3j,
      Credentials credentials,
      BigInteger gasPrice,
      BigInteger gasLimit) {
    return new VotingAlgMajorityWhoVoted(contractAddress, web3j, credentials, gasPrice, gasLimit);
  }

  @Deprecated
  public static VotingAlgMajorityWhoVoted load(
      String contractAddress,
      Web3j web3j,
      TransactionManager transactionManager,
      BigInteger gasPrice,
      BigInteger gasLimit) {
    return new VotingAlgMajorityWhoVoted(
        contractAddress, web3j, transactionManager, gasPrice, gasLimit);
  }

  public static VotingAlgMajorityWhoVoted load(
      String contractAddress,
      Web3j web3j,
      Credentials credentials,
      ContractGasProvider contractGasProvider) {
    return new VotingAlgMajorityWhoVoted(contractAddress, web3j, credentials, contractGasProvider);
  }

  public static VotingAlgMajorityWhoVoted load(
      String contractAddress,
      Web3j web3j,
      TransactionManager transactionManager,
      ContractGasProvider contractGasProvider) {
    return new VotingAlgMajorityWhoVoted(
        contractAddress, web3j, transactionManager, contractGasProvider);
  }

  public static RemoteCall<VotingAlgMajorityWhoVoted> deploy(
      Web3j web3j, Credentials credentials, ContractGasProvider contractGasProvider) {
    return deployRemoteCall(
        VotingAlgMajorityWhoVoted.class, web3j, credentials, contractGasProvider, BINARY, "");
  }

  @Deprecated
  public static RemoteCall<VotingAlgMajorityWhoVoted> deploy(
      Web3j web3j, Credentials credentials, BigInteger gasPrice, BigInteger gasLimit) {
    return deployRemoteCall(
        VotingAlgMajorityWhoVoted.class, web3j, credentials, gasPrice, gasLimit, BINARY, "");
  }

  public static RemoteCall<VotingAlgMajorityWhoVoted> deploy(
      Web3j web3j, TransactionManager transactionManager, ContractGasProvider contractGasProvider) {
    return deployRemoteCall(
        VotingAlgMajorityWhoVoted.class,
        web3j,
        transactionManager,
        contractGasProvider,
        BINARY,
        "");
  }

  @Deprecated
  public static RemoteCall<VotingAlgMajorityWhoVoted> deploy(
      Web3j web3j,
      TransactionManager transactionManager,
      BigInteger gasPrice,
      BigInteger gasLimit) {
    return deployRemoteCall(
        VotingAlgMajorityWhoVoted.class, web3j, transactionManager, gasPrice, gasLimit, BINARY, "");
  }
}
