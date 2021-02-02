// Copyright ConsenSys AG.
// SPDX-License-Identifier: Apache-2.0
var Migrations = artifacts.require("./Migrations.sol");

module.exports = function(deployer) {
  deployer.deploy(Migrations);
};
