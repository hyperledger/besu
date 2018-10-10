/* global artifacts contract describe assert it */

const TestAdoption = artifacts.require('Adoption.sol');
var proxy;
contract('Adoption 1', () => {
  describe('Function: adopt pet 1', () => {
    it('Should successfully adopt pet within range', async () => {
      proxy = await TestAdoption.new();
      await proxy.adopt(1);

      assert(true, 'expected adoption of pet within range to succeed');
    });

    it('Should catch an error and then return', async () => {
        try {
          await proxy.adopt(22);
        } catch (err) {
          assert(true, err.toString().includes('revert'), 'expected revert in message');

          return;
        }

      assert(false, 'did not catch expected error from petID out of range');
    });

    it('Should successfully adopt pet within range 2', async () => {
      await proxy.adopt(2);

      assert(true, 'expected adoption of pet within range to succeed');
    });
  });
});
