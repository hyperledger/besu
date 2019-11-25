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
package org.hyperledger.besu.enclave;

import org.hyperledger.besu.enclave.types.CreatePrivacyGroupRequest;
import org.hyperledger.besu.enclave.types.DeletePrivacyGroupRequest;
import org.hyperledger.besu.enclave.types.FindPrivacyGroupRequest;
import org.hyperledger.besu.enclave.types.PrivacyGroup;
import org.hyperledger.besu.enclave.types.ReceiveRequest;
import org.hyperledger.besu.enclave.types.ReceiveResponse;
import org.hyperledger.besu.enclave.types.SendRequest;
import org.hyperledger.besu.enclave.types.SendResponse;

public class Enclave {

  private static final String ORION = "application/vnd.orion.v1+json";
  private static final String JSON = "application/json";

  private final RequestTransmitter requestTransmitter;

  public Enclave(final RequestTransmitter requestTransmitter) {
    this.requestTransmitter = requestTransmitter;
  }

  public boolean upCheck() {
    try {
      final String upcheckResponse =
          requestTransmitter.getRequest("", "", "/upcheck", String.class);
      return upcheckResponse.equals("I'm up!");
    } catch (final Exception e) {
      return false;
    }
  }

  public SendResponse send(final SendRequest content) {
    return requestTransmitter.postRequest(JSON, content, "/send", SendResponse.class);
  }

  public ReceiveResponse receive(final ReceiveRequest content) {
    return requestTransmitter.postRequest(ORION, content, "/receive", ReceiveResponse.class);
  }

  public PrivacyGroup createPrivacyGroup(final CreatePrivacyGroupRequest content) {
    return requestTransmitter.postRequest(JSON, content, "/createPrivacyGroup", PrivacyGroup.class);
  }

  public String deletePrivacyGroup(final DeletePrivacyGroupRequest content) {
    return requestTransmitter.postRequest(JSON, content, "/deletePrivacyGroup", String.class);
  }

  public PrivacyGroup[] findPrivacyGroup(final FindPrivacyGroupRequest content) {
    return requestTransmitter.postRequest(JSON, content, "/findPrivacyGroup", PrivacyGroup[].class);
  }
}
