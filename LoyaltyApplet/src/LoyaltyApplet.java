import javacard.framework.*;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.smartcardio.CardException;
import javax.sound.midi.SysexMessage;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.EncodedKeySpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.sql.Timestamp;
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

    boolean personalized = false;

    byte[] cardID = null;
    byte[] terminalID = null;
    byte[] certificate = null;
    PublicKey publicKey = null;
    PrivateKey privateKey = null;
    PublicKey publicKeyCA = null;

    byte[] incoming = null;
    int incomingIndex = 0;
    int lengthIncoming = 0;

    int counter = 1;

    //card keeps track of the most recent 100 transactions
    int lastTransactionIndex = 0;
    Byte [][] transactions = new Byte[100][4];

    public LoyaltyApplet() {
        enteredValue = JCSystem.makeTransientShortArray((short) 1, JCSystem.CLEAR_ON_RESET);
        lastOp = makeTransientByteArray((short) 1, JCSystem.CLEAR_ON_RESET);
        lastKeyWasDigit = JCSystem.makeTransientBooleanArray((short) 1, JCSystem.CLEAR_ON_RESET);
        m = 0;
        register();
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
                if (P1 == AppUtil.AppComState.SEND_LENGTH.mode){
                    receiveLength(apdu, buffer);
                }
                if (P1 == AppUtil.AppComState.SEND_INFO.mode){
                    receiveInfo(apdu, buffer);
                }
                if (P1 == AppUtil.AppComState.SEND_CERTIFICATE.mode){
                    try {
                        sendCertificateAndCounter(apdu, buffer);
                    } catch (NoSuchAlgorithmException e) {
                        e.printStackTrace();
                    } catch (NoSuchPaddingException e) {
                        e.printStackTrace();
                    } catch (InvalidKeyException e) {
                        e.printStackTrace();
                    } catch (IllegalBlockSizeException e) {
                        e.printStackTrace();
                    } catch (BadPaddingException e) {
                        e.printStackTrace();
                    }
                    System.out.println("");
                }
                else if (P1 == AppUtil.AppComState.SEND_AMOUNT_CHECK.mode){
                    IncreaseBalance(apdu, buffer, cardID, transactions);
                    System.out.println("");
                }
                break;

            case SPEND:
                currentMode= AppUtil.AppMode.SPEND;
                if (P1 == AppUtil.AppComState.SEND_CERTIFICATE.mode){
                    try {
                        sendCertificateAndCounter(apdu, buffer);
                    } catch (NoSuchAlgorithmException e) {
                        e.printStackTrace();
                    } catch (NoSuchPaddingException e) {
                        e.printStackTrace();
                    } catch (InvalidKeyException e) {
                        e.printStackTrace();
                    } catch (IllegalBlockSizeException e) {
                        e.printStackTrace();
                    } catch (BadPaddingException e) {
                        e.printStackTrace();
                    }
                    System.out.println("");
                }
                else if (P1 == AppUtil.AppComState.SEND_AMOUNT_CHECK.mode){
                    checkAmountAndDecreaseBalance(apdu, buffer, cardID, transactions);
                    System.out.println("");
                }
                break;

            case VIEW:
                currentMode= AppUtil.AppMode.VIEW;
                view_balance(apdu, buffer);
                break;
            case PERSONALIZE:
                currentMode = AppUtil.AppMode.PERSONALIZE;
                if (P1 == AppUtil.AppComState.SEND_LENGTH.mode){
                    receiveLength(apdu, buffer);
                }
                if (P1 == AppUtil.AppComState.SEND_INFO.mode){
                    receiveInfo(apdu, buffer);
                }
                if (P1 == AppUtil.AppComState.PROCESS_INFO.mode){
                    acceptInfoPersonalize(apdu, buffer);
                }
                break;
            default:
                ISOException.throwIt(SW_INS_NOT_SUPPORTED);
        }
    }

    private void receiveLength(APDU apdu, byte[] buffer){
        //Return directly when already personalized with a value of 0
        if(personalized) {
            short le = apdu.setOutgoing();
            apdu.setOutgoingLength(le);
            byte[] send_answer = {(byte)0};
            System.arraycopy(send_answer, 0, buffer, 0, send_answer.length);
            apdu.sendBytes((short) 0, le);
            return;
        }

        //Retrieve the length of the incoming message and creating a new byte[] for that message
        lengthIncoming = ByteBuffer.wrap(Arrays.copyOfRange(buffer, 5, 5+ 8)).getInt();
        incoming = new byte[lengthIncoming];
        incomingIndex = 0;

        //Return with 1
        short le = apdu.setOutgoing();
        byte[] send_answer = {(byte)1};
        apdu.setOutgoingLength(le);
        System.arraycopy(send_answer, 0, buffer, 0, send_answer.length);
        apdu.sendBytes((short) 0, le);
    }

    private void receiveInfo(APDU apdu, byte[] buffer){
        //Copy the incoming buffer into the previously made byte[] at the right position of incomingIndex
        if(lengthIncoming - incomingIndex >= 250){
            System.arraycopy(buffer, 5, incoming, incomingIndex, 250);
        } else {
            System.arraycopy(buffer, 5, incoming, incomingIndex, lengthIncoming - incomingIndex);
        }
        incomingIndex += 250;

        //Return with 1
        short le = apdu.setOutgoing();
        byte[] send_answer = {(byte)1};
        apdu.setOutgoingLength(le);
        System.arraycopy(send_answer, 0, buffer, 0, send_answer.length);
        apdu.sendBytes((short) 0, le);
    }

    private void acceptInfoPersonalize(APDU apdu, byte[] buffer) {
        //Get information out of incoming
        int IDLength = ByteBuffer.wrap(Arrays.copyOfRange(incoming, 0, 8)).getInt();
        byte[] cardIDBytes = Arrays.copyOfRange(incoming, 8, 8 + IDLength);
        cardID = cardIDBytes;
        int certLength =  ByteBuffer.wrap(Arrays.copyOfRange(incoming, 8 + IDLength, 8 + IDLength + 8)).getInt();
        byte[] certificateBytes = Arrays.copyOfRange(incoming, 8 + IDLength + 8, 8 + IDLength + 8 + certLength);
        certificate = certificateBytes;
        int pubKeyLength =  ByteBuffer.wrap(Arrays.copyOfRange(incoming, 8 + IDLength + 8 + certLength, 8 + IDLength + 8 + certLength + 8)).getInt();
        byte[] pubKeyBytes = Arrays.copyOfRange(incoming, 8 + IDLength + 8 + certLength + 8, 8 + IDLength + 8 + certLength + 8 + pubKeyLength);
        int privKeyLength =  ByteBuffer.wrap(Arrays.copyOfRange(incoming, 8 + IDLength + 8 + certLength + 8 + pubKeyLength, 8 + IDLength + 8 + certLength + 8 + pubKeyLength + 8)).getInt();
        byte[] privKeyBytes = Arrays.copyOfRange(incoming, 8 + IDLength + 8 + certLength + 8 + pubKeyLength + 8, 8 + IDLength + 8 + certLength + 8 + pubKeyLength + 8 + privKeyLength);
        int pubCALength = ByteBuffer.wrap(Arrays.copyOfRange(incoming, 8 + IDLength + 8 + certLength + 8 + pubKeyLength + 8 + privKeyLength, 8 + IDLength + 8 + certLength + 8 + pubKeyLength + 8 + privKeyLength + 8)).getInt();
        byte[] pubCABytes = Arrays.copyOfRange(incoming, 8 + IDLength + 8 + certLength + 8 + pubKeyLength + 8 + privKeyLength + 8, 8 + IDLength + 8 + certLength + 8 + pubKeyLength + 8 + privKeyLength + 8 + pubCALength);

        //Use a KeyFactory to regenerate the keys from the byte arrays
        try {
            KeyFactory kf = KeyFactory.getInstance("RSA");
            EncodedKeySpec pubKeySpec = new X509EncodedKeySpec(pubKeyBytes);
            publicKey = kf.generatePublic(pubKeySpec);
            EncodedKeySpec privKeySpec = new PKCS8EncodedKeySpec(privKeyBytes);
            privateKey = kf.generatePrivate(privKeySpec);
            EncodedKeySpec pubCASpec = new X509EncodedKeySpec(pubCABytes);
            publicKeyCA = kf.generatePublic(pubCASpec);
        } catch (Exception e) {
            e.printStackTrace();
        }

        //Set card to be personalized
        personalized = true;

        //Return byte 1
        short le = apdu.setOutgoing();
        byte[] send_answer = {(byte)1};
        apdu.setOutgoingLength(le);
        System.arraycopy(send_answer, 0, buffer, 0, send_answer.length);
        apdu.sendBytes((short) 0, le);
    }

    private void sendCertificateAndCounter(APDU apdu, byte[] buffer) throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException {
        //Get information out of incoming
        int IDLength = ByteBuffer.wrap(Arrays.copyOfRange(incoming, 0, 8)).getInt();
        byte[] terminalIDBytes = Arrays.copyOfRange(incoming, 8, 8 + IDLength);
        terminalID = terminalIDBytes;
        int certLength =  ByteBuffer.wrap(Arrays.copyOfRange(incoming, 8 + IDLength, 8 + IDLength + 8)).getInt();
        byte[] certificateBytesTerminal = Arrays.copyOfRange(incoming, 8 + IDLength + 8, 8 + IDLength + 8 + certLength);

        //TODO Verify certificate by creating a signature of info
        Certificate certificateTerminalCheck = new Certificate(certificateBytesTerminal);
        String expiryDate = certificateTerminalCheck.getExpiryDate();
        String issuerName = certificateTerminalCheck.getIssuerName();
        byte[] signatureCheck = certificateTerminalCheck.getSignature();
        PublicKey publicKeyTerminal = certificateTerminalCheck.getPublickKey();

        String info = terminalID + issuerName + expiryDate + publicKeyTerminal;
        byte[] infoBytes = info.getBytes(StandardCharsets.UTF_8);
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(infoBytes);

        Cipher decryptCipher = Cipher.getInstance("RSA");
        decryptCipher.init(Cipher.DECRYPT_MODE,publicKeyCA);
        byte[] decryptedSignature = decryptCipher.doFinal(signatureCheck);

        if(hash == decryptedSignature){
            System.out.println("certificate POS terminal is valid");
        }
        else{
            System.out.println("certificate POS terminal is NOT valid");
            return;
        }

        //certificate == signature of certificate?

        //return counter + certificate
        byte[] send = new byte[8 + certificate.length];
        System.out.println("length send certificate: " + certificate.length);
        byte[] counterArray = bigIntToByteArray(counter);
        System.arraycopy(counterArray, 0, send, 0, 8);
        System.arraycopy(certificate, 0, send, 8, certificate.length);
        apdu.setOutgoingLength((short) 30); // Must be the same as expected length at i4 at the caller.
        System.arraycopy(send, 0, buffer, 0, send.length);
        apdu.sendBytes((short) 0, (short) 30);
    }

    private byte[] bigIntToByteArray (int i){
        BigInteger bigInt = BigInteger.valueOf(i);
        return bigInt.toByteArray();
    }

    private void IncreaseBalance(APDU apdu, byte[] buffer, byte[] cardID, Byte[][] transactions){
        short le = -1;
        le = apdu.setOutgoing();
        if (le < 3) {
            ISOException.throwIt((short) (SW_WRONG_LENGTH | 4));
        }

        short counter = (short) buffer[5];
        int terminalId = buffer[6];
        short amount = (short) buffer[7];
        byte[] yesNo = {(byte)0};
        System.out.println("Amount: " + amount);
        JCSystem.beginTransaction(); // Make Persistent Transaction
        balance += amount;
        JCSystem.commitTransaction();

        counter += 1;
        System.out.println("C -> T: " + counter);
        byte[] counterr = {(byte) counter};
        byte[] send = new byte[counterr.length + cardID.length + yesNo.length];
        System.arraycopy(counterr, 0, send, 0, counterr.length);
        System.arraycopy(cardID, 0, send, counterr.length, cardID.length);
        System.arraycopy(yesNo, 0, send, counterr.length + cardID.length, yesNo.length);
        apdu.setOutgoingLength((short) 3); // Must be the same as expected length at i4 at the caller.
        System.arraycopy(send, 0, buffer, 0, send.length);
        apdu.sendBytes((short) 0, (short) 3);

        //store transaction
        java.sql.Timestamp timestamp = new Timestamp(System.currentTimeMillis());
        //transactions[lastTransactionIndex][0] = timestamp.toString().getBytes();
        transactions[lastTransactionIndex][0] = (byte)0;
        transactions[lastTransactionIndex][1] = (byte)0; //TODO: this needs to be cardID
        transactions[lastTransactionIndex][2] = (byte)terminalId;
        transactions[lastTransactionIndex][3] = (byte)amount;
        System.out.println("Transaction " + lastTransactionIndex + " " + transactions[lastTransactionIndex][0] + " " + transactions[lastTransactionIndex][1] + " " + transactions[lastTransactionIndex][2] + " " + transactions[lastTransactionIndex][3]);
        lastTransactionIndex+=1;

    }

    private void checkAmountAndDecreaseBalance(APDU apdu, byte[] buffer, byte[] cardID, Byte[][] transactions){
        short le = -1;
        le = apdu.setOutgoing();
        if (le < 2) {
            ISOException.throwIt((short) (SW_WRONG_LENGTH | 4));
        }

        short counter = (short) buffer[5];
        int terminalId = buffer[6];
        short amount = (short) buffer[7];
        byte[] yesNo = {(byte)0};
        System.out.println(amount);
        JCSystem.beginTransaction();
        if(amount <= balance){
            System.out.println("Amount <= balance");
            yesNo[0] = (byte)1;
            balance -= amount;
        }
        else{
            return;
        }
        JCSystem.commitTransaction();

        counter += 1;
        System.out.println("C -> T: " + counter);
        //send counter + cardId + yes/no
        byte[] counterr = {(byte) counter};
        byte[] send = new byte[counterr.length + cardID.length + yesNo.length];
        System.arraycopy(counterr, 0, send, 0, counterr.length);
        System.arraycopy(cardID, 0, send, counterr.length, cardID.length);
        System.arraycopy(yesNo, 0, send, counterr.length + cardID.length, yesNo.length);
        apdu.setOutgoingLength((short) 3); // Must be the same as expected length at i4 at the caller.
        System.arraycopy(send, 0, buffer, 0, send.length);
        apdu.sendBytes((short) 0, (short) 3);

        //store transaction
        //TODO store timestamp in transaction
        java.sql.Timestamp timestamp = new Timestamp(System.currentTimeMillis());
        //transactions[lastTransactionIndex][0] = timestamp.toString().getBytes();
        transactions[lastTransactionIndex][0] = (byte)0;
        transactions[lastTransactionIndex][1] = (byte)0; //TODO: this needs to be cardID
        transactions[lastTransactionIndex][2] = (byte)terminalId;
        transactions[lastTransactionIndex][3] = (byte)amount;
        System.out.println("Transaction " + lastTransactionIndex + ": " + transactions[lastTransactionIndex][0] + " " + transactions[lastTransactionIndex][1] + " " + transactions[lastTransactionIndex][2] + " " + transactions[lastTransactionIndex][3]);
        lastTransactionIndex+=1;
    }

    private void view_balance(APDU apdu, byte[] buffer){
        short le = apdu.setOutgoing();
        byte[] send_balance = {(byte) balance};
        apdu.setOutgoingLength(le);
        System.arraycopy(send_balance, 0, buffer, 0, send_balance.length);
        apdu.sendBytes((short) 0, le);
    }


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
