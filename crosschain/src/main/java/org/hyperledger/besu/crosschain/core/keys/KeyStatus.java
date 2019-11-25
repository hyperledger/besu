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
package org.hyperledger.besu.crosschain.core.keys;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public enum KeyStatus {
  UNKNOWN_KEY(Constants.UNKNOWN_KEY),
  KEY_GEN_POST_XVALUE(Constants.KEY_GEN_POST_XVALUE),
  KEY_GEN_POST_COMMITMENT(Constants.KEY_GEN_POST_COMMITMENT),
  KEY_GEN_PUBLIC_VALUES(Constants.KEY_GEN_PUBLIC_VALUES),
  KEY_GEN_PRIVATE_VALUES(Constants.KEY_GEN_SEND_PRIVATE_VALUES),
  KEY_GEN_COMPLETE(Constants.KEY_GEN_COMPLETE),
  ACTIVE_KEY(Constants.ACTIVE_KEY);

  private static Logger LOG = LogManager.getLogger();

  private static class Constants {
    private static final int UNKNOWN_KEY = 0;
    private static final int KEY_GEN_POST_XVALUE = 1;
    private static final int KEY_GEN_POST_COMMITMENT = 2;
    private static final int KEY_GEN_PUBLIC_VALUES = 3;
    private static final int KEY_GEN_SEND_PRIVATE_VALUES = 4;
    private static final int KEY_GEN_COMPLETE = 5;
    private static final int ACTIVE_KEY = 6;
  }

  public int value;

  KeyStatus(final int val) {
    this.value = val;
  }

  public static KeyStatus create(final int val) {
    switch (val) {
      case Constants.UNKNOWN_KEY:
        return UNKNOWN_KEY;
      case Constants.KEY_GEN_POST_XVALUE:
        return KEY_GEN_POST_XVALUE;
      case Constants.KEY_GEN_POST_COMMITMENT:
        return KEY_GEN_POST_COMMITMENT;
      case Constants.KEY_GEN_PUBLIC_VALUES:
        return KEY_GEN_PUBLIC_VALUES;
      case Constants.KEY_GEN_COMPLETE:
        return KEY_GEN_COMPLETE;
      case Constants.ACTIVE_KEY:
        return ACTIVE_KEY;
      case Constants.KEY_GEN_SEND_PRIVATE_VALUES:
        return KEY_GEN_PRIVATE_VALUES;
      default:
        String error = "Unknown KeyStatus: " + val;
        LOG.error(error);
        throw new RuntimeException(error);
    }
  }
}
