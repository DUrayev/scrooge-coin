import org.junit.Before;
import org.junit.Test;

import java.math.BigInteger;
import java.security.*;

import static org.junit.Assert.assertEquals;

public class HandleTxTest {

    private KeyPair pk_scrooge;
    private KeyPair pk_alice;
    private HandleTxTest.Tx tx;
    private HandleTxTest.Tx tx2;
    private HandleTxTest.Tx tx3;
    private HandleTxTest.Tx tx4;
    private HandleTxTest.Tx tx0;
    private TxHandler txHandler;
    private MaxFeeTxHandler maxFeeTxHandler;


    @Before
    public void preparation() throws NoSuchAlgorithmException, SignatureException {
        /*
         * Generate key pairs, for Scrooge & Alice
         */
        pk_scrooge = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        pk_alice   = KeyPairGenerator.getInstance("RSA").generateKeyPair();

        /*
         * Set up the root transaction:
         *
         * Generating a root transaction tx out of thin air, so that Scrooge owns a coin of value 10
         * By thin air I mean that this tx will not be validated, I just need it to get
         * a proper Transaction.Output which I then can put in the UTXOPool, which will be passed
         * to the TXHandler.
         */
        tx = new HandleTxTest.Tx();
        tx.addOutput(10, pk_scrooge.getPublic());

        // This value has no meaning, but tx.getRawDataToSign(0) will access it in prevTxHash;
        byte[] initialHash = BigInteger.valueOf(0).toByteArray();
        tx.addInput(initialHash, 0);

        tx.signTx(pk_scrooge.getPrivate(), 0);

        /*
         * Set up the UTXOPool
         */
        // The transaction output of the root transaction is the initial unspent output.
        UTXOPool utxoPool = new UTXOPool();
        UTXO utxo = new UTXO(tx.getHash(),0);
        utxoPool.addUTXO(utxo, tx.getOutput(0));

        txHandler = new TxHandler(utxoPool);
        maxFeeTxHandler = new MaxFeeTxHandler(utxoPool);

        /*
         * Set up a test Transaction
         */
        tx2 = new HandleTxTest.Tx();

        // the Transaction.Output of tx at position 0 has a value of 10
        tx2.addInput(tx.getHash(), 0);

        // I split the coin of value 10 into 3 coins and send all of them for simplicity to
        // the same address (Alice)
        tx2.addOutput(5, pk_alice.getPublic());
        tx2.addOutput(4, pk_alice.getPublic());
        // Note that in the real world fixed-point types would be used for the values, not doubles.
        // Doubles exhibit floating-point rounding errors. This type should be for example BigInteger
        // and denote the smallest coin fractions (Satoshi in Bitcoin).

        // There is only one (at position 0) Transaction.Input in tx2
        // and it contains the coin from Scrooge, therefore I have to sign with the private key from Scrooge
        tx2.signTx(pk_scrooge.getPrivate(), 0);


        tx3 = new HandleTxTest.Tx();
        tx3.addInput(tx2.getHash(), 0);
        tx3.addOutput(3, pk_scrooge.getPublic());

        tx3.signTx(pk_alice.getPrivate(), 0);

        tx4 = new HandleTxTest.Tx();
        tx4.addInput(tx3.getHash(), 0);
        tx4.addOutput(0, pk_alice.getPublic());
        tx4.signTx(pk_scrooge.getPrivate(), 0);

        tx0 = new HandleTxTest.Tx();
        tx0.addInput(tx4.getHash(), 0);
        tx0.addOutput(0, pk_scrooge.getPublic());
        tx0.signTx(pk_alice.getPrivate(), 0);
    }
    @Test
    public void testOneValidTransaction() throws SignatureException {
        assertEquals(1, txHandler.handleTxs(new Transaction[]{tx2}).length);
    }

    @Test
    public void testTwoValidOrderedTransaction() throws SignatureException {
        assertEquals(2, txHandler.handleTxs(new Transaction[]{tx2, tx3}).length);
    }

    @Test
    public void testTwoValidUnorderedTransaction() throws SignatureException {
        assertEquals(2, txHandler.handleTxs(new Transaction[]{tx3, tx2}).length);
    }

    @Test
    public void testThreeValidOrderedTransaction() throws SignatureException {
        assertEquals(3, txHandler.handleTxs(new Transaction[]{tx2, tx3, tx4}).length);
    }
    @Test
    public void testThreeValidUnorderedTransaction() throws SignatureException {
        assertEquals(3, txHandler.handleTxs(new Transaction[]{tx4, tx3, tx2}).length);
    }
    @Test
    public void testThreeValidUnorderedTransaction2() throws SignatureException {
        assertEquals(3, txHandler.handleTxs(new Transaction[]{tx4, tx2, tx3}).length);
    }
    @Test
    public void testThreeValidUnorderedTransaction3() throws SignatureException {
        assertEquals(3, txHandler.handleTxs(new Transaction[]{tx3, tx2, tx4}).length);
    }
    @Test
    public void testThreeValidUnorderedTransaction4() throws SignatureException {
        assertEquals(3, txHandler.handleTxs(new Transaction[]{tx3, tx4, tx2}).length);
    }
    @Test
    public void testThreeValidUnorderedTransaction5() throws SignatureException {
        assertEquals(3, txHandler.handleTxs(new Transaction[]{tx2, tx4, tx3}).length);
    }

    @Test
    public void testInvalidTransaction() throws SignatureException {
        assertEquals(0, txHandler.handleTxs(new Transaction[]{tx3}).length);
    }
    @Test
    public void testInvalidTransaction2() throws SignatureException {
        assertEquals(0, txHandler.handleTxs(new Transaction[]{tx4}).length);
    }

    @Test
    public void testTwoInvalidTransaction() throws SignatureException {
        assertEquals(0, txHandler.handleTxs(new Transaction[]{tx3, tx4}).length);
    }
    @Test
    public void testTwoInvalidTransaction2() throws SignatureException {
        assertEquals(0, txHandler.handleTxs(new Transaction[]{tx4, tx3}).length);
    }

    @Test
    public void testDuplicatedTransaction() throws SignatureException {
        assertEquals(3, txHandler.handleTxs(new Transaction[]{tx2, tx3, tx4, tx2}).length);
    }
    @Test
    public void testDuplicatedTransaction2() throws SignatureException {
        assertEquals(3, txHandler.handleTxs(new Transaction[]{tx2, tx3, tx4, tx3}).length);
    }
    @Test
    public void testDuplicatedTransaction3() throws SignatureException {
        assertEquals(3, txHandler.handleTxs(new Transaction[]{tx2, tx3, tx4, tx4}).length);
    }
    @Test
    public void testDuplicatedTransaction4() throws SignatureException {
        assertEquals(3, txHandler.handleTxs(new Transaction[]{tx4, tx3, tx4, tx2}).length);
    }
    @Test
    public void testDuplicatedTransaction5() throws SignatureException {
        assertEquals(3, txHandler.handleTxs(new Transaction[]{tx4, tx3, tx2, tx2}).length);
    }

    @Test
    public void testSequenceOfTransactions() throws SignatureException {
        assertEquals(1, txHandler.handleTxs(new Transaction[]{tx2}).length);
        assertEquals(1, txHandler.handleTxs(new Transaction[]{tx3}).length);
        assertEquals(1, txHandler.handleTxs(new Transaction[]{tx4}).length);

    }
    @Test
    public void testSequenceOfTransactions2() throws SignatureException {
        assertEquals(1, txHandler.handleTxs(new Transaction[]{tx2}).length);
        assertEquals(2, txHandler.handleTxs(new Transaction[]{tx4, tx3}).length);
    }
    @Test
    public void testSequenceOfTransactions3() throws SignatureException {
        assertEquals(1, txHandler.handleTxs(new Transaction[]{tx2}).length);
        assertEquals(2, txHandler.handleTxs(new Transaction[]{tx3, tx4}).length);
    }
    @Test
    public void testSequenceOfTransactions4() throws SignatureException {
        assertEquals(2, txHandler.handleTxs(new Transaction[]{tx2, tx3}).length);
        assertEquals(1, txHandler.handleTxs(new Transaction[]{tx4}).length);
    }
    @Test
    public void testSequenceOfTransactions5() throws SignatureException {
        assertEquals(2, txHandler.handleTxs(new Transaction[]{tx3, tx2}).length);
        assertEquals(1, txHandler.handleTxs(new Transaction[]{tx4}).length);
    }

    @Test
    public void testSendTheSameTx() throws SignatureException {
        Transaction[] txs = txHandler.handleTxs(new Transaction[]{tx2, tx3});
        assertEquals(0, txHandler.handleTxs(txs).length);
    }

    @Test
    public void testTxWithZeroOutputAtTheEnd() throws SignatureException {
        assertEquals(4, txHandler.handleTxs(new Transaction[]{tx2, tx3, tx4, tx0}).length);
    }

    @Test
    public void testMaxFeeTxHandler() throws SignatureException {
        assertEquals(1, maxFeeTxHandler.handleTxs(new Transaction[]{tx4, tx3, tx2}).length);
    }


    public static class Tx extends Transaction {
        public void signTx(PrivateKey sk, int input) throws SignatureException {
            Signature sig = null;
            try {
                sig = Signature.getInstance("SHA256withRSA");
                sig.initSign(sk);
                sig.update(this.getRawDataToSign(input));
            } catch (NoSuchAlgorithmException | InvalidKeyException e) {
                throw new RuntimeException(e);
            }
            this.addSignature(sig.sign(),input);
            // Note that this method is incorrectly named, and should not in fact override the Java
            // object finalize garbage collection related method.
            this.finalize();
        }
    }
}
