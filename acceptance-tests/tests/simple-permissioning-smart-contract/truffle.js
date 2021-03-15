// Copyright ConsenSys AG.
// SPDX-License-Identifier: Apache-2.0
const PrivateKeyProvider = require('truffle-hdwallet-provider');

// address 627306090abaB3A6e1400e9345bC60c78a8BEf57
const privateKey = 'c87509a1c067bbde78beb793e6fa76530b6382a4c0241e5e4a9ec0a0f44dc0d3';
const localDev = 'http://127.0.0.1:8545';

const privateKeyProvider = new PrivateKeyProvider(privateKey, localDev);

module.exports = {
  networks: {
    development: {
      host: '127.0.0.1',
      port: 7545,
      network_id: '*',
    },
    devwallet: {
      provider: privateKeyProvider,
      network_id: '2018',
    },
  },
};
