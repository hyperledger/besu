/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.ethereum.core;

import org.hyperledger.besu.evm.log.LogTopic;

import java.util.Arrays;
import java.util.stream.Collectors;

import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.DynamicBytes;
import org.web3j.abi.datatypes.Event;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.Log;
import org.web3j.tx.Contract;
import org.web3j.tx.TransactionManager;
import org.web3j.tx.gas.ContractGasProvider;

public class DepositContract extends Contract {

  public static final Event DEPOSITEVENT_EVENT =
      new Event(
          "DepositEvent",
          Arrays.asList(
              new TypeReference<DynamicBytes>() {},
              new TypeReference<DynamicBytes>() {},
              new TypeReference<DynamicBytes>() {},
              new TypeReference<DynamicBytes>() {},
              new TypeReference<DynamicBytes>() {}));

  DepositContract(
      final String contractBinary,
      final String contractAddress,
      final Web3j web3j,
      final TransactionManager transactionManager,
      final ContractGasProvider gasProvider) {
    super(contractBinary, contractAddress, web3j, transactionManager, gasProvider);
  }

  public static EventValuesWithLog staticExtractDepositEventWithLog(
      final org.hyperledger.besu.evm.log.Log log) {
    Log web3jLog = new Log();
    web3jLog.setTopics(
        log.getTopics().stream().map(LogTopic::toHexString).collect(Collectors.toList()));
    web3jLog.setData(log.getData().toHexString());

    return staticExtractEventParametersWithLog(DEPOSITEVENT_EVENT, web3jLog);
  }
}
