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
package org.hyperledger.besu.crosschain.core.messages;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public enum ThresholdSignedMessageType {
  UNKNOWN(Constants.UNKNOWN),
  CROSSCHAIN_TRANSACTION_START(Constants.CROSSCHAIN_TRANSACTION_START),
  CROSSCHAIN_TRANSACTION_COMMIT(Constants.CROSSCHAIN_TRANSACTION_COMMIT),
  CROSSCHAIN_TRANSACTION_IGNORE(Constants.CROSSCHAIN_TRANSACTION_IGNORE),
  SUBORDINATE_VIEW_RESULT(Constants.SUBORDINATE_VIEW_RESULT),
  SUBORDINATE_TRANSACTION_READY(Constants.SUBORDINATE_TRANSACTION_READY);

  private static Logger LOG = LogManager.getLogger();

  private static class Constants {
    private static final int UNKNOWN = 0;
    private static final int CROSSCHAIN_TRANSACTION_START = 1;
    private static final int CROSSCHAIN_TRANSACTION_COMMIT = 2;
    private static final int CROSSCHAIN_TRANSACTION_IGNORE = 3;
    private static final int SUBORDINATE_VIEW_RESULT = 4;
    private static final int SUBORDINATE_TRANSACTION_READY = 5;
  }

  public int value;

  ThresholdSignedMessageType(final int val) {
    this.value = val;
  }

  public static ThresholdSignedMessageType create(final int val) {
    switch (val) {
      case Constants.UNKNOWN:
        return UNKNOWN;
      case Constants.CROSSCHAIN_TRANSACTION_START:
        return CROSSCHAIN_TRANSACTION_START;
      case Constants.CROSSCHAIN_TRANSACTION_COMMIT:
        return CROSSCHAIN_TRANSACTION_COMMIT;
      case Constants.CROSSCHAIN_TRANSACTION_IGNORE:
        return CROSSCHAIN_TRANSACTION_IGNORE;
      case Constants.SUBORDINATE_VIEW_RESULT:
        return SUBORDINATE_VIEW_RESULT;
      case Constants.SUBORDINATE_TRANSACTION_READY:
        return SUBORDINATE_TRANSACTION_READY;

      default:
        String error = "Unknown ThresholdSignedMessageType: " + val;
        LOG.error(error);
        throw new RuntimeException(error);
    }
  }
}
