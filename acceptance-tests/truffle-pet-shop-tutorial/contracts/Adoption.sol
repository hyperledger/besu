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

contract Adoption {
    address[16] public adopters;
// Adopting a pet
function adopt(uint petId) public returns (uint) {
  require(petId >= 0 && petId <= 15);

  adopters[petId] = msg.sender;

  return petId;
}
// who has adopted this pet?
function getOwner(uint petId) public view returns (address) {
  require(petId >= 0 && petId <= 15);
  return adopters[petId];
}

// is this pet adopted?
function isAdopted(uint petId) public view returns (bool) {
  require(petId >= 0 && petId <= 15);
  return adopters[petId] != 0;
}
// Adopting a pet
function unadopt(uint petId) public returns (uint) {
  require(petId >= 0 && petId <= 15);

  adopters[petId] = 0;

  return petId;
}

// Retrieving the adopters
function getAdopters() public view returns (address[16]) {
  return adopters;
}
}
