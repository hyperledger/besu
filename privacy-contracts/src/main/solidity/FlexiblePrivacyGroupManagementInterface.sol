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
pragma solidity ^0.6.0;
interface FlexiblePrivacyGroupManagementInterface {

    function addParticipants(bytes32[] calldata publicEnclaveKeys) external returns (bool);

    function removeParticipant(bytes32 participant) external returns (bool);

    function getParticipants() external view returns (bytes32[] memory);

    function lock() external;

    function unlock() external;

    function canExecute() external view returns (bool);

    function getVersion() external view returns (bytes32);

    function canUpgrade() external returns (bool);
}
