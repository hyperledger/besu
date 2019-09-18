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
package org.hyperledger.besu.cli.subcommands.networkcreate.mapping;

import java.util.ArrayList;
import java.util.List;

public class InitConfigurationErrorHandler {

  private List<InitConfigurationError> errors = new ArrayList<>();

  public void add(final String item, final String value, final String message) {
    errors.add(new InitConfigurationError(item, value, message));
  }

  public List<InitConfigurationError> getErrors() {
    return errors;
  }

  @Override
  public String toString() {
    String errorString =
        String.format("%1$s %2$s:%n", errors.size(), errors.size() > 1 ? "errors" : "error");
    for (InitConfigurationError error : errors) {
      errorString += String.format("- %1$s: (%2$s) %3$s%n", error.item, error.value, error.message);
    }
    return errorString;
  }

  static class InitConfigurationError {

    private final String item;
    private final String value;
    private final String message;

    InitConfigurationError(final String item, final String value, final String message) {
      this.item = item;
      this.value = value;
      this.message = message;
    }

    public String getItem() {
      return item;
    }

    public String getValue() {
      return value;
    }

    public String getMessage() {
      return message;
    }
  }
}
