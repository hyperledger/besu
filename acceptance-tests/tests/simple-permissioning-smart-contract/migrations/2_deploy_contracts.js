// Copyright ConsenSys AG.
// SPDX-License-Identifier: Apache-2.0
var SimpleNodePermissioning = artifacts.require("SimpleNodePermissioning");
var SimpleAccountPermissioning = artifacts.require("SimpleAccountPermissioning");

module.exports = function(deployer) {
  deployer.deploy(SimpleNodePermissioning);
  deployer.deploy(SimpleAccountPermissioning);
};
