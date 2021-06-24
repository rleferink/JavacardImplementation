import javacard.framework.*;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.smartcardio.CardException;
import javax.sound.midi.SysexMessage;

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

    //TODO: Remove when switched to new data types
    String card = "certificate card";
    int cardId = 21;

    boolean personalized = false;

    byte[] cardID = null;
    byte[] certificate = null;
    PublicKey publicKey = null;
    PrivateKey privateKey = null;

    byte[] incoming = null;
    int incomingIndex = 0;
    int lengthIncoming = 0;

    //card keeps track of the most recent 100 transactions
    int lastTransactionIndex = 0;
    Byte [][] transactions = new Byte[100][4];

    public LoyaltyApplet() {
        enteredValue = JCSystem.makeTransientShortArray((short) 1, JCSystem.CLEAR_ON_RESET);
        lastOp = makeTransientByteArray((short) 1, JCSystem.CLEAR_ON_RESET);
        lastKeyWasDigit = JCSystem.makeTransientBooleanArray((short) 1, JCSystem.CLEAR_ON_RESET);
        m = 0;
        register();

        //TODO obtain keys from certificate

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
                System.out.println("add");
                if (P1 == AppUtil.AppComState.SEND_CERTIFICATE.mode){
                    sendCertificateAndCounter(apdu, buffer);
                    System.out.println("");
                }
                else if (P1 == AppUtil.AppComState.SEND_AMOUNT_CHECK.mode){
                    IncreaseBalance(apdu, buffer, cardId, transactions);
                    System.out.println("");
                }
                break;

            case SPEND:
                //instruction: SPEND
                currentMode= AppUtil.AppMode.SPEND;
                System.out.println("spend");
                if (P1 == AppUtil.AppComState.SEND_CERTIFICATE.mode){
                    sendCertificateAndCounter(apdu, buffer);
                    System.out.println("");
                }
                else if (P1 == AppUtil.AppComState.SEND_AMOUNT_CHECK.mode){
                    checkAmountAndDecreaseBalance(apdu, buffer, cardId, transactions);
                    System.out.println("");
                }
                break;

            case VIEW:
                //instruction: VIEW
                currentMode= AppUtil.AppMode.VIEW;
                System.out.println("view");
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
                    acceptInfo(apdu, buffer);
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

    private void acceptInfo(APDU apdu, byte[] buffer) {
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

        //Use a KeyFactory to regenerate the keys from the byte arrays
        try {
            KeyFactory kf = KeyFactory.getInstance("RSA");
            EncodedKeySpec pubKeySpec = new X509EncodedKeySpec(pubKeyBytes);
            publicKey = kf.generatePublic(pubKeySpec);
            EncodedKeySpec privKeySpec = new PKCS8EncodedKeySpec(privKeyBytes);
            privateKey = kf.generatePrivate(privKeySpec);
        } catch (Exception e) {
            e.printStackTrace();
        }

        //Set card to be personalized
        personalized = true;

        //Return with 1
        short le = apdu.setOutgoing();
        byte[] send_answer = {(byte)1};
        apdu.setOutgoingLength(le);
        System.arraycopy(send_answer, 0, buffer, 0, send_answer.length);
        apdu.sendBytes((short) 0, le);
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

    private void IncreaseBalance(APDU apdu, byte[] buffer, int cardId, Byte[][] transactions){
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
        byte[] cardNr = {(byte) cardId};
        byte[] send = new byte[counterr.length + cardNr.length + yesNo.length];
        System.arraycopy(counterr, 0, send, 0, counterr.length);
        System.arraycopy(cardNr, 0, send, counterr.length, cardNr.length);
        System.arraycopy(yesNo, 0, send, counterr.length + cardNr.length, yesNo.length);
        apdu.setOutgoingLength((short) 3); // Must be the same as expected length at i4 at the caller.
        System.arraycopy(send, 0, buffer, 0, send.length);
        apdu.sendBytes((short) 0, (short) 3);

        //store transaction
        java.sql.Timestamp timestamp = new Timestamp(System.currentTimeMillis());
        //transactions[lastTransactionIndex][0] = timestamp.toString().getBytes();
        transactions[lastTransactionIndex][0] = (byte)0;
        transactions[lastTransactionIndex][1] = (byte)cardId;
        transactions[lastTransactionIndex][2] = (byte)terminalId;
        transactions[lastTransactionIndex][3] = (byte)amount;
        System.out.println("Transaction " + lastTransactionIndex + " " + transactions[lastTransactionIndex][0] + " " + transactions[lastTransactionIndex][1] + " " + transactions[lastTransactionIndex][2] + " " + transactions[lastTransactionIndex][3]);
        lastTransactionIndex+=1;

    }

    private void checkAmountAndDecreaseBalance(APDU apdu, byte[] buffer, int cardNr, Byte[][] transactions){
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
        byte[] cardId = {(byte) cardNr};
        byte[] send = new byte[counterr.length + cardId.length + yesNo.length];
        System.arraycopy(counterr, 0, send, 0, counterr.length);
        System.arraycopy(cardId, 0, send, counterr.length, cardId.length);
        System.arraycopy(yesNo, 0, send, counterr.length + cardId.length, yesNo.length);
        apdu.setOutgoingLength((short) 3); // Must be the same as expected length at i4 at the caller.
        System.arraycopy(send, 0, buffer, 0, send.length);
        apdu.sendBytes((short) 0, (short) 3);

        //store transaction
        //TODO store timestamp in transaction
        java.sql.Timestamp timestamp = new Timestamp(System.currentTimeMillis());
        //transactions[lastTransactionIndex][0] = timestamp.toString().getBytes();
        transactions[lastTransactionIndex][0] = (byte)0;
        transactions[lastTransactionIndex][1] = (byte)cardNr;
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
