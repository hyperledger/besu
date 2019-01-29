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
package tech.pegasys.pantheon.tests.acceptance.dsl.transaction;

import tech.pegasys.pantheon.ethereum.core.Address;

import java.util.List;
import java.util.Map;

import org.web3j.protocol.core.Response;

public class ResponseTypes {
  public static class ProposeResponse extends Response<Boolean> {}

  public static class DiscardResponse extends Response<Boolean> {}

  public static class SignersBlockResponse extends Response<List<Address>> {}

  public static class ProposalsResponse extends Response<Map<Address, Boolean>> {}

  public static class AddAccountsToWhitelistResponse extends Response<Boolean> {}

  public static class RemoveAccountsFromWhitelistResponse extends Response<Boolean> {}

  public static class GetAccountsWhitelistResponse extends Response<List<String>> {}

  public static class AddNodeResponse extends Response<Boolean> {}

  public static class RemoveNodeResponse extends Response<Boolean> {}

  public static class GetNodesWhitelistResponse extends Response<List<String>> {}
}
