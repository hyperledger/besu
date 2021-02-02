// Copyright ConsenSys AG.
// SPDX-License-Identifier: Apache-2.0
const TestPermissioning = artifacts.require('SimpleAccountPermissioning.sol');
var proxy;

var address1 = "0x627306090abaB3A6e1400e9345bC60c78a8BEf57";
var address2 = "0xfe3b557e8fb62b89f4916b721be55ceb828dbd73";

var value = 99;
var gasPrice = 88;
var gasLimit = 77;
var payload = "0x1234";

contract('Permissioning: Accounts', () => {
  it('TEST CONTRACT permits ANY transaction when account whitelist is empty', async () => {
    proxy = await TestPermissioning.new();
    let permitted = await proxy.transactionAllowed(address1, address2, value, gasPrice, gasLimit, payload);
    assert.equal(permitted, true, 'expected ANY tx to be permitted since whitelist is empty');
  });

  it('Should add an account to the whitelist and then permit that node', async () => {
    await proxy.addAccount(address1);
    let permitted = await proxy.transactionAllowed(address1, address2, value, gasPrice, gasLimit, payload);
    assert.equal(permitted, true, 'added address1: expected tx1 to be permitted');

    // await another
    await proxy.addAccount(address2);
    permitted = await proxy.transactionAllowed(address2, address1, value, gasPrice, gasLimit, payload);
    assert.equal(permitted, true, 'added address2: expected tx2 to be permitted');

    // first one still permitted
    permitted = await proxy.transactionAllowed(address1, address2, value, gasPrice, gasLimit, payload);
    assert.equal(permitted, true, 'expected tx from address1 to still be permitted');
  });

  it('Should remove an account from the whitelist and then NOT permit that account to send tx', async () => {
    await proxy.removeAccount(address2);
    let permitted = await proxy.transactionAllowed(address2, address1, value, gasPrice, gasLimit, payload);
    assert.equal(permitted, false, 'expected removed account (address2) NOT permitted to send tx');

    // first one still permitted
    permitted = await proxy.transactionAllowed(address1, address2, value, gasPrice, gasLimit, payload);
    assert.equal(permitted, true, 'expected tx from address1 to still be permitted');

    // remove remaining account from whitelist
    await proxy.removeAccount(address1);
    permitted = await proxy.transactionAllowed(address2, address1, value, gasPrice, gasLimit, payload);
    assert.equal(permitted, true, 'whitelist now empty so ANY account now permitted to send tx');

    // first one still permitted
    permitted = await proxy.transactionAllowed(address1, address2, value, gasPrice, gasLimit, payload);
    assert.equal(permitted, true, 'expected tx from address1 to still be permitted');
  });
});
