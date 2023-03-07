package org.hyperledger.besu.ethereum.core;

import org.hyperledger.besu.evm.log.LogTopic;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.DynamicBytes;
import org.web3j.abi.datatypes.Event;
import org.web3j.protocol.Web3j;
import org.web3j.tx.Contract;
import org.web3j.tx.TransactionManager;
import org.web3j.tx.gas.ContractGasProvider;
import org.web3j.protocol.core.methods.response.Log;

import java.util.Arrays;
import java.util.stream.Collectors;

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

  protected DepositContract(final String contractBinary, final String contractAddress, final Web3j web3j, final TransactionManager transactionManager, final ContractGasProvider gasProvider) {
    super(contractBinary, contractAddress, web3j, transactionManager, gasProvider);
  }

  public static EventValuesWithLog staticExtractDepositEventWithLog(final org.hyperledger.besu.evm.log.Log log) {
    Log web3jLog = new Log();
    web3jLog.setTopics(log.getTopics().stream().map(LogTopic::toHexString).collect(Collectors.toList()));
    web3jLog.setData(log.getData().toHexString());

    return staticExtractEventParametersWithLog(DEPOSITEVENT_EVENT, web3jLog);
  }

}
