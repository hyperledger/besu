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
package org.hyperledger.besu.crosschain.core.keys.generation;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public enum KeyGenFailureToCompleteReason {
  UNKNOWN_KEY(Constants.UNKNOWN_KEY),
  NO_FAILURE_THUS_FAR(Constants.NO_FAILURE_THUS_FAR),
  DID_NOT_POST_XVALUE(Constants.DID_NOT_POST_XVALUE),
  DID_NOT_POST_COMMITMENT(Constants.DID_NOT_POST_COMMITMENT),
  DID_NOT_POST_COEFFICIENT_PUBLIC_VALUES(Constants.DID_NOT_POST_COEFFICIENT_PUBLIC_VALUES),
  INVALID_COEFFICIENT_PUBLIC_VALUES(Constants.INVALID_COEFFICIENT_PUBLIC_VALUES),
  COEFFICIENT_PUBLIC_VALUES_DID_NOT_MATCH_COMMITMENTS(
      Constants.COEFFICIENT_PUBLIC_VALUES_DID_NOT_MATCH_COMMITMENTS),
  DID_NOT_SEND_PRIVATE_VALUES(Constants.DID_NOT_SEND_PRIVATE_VALUES),
  PRIVATE_VALUES_DID_NOT_MATCH_COEFFICIENT_PUBLIC_VALUES(
      Constants.PRIVATE_VALUES_DID_NOT_MATCH_COEFFICIENT_PUBLIC_VALUES),
  SUCCESS(Constants.SUCCESS);

  private static Logger LOG = LogManager.getLogger();

  private static class Constants {
    private static final int UNKNOWN_KEY = 0;
    private static final int NO_FAILURE_THUS_FAR = 1;
    private static final int DID_NOT_POST_XVALUE = 2;
    private static final int DID_NOT_POST_COMMITMENT = 3;
    private static final int DID_NOT_POST_COEFFICIENT_PUBLIC_VALUES = 4;
    private static final int INVALID_COEFFICIENT_PUBLIC_VALUES = 5;
    private static final int COEFFICIENT_PUBLIC_VALUES_DID_NOT_MATCH_COMMITMENTS = 6;
    private static final int DID_NOT_SEND_PRIVATE_VALUES = 7;
    private static final int PRIVATE_VALUES_DID_NOT_MATCH_COEFFICIENT_PUBLIC_VALUES = 8;
    private static final int SUCCESS = 20;
  }

  public int value;

  KeyGenFailureToCompleteReason(final int val) {
    this.value = val;
  }

  public static KeyGenFailureToCompleteReason create(final int val) {
    switch (val) {
      case Constants.UNKNOWN_KEY:
        return UNKNOWN_KEY;
      case Constants.NO_FAILURE_THUS_FAR:
        return NO_FAILURE_THUS_FAR;
      case Constants.DID_NOT_POST_XVALUE:
        return DID_NOT_POST_XVALUE;
      case Constants.DID_NOT_POST_COMMITMENT:
        return DID_NOT_POST_COMMITMENT;
      case Constants.DID_NOT_POST_COEFFICIENT_PUBLIC_VALUES:
        return DID_NOT_POST_COEFFICIENT_PUBLIC_VALUES;
      case Constants.INVALID_COEFFICIENT_PUBLIC_VALUES:
        return INVALID_COEFFICIENT_PUBLIC_VALUES;
      case Constants.COEFFICIENT_PUBLIC_VALUES_DID_NOT_MATCH_COMMITMENTS:
        return COEFFICIENT_PUBLIC_VALUES_DID_NOT_MATCH_COMMITMENTS;
      case Constants.DID_NOT_SEND_PRIVATE_VALUES:
        return DID_NOT_SEND_PRIVATE_VALUES;
      case Constants.PRIVATE_VALUES_DID_NOT_MATCH_COEFFICIENT_PUBLIC_VALUES:
        return PRIVATE_VALUES_DID_NOT_MATCH_COEFFICIENT_PUBLIC_VALUES;
      case Constants.SUCCESS:
        return SUCCESS;

      default:
        String error = "Unknown KeyGenFailureToCompleteReason: " + val;
        LOG.error(error);
        throw new RuntimeException(error);
    }
  }
}
