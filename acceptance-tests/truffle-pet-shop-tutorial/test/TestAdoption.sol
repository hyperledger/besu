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
pragma solidity ^0.4.17;

import "truffle/Assert.sol";
import "truffle/DeployedAddresses.sol";
import "../contracts/Adoption.sol";

contract TestAdoption {
  Adoption adoption = Adoption(DeployedAddresses.Adoption());
// Testing the adopt() function
function testUserCanAdoptPet() public {
  uint returnedId = adoption.adopt(8);

  uint expected = 8;

  Assert.equal(returnedId, expected, "Adoption of pet ID 8 should be recorded.");
}

// Testing retrieval of a single pet's owner
function testGetAdopterAddressByPetId() public {
  // Expected owner is this contract
  address expected = this;

  address adopter = adoption.adopters(8);

  Assert.equal(adopter, expected, "Owner of pet ID 8 should be recorded.");
}

// Testing retrieval of all pet owners
function testGetAdopterAddressByPetIdInArray() public {
  // Expected owner is this contract
  address expected = this;

  // Store adopters in memory rather than contract's storage
  address[16] memory adopters = adoption.getAdopters();

  Assert.equal(adopters[8], expected, "Owner of pet ID 8 should be recorded.");
}


// Testing the unadopt() function
function testUserCanUnadoptPet() public {
  uint returnedId = adoption.unadopt(8);

  uint expected = 8;

  Assert.equal(returnedId, expected, "Adoption of pet ID 8 should be recorded.");

  // Store adopters in memory rather than contract's storage
  address[16] memory adopters = adoption.getAdopters();
  Assert.equal(adopters[8], 0, "owner should be reset after unadopt");
}
}
