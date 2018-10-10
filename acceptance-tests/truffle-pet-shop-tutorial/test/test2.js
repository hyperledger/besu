/* global artifacts contract describe assert it */

const TestAdoption = artifacts.require('Adoption.sol');
var proxy;
contract('Adoption 2', () => {
  describe('Function: adopt pet 3', () => {
    it('Should successfully adopt pet within range', async () => {
      proxy = await TestAdoption.new();

      await proxy.adopt(3);
      assert(true, 'expected adoption of pet within range to succeed');

      const isAdopted = await proxy.isAdopted(3);
      assert.equal(isAdopted, true, 'expected pet 3 to be adopted (adopted in this test method)');
    });

    it('Pet 2 should NOT be adopted (was adopted in a different test file)', async () => {
      // check status of pet2
      const isAdopted = await proxy.isAdopted(2);

      assert.equal(isAdopted, false, 'expected pet 2 to NOT be adopted (was adopted in a different test file)');
    });
  });
});
