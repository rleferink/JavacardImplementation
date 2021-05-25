import javacard.framework.*;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.smartcardio.CardException;
import javax.sound.midi.SysexMessage;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.Arrays;
import javax.crypto.Cipher;

import static javacard.framework.JCSystem.makeTransientByteArray;

public class LoyaltyApplet extends Applet implements ISO7816 {


    private static final byte X = 0;
    private AppUtil.AppMode currentMode;

    private short[] enteredValue;
    private short m;
    private byte[] lastOp;
    private boolean[] lastKeyWasDigit;
    private short balance = 100; // Initial card balance

    // code of instruction byte in the command APDU header
    final static byte SEND_CERTIFICATE = (byte) 0x20;
    final static byte ACK_ONLINE = (byte) 0x21;
    final static byte DECREASE_BALANCE = (byte) 0x22;

    String card = "certificate card";

    public LoyaltyApplet() {
        enteredValue = JCSystem.makeTransientShortArray((short) 1, JCSystem.CLEAR_ON_RESET);
        lastOp = makeTransientByteArray((short) 1, JCSystem.CLEAR_ON_RESET);
        lastKeyWasDigit = JCSystem.makeTransientBooleanArray((short) 1, JCSystem.CLEAR_ON_RESET);
        m = 0;
        register();

        //TODO set keys in the certificates
        //TODO obtain keys from certificate
        try {
            //generate key pair
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair pair = generator.generateKeyPair();
            PrivateKey privateKey = pair.getPrivate();
            PublicKey publicKey = pair.getPublic();

            //set string which needs to be encrypted
            String secretMessage = "1234";
            Cipher encryptCipher = Cipher.getInstance("RSA");
            encryptCipher.init(Cipher.ENCRYPT_MODE,publicKey);

            //create byte[] to store encrypted message
            byte[] secretMessageBytes = secretMessage.getBytes(StandardCharsets.UTF_8);
            byte[] encryptedMessageBytes = encryptCipher.doFinal(secretMessageBytes);

            //create decrypt cipher
            Cipher decryptCipher = Cipher.getInstance("RSA");
            decryptCipher.init(Cipher.DECRYPT_MODE, privateKey);

            //create byte[] to store decrypted message and then into a string
            byte[] decryptedMessageBytes = decryptCipher.doFinal(encryptedMessageBytes);
            String decryptedMessage = new String(decryptedMessageBytes, StandardCharsets.UTF_8);

            //check if original message is equal to the decrypted message
            if(secretMessage.equals(decryptedMessage)){
                System.out.println("Loyalty Applet: messages are equal");
            };

        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException e) {
            e.printStackTrace();
        } catch (IllegalBlockSizeException e) {
            e.printStackTrace();
        } catch (BadPaddingException e) {
            e.printStackTrace();
        }
    }

    public static void install(byte[] buffer, short offset, byte length) throws SystemException {
        new LoyaltyApplet();
    }

    @Override
    public void process(APDU apdu) throws ISOException {
        byte[] buffer = apdu.getBuffer();
        byte ins = buffer[OFFSET_INS];
        byte P1 = buffer[OFFSET_P1];


        /* Ignore the APDU that selects this applet... */
        if (selectingApplet()) {
            return;
        }

        AppUtil.AppMode insAsEnum = null;
        for (AppUtil.AppMode i : AppUtil.AppMode.values()) {
            if (i.mode == ins) {
                insAsEnum = i;
            }
        }

        switch (insAsEnum) {
            case ADD:
                //instruction: ADD
                currentMode = AppUtil.AppMode.ADD;
                if (P1 == AppUtil.AppComState.SEND_CERTIFICATE.mode){
                    sendCertificateAndCounter(apdu, buffer);
                    System.out.println("");
                }
                else if (P1 == AppUtil.AppComState.SEND_AMOUNT_CHECK.mode){
                    checkAmountAndIncreaseBalance(apdu, buffer);
                    System.out.println("");
                }
                break;

            case SPEND:
                //instruction: SPEND
                currentMode= AppUtil.AppMode.SPEND;
                if (P1 == AppUtil.AppComState.SEND_CERTIFICATE.mode){
                    sendCertificateAndCounter(apdu, buffer);
                    System.out.println("");
                }
                else if (P1 == AppUtil.AppComState.SEND_AMOUNT_CHECK.mode){
                    checkAmountAndDecreaseBalance(apdu, buffer);
                    System.out.println("");
                }
                break;

            case VIEW:
                //instruction: VIEW
                currentMode= AppUtil.AppMode.VIEW;
                view_balance(apdu, buffer);

                break;
            default:
                ISOException.throwIt(SW_INS_NOT_SUPPORTED);
        }
    }

    private void sendCertificateAndCounter(APDU apdu, byte[] buffer){
        short le = -1;
        le = apdu.setOutgoing();
        if (le < 30) {
            ISOException.throwIt((short) (SW_WRONG_LENGTH | 30));
        }

        short buffer_size = (short) buffer[4];
        short counter = (short) buffer[5];
        String certificate_terminal = new String(Arrays.copyOfRange(buffer,6,buffer_size+5));
        if(certificate_terminal.equals("certificate POSTerminal")){
            System.out.println("Certificate  POSTerminal CORRECT");
        }
        else{
            return;
        }

        counter += 1;
        System.out.println("C -> T: " + counter);
        byte[] counterr = {(byte) counter};
        byte[] certificate = card.getBytes();
        byte[] send = new byte[counterr.length + certificate.length];
        System.arraycopy(counterr, 0, send, 0, counterr.length);
        System.arraycopy(certificate, 0, send, counterr.length, certificate.length);
        apdu.setOutgoingLength((short) 30); // Must be the same as expected length at i4 at the caller.
        System.arraycopy(send, 0, buffer, 0, send.length);
        apdu.sendBytes((short) 0, (short) 30);
    }

    private void checkAmountAndIncreaseBalance(APDU apdu, byte[] buffer){
        short le = -1;
        le = apdu.setOutgoing();
        if (le < 2) {
            ISOException.throwIt((short) (SW_WRONG_LENGTH | 4));
        }

        short counter = (short) buffer[5];
        short amount = (short) buffer[6];
        byte[] yesNo = {(byte)0};
        System.out.println(amount);
        balance += amount;


        counter += 1;
        System.out.println("C -> T: " + counter);
        byte[] counterr = {(byte) counter};
        byte[] send = new byte[counterr.length + yesNo.length];
        System.arraycopy(counterr, 0, send, 0, counterr.length);
        System.arraycopy(yesNo, 0, send, counterr.length, yesNo.length);
        apdu.setOutgoingLength((short) 2); // Must be the same as expected length at i4 at the caller.
        System.arraycopy(send, 0, buffer, 0, send.length);
        apdu.sendBytes((short) 0, (short) 2);
    }

    private void checkAmountAndDecreaseBalance(APDU apdu, byte[] buffer){
        short le = -1;
        le = apdu.setOutgoing();
        if (le < 2) {
            ISOException.throwIt((short) (SW_WRONG_LENGTH | 4));
        }

        short counter = (short) buffer[5];
        short amount = (short) buffer[6];
        byte[] yesNo = {(byte)0};
        System.out.println(amount);
        if(amount <= balance){
            System.out.println("Amount <= balance");
            yesNo[0] = (byte)1;
            balance -= amount;
        }
        else{
            return;
        }

        counter += 1;
        System.out.println("C -> T: " + counter);
        byte[] counterr = {(byte) counter};
        byte[] send = new byte[counterr.length + yesNo.length];
        System.arraycopy(counterr, 0, send, 0, counterr.length);
        System.arraycopy(yesNo, 0, send, counterr.length, yesNo.length);
        apdu.setOutgoingLength((short) 2); // Must be the same as expected length at i4 at the caller.
        System.arraycopy(send, 0, buffer, 0, send.length);
        apdu.sendBytes((short) 0, (short) 2);
    }

    private void view_balance(APDU apdu, byte[] buffer){
        short le = -1;
        le = apdu.setOutgoing();
        if (le < 1) {
            ISOException.throwIt((short) (SW_WRONG_LENGTH | 1));
        }

        byte[] send_balance = {(byte) balance};
        apdu.setOutgoingLength((short) 1); // Must be the same as expected length at i4 at the caller.
        System.arraycopy(send_balance, 0, buffer, 0, send_balance.length);
        apdu.sendBytes((short) 0, (short) 1);
    }


/*    private void readBuffer(APDU apdu, byte[] dest, short offset, short length) {
        System.out.println("\nCard: Command receiving");
        byte[] buf = apdu.getBuffer();
        short readCount = apdu.setIncomingAndReceive();
        short i = 0;
        System.out.println("readCount: " + readCount);
        //System.out.println("length buffer: " + buf.length);
        System.out.println("buf[5]: " + buf[5]);
        Util.arrayCopy(buf,OFFSET_CDATA,dest,offset,readCount);
        while ((short)(i + readCount) < length) {
            i += readCount;
            offset += readCount;
            readCount = (short)apdu.receiveBytes(OFFSET_CDATA);
            Util.arrayCopy(buf,OFFSET_CDATA,dest,offset,readCount);
        }
    }
 */

/*    private void send_certificate_and_nonce(byte[] buffer, APDU apdu){
        System.out.println("STEP 1 - send certificate and nonce");
        //byte[] buffer = apdu.getBuffer();
        //short byteRead = (apdu.setIncomingAndReceive());
        //System.out.println("byteRead: " + byteRead);
        //if (byteRead != 1) ISOException.throwIt(SW_WRONG_LENGTH);
        //TODO: receive and validate certificate of terminal
        //byte certificate = buffer[OFFSET_CDATA]

        //TODO: send certificate of card back with nonce
        short nonce = (short) 11;
        System.out.println("length of buffer: " + buffer.length);
        apdu.setOutgoing();
        System.out.println("1");
        try {
            //Util.setShort(buffer, (short) 0, nonce);
        }
        catch (TransactionException c){
            System.out.println("Exception");
            return;
        }
        System.out.println("2");
        apdu.setOutgoingLength((short)2);
        System.out.println("3");
        apdu.sendBytes((short) 0, (short)2);
        System.out.println("Bytes sent back to Terminal\n");
    }
 */

    private void ack_online(APDU apdu){
        System.out.println("ack online");
    }

    private void decrease_balance(APDU apdu){
        System.out.println("decrease balance");
    }

    byte[] getBytes(BigInteger big) {
        byte[] data = big.toByteArray();
        if (data[0] == 0) {
            byte[] tmp = data;
            data = new byte[tmp.length - 1];
            System.arraycopy(tmp, 1, data, 0, tmp.length - 1);
        }
        return data;
    }
}
