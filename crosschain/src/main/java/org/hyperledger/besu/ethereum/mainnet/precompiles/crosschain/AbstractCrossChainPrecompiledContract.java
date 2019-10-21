/*
 * Copyright 2018 ConsenSys AG.
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
package org.hyperledger.besu.ethereum.mainnet.precompiles.crosschain;

import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.CrosschainTransaction;
import org.hyperledger.besu.ethereum.core.Gas;
import org.hyperledger.besu.ethereum.crosschain.CrosschainThreadLocalDataHolder;
import org.hyperledger.besu.ethereum.mainnet.AbstractPrecompiledContract;
import org.hyperledger.besu.ethereum.vm.GasCalculator;
import org.hyperledger.besu.util.bytes.BytesValue;

import java.math.BigInteger;
import java.util.Arrays;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public abstract class AbstractCrossChainPrecompiledContract extends AbstractPrecompiledContract {

  protected static final Logger LOG = LogManager.getLogger();

  private static final int uint256Length = 32;
  private static final int contractAddressLength = 20;
  private static final int functionSignatureHashLength = 4;
  private int offset;

  private BigInteger actualLength;
  private BigInteger actualSidechainId;
  private BigInteger actualZeroFillBeforeAddress;
  private BigInteger actualContractAddress;
  private BigInteger actualLengthOfFunctionAndParametersAndLength;
  private BigInteger actualLengthOfFunctionAndParameters;
  private BigInteger actualFunction;
  private BigInteger actualFunctionParameters;

  protected AbstractCrossChainPrecompiledContract(
      final String name, final GasCalculator gasCalculator) {
    super(name, gasCalculator);
  }

  @Override
  public Gas gasRequirement(final BytesValue input) {
    return gasCalculator().idPrecompiledContractGasCost(input);
  } // TODO do not use the idPrecompiledContractGasCost, create a new one

  protected BytesValue processSubordinateTxOrView(final BytesValue input) {
    this.offset = 0;
    this.actualLength = extractParameter(input, uint256Length);
    this.actualSidechainId = extractParameter(input, uint256Length);
    this.actualZeroFillBeforeAddress = extractParameter(input, 12);
    this.actualContractAddress = extractParameter(input, contractAddressLength);
    this.actualLengthOfFunctionAndParametersAndLength = extractParameter(input, uint256Length);
    this.actualLengthOfFunctionAndParameters = extractParameter(input, uint256Length);
    this.actualFunction = extractParameter(input, functionSignatureHashLength);
    this.actualFunctionParameters =
        extractParameter(
            input, actualLengthOfFunctionAndParameters.intValue() - functionSignatureHashLength);
    if (this.offset != input.size()) {
      // TODO: We need to call the precompile with only an extra 4 bytes, and not an extra 32 bytes.
      // TODO: While this log message is still appearing, we know we have something to fix.
      LOG.info(
          "Actual parameter was longer than needed: Needed: "
              + this.offset
              + ", Actual: "
              + input.size());
    }

    // Fetch the transaction which is the context of this pre-compile execution.
    CrosschainTransaction tx = CrosschainThreadLocalDataHolder.getCrosschainTransaction();
    if (tx == null) {
      LOG.error("Attempted a crosschain transaction that had not been provided 1");
      logActual();
      // Indicate execution failed unexpectedly by returning null.
      return null;
    }
    CrosschainTransaction ct = tx.getNextSubordinateTransactionOrView();
    if (!isMatched(ct)) {
      LOG.error("Mismatched expected and actual crosschain subordinate view and transaction");
      logActual();
      // Indicate execution failed unexpectedly by returning null.
      return null;
    }

    BigInteger expectedSidechainId =
        (ct.getChainId().isPresent() ? ct.getChainId().get() : BigInteger.ZERO);
    Address expectedContractAddress =
        (ct.getTo().isPresent() ? ct.getTo().get() : Address.fromHexString("0x0"));
    BigInteger expectedFunction = extractParameter(ct.getPayload(), 0, 4);
    BigInteger expectedFunctionParameters =
        extractParameter(ct.getPayload(), 4, ct.getPayload().size() - 4);

    boolean fail = false;
    if (!expectedSidechainId.equals(actualSidechainId)) {
      LOG.error("Expected and actual target SidechainId do not match");
      fail = true;
    }
    BigInteger expectedAddress = new BigInteger(1, expectedContractAddress.extractArray());
    if (!expectedAddress.equals(actualContractAddress)) {
      LOG.error("Expected and actual target ContractAddress do not match");
      fail = true;
    }
    if (!expectedFunction.equals(actualFunction)) {
      LOG.error("Expected and actual target Function do not match");
      fail = true;
    }
    if (!expectedFunctionParameters.equals(actualFunctionParameters)) {
      LOG.error("Expected and actual target Function Parameters do not match");
      fail = true;
    }

    if (fail) {
      logActual();

      LOG.error("expectedSidechainId: " + expectedSidechainId.toString(16));
      LOG.error("expectedContractAddress: " + expectedContractAddress.toUnprefixedString());
      LOG.error("expectedFunction: " + expectedFunction.toString(16));
      LOG.error("expectedlParameters: " + expectedFunctionParameters.toString(16));
      // Indicate execution failed unexpectedly by returning null.
      return null;
    }

    if (ct.getType().isSubordinateView()) {
      BytesValue result = ct.getSignedResult();
      LOG.info("Crosschain Result: " + result.toString());
      return result;
    } else {
      return BytesValue.of(1);
    }
  }

  private void logActual() {
    LOG.error("actualLength: " + actualLength.toString(16));
    LOG.error("actualSidechainId: " + actualSidechainId.toString(16));
    LOG.error("actualZeroFillBeforeAddress: " + actualZeroFillBeforeAddress.toString(16));
    LOG.error("actualContractAddress: " + actualContractAddress.toString(16));
    LOG.error("actualLengthOfRest: " + actualLengthOfFunctionAndParametersAndLength.toString(16));
    LOG.error("actualLengthOfFuncParam: " + actualLengthOfFunctionAndParameters.toString(16));
    LOG.error("actualFunction: " + actualFunction.toString(16));
    LOG.error("actualParameters: " + actualFunctionParameters.toString(16));
  }

  protected abstract boolean isMatched(CrosschainTransaction ct);

  private BigInteger extractParameter(final BytesValue input, final int length) {
    BigInteger result = extractParameter(input, this.offset, length);
    this.offset += length;
    return result;
  }

  private static BigInteger extractParameter(
      final BytesValue input, final int offset, final int length) {
    if (offset > input.size() || length == 0) {
      return BigInteger.ZERO;
    }
    final byte[] raw = Arrays.copyOfRange(input.extractArray(), offset, offset + length);
    return new BigInteger(1, raw);
  }
}
