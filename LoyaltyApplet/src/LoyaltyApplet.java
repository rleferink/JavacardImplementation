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
    private AppUtil.AppMode currentMode;

    private short balance = 0; // Initial card balance

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

    byte[] sending = null;
    int sendingIndex = 0;
    int lengthSending = 0;

    int counter = 1;

    //card keeps track of the most recent 100 transactions
    int lastTransactionIndex = 0;
    Byte [][] transactions = new Byte[100][8];

    public LoyaltyApplet() {
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
                    receiveLength(apdu, buffer, currentMode.mode);
                }
                else if (P1 == AppUtil.AppComState.SEND_INFO.mode){
                    receiveInfo(apdu, buffer);
                }
                else if (P1 == AppUtil.AppComState.SEND_CERTIFICATE.mode){
                    sendCertificateAndCounter(apdu);
                }
                else if (P1 == AppUtil.AppComState.SEND_AMOUNT_CHECK.mode){
                    IncreaseBalance(apdu, buffer);
                }
                break;

            case SPEND:
                currentMode= AppUtil.AppMode.SPEND;
                if (P1 == AppUtil.AppComState.SEND_LENGTH.mode){
                    receiveLength(apdu, buffer, currentMode.mode);
                }
                else if (P1 == AppUtil.AppComState.SEND_INFO.mode){
                    receiveInfo(apdu, buffer);
                }
                else if (P1 == AppUtil.AppComState.SEND_CERTIFICATE.mode){
                    sendCertificateAndCounter(apdu);
                }
                else if (P1 == AppUtil.AppComState.SEND_AMOUNT_CHECK.mode){
                    checkAmountAndDecreaseBalance(apdu, buffer);
                }
                break;

            case VIEW:
                currentMode= AppUtil.AppMode.VIEW;
                view_balance(apdu, buffer);
                break;
            case PERSONALIZE:
                currentMode = AppUtil.AppMode.PERSONALIZE;
                if (P1 == AppUtil.AppComState.SEND_LENGTH.mode){
                    receiveLength(apdu, buffer, currentMode.mode);
                }
                else if (P1 == AppUtil.AppComState.SEND_INFO.mode){
                    receiveInfo(apdu, buffer);
                }
                else if (P1 == AppUtil.AppComState.PROCESS_INFO.mode){
                    acceptInfoPersonalize(apdu, buffer);
                }
                break;
            case DATA_SENDING:
                currentMode = AppUtil.AppMode.DATA_SENDING;
                sendData(apdu);
            default:
                ISOException.throwIt(SW_INS_NOT_SUPPORTED);
        }
    }

    private void receiveLength(APDU apdu, byte[] buffer, byte state){
        //Return directly when already personalized with a value of 0
        if(personalized && state == AppUtil.AppMode.PERSONALIZE.mode) {
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

    private void sendCertificateAndCounter(APDU apdu) {
        //Get information out of incoming
        int IDLength = ByteBuffer.wrap(Arrays.copyOfRange(incoming, 0, 8)).getInt();
        byte[] terminalIDBytes = Arrays.copyOfRange(incoming, 8, 8 + IDLength);
        terminalID = terminalIDBytes;
        int certLength =  ByteBuffer.wrap(Arrays.copyOfRange(incoming, 8 + IDLength, 8 + IDLength + 8)).getInt();
        byte[] certificateBytesTerminal = Arrays.copyOfRange(incoming, 8 + IDLength + 8, 8 + IDLength + 8 + certLength);

        //Make a certificate object from the byte array and extract data
        Certificate certificateTerminalCheck = new Certificate(certificateBytesTerminal);
        String expiryDate = certificateTerminalCheck.getExpiryDate();
        String issuerName = certificateTerminalCheck.getIssuerName();
        PublicKey publicKeyTerminal = certificateTerminalCheck.getPublickKey();

        //Combine extracted data to verify
        String info = terminalID + issuerName + expiryDate + publicKeyTerminal;
        byte[] infoBytes = info.getBytes(StandardCharsets.UTF_8);

        //Verify the signature
        if(certificateTerminalCheck.verifySignature(infoBytes, publicKeyCA)){
            System.out.println("certificate POS terminal is valid");
        }
        else{
            System.out.println("certificate POS terminal is NOT valid");
            //TODO Respond with a response APDU
        }

        //return counter + certificate with multiple APDUs
        apdu.setOutgoing();
        sending = new byte[8 + certificate.length];
        lengthSending = 8 + certificate.length;
        byte[] counterArray = ByteBuffer.allocate(8).putInt(counter).array();
        System.arraycopy(counterArray, 0, sending, 0, 8);
        System.arraycopy(certificate, 0, sending, 8, certificate.length);

        apdu.setOutgoingLength((short) 250);
        apdu.sendBytesLong(sending, (short) sendingIndex, (short) 250);
        sendingIndex += 250;
        ISOException.throwIt((short) 0x6100);
    }

    private void sendData(APDU apdu){
        apdu.setOutgoing();
        if((lengthSending - sendingIndex) > 250){
            apdu.setOutgoingLength((short) 250);
            apdu.sendBytesLong(sending, (short) sendingIndex, (short) 250);
            sendingIndex += 250;
            ISOException.throwIt((short) 0x6100);
        }
        apdu.setOutgoingLength((short) (lengthSending - sendingIndex));
        apdu.sendBytesLong(sending, (short) sendingIndex, (short) (lengthSending - sendingIndex));
        ISOException.throwIt((short) 0x9000);
    }

    private void IncreaseBalance(APDU apdu, byte[] buffer){
        //Get information out of incoming buffer
        counter = ByteBuffer.wrap(Arrays.copyOfRange(incoming, 0, 8)).getInt();
        int IDLength = ByteBuffer.wrap(Arrays.copyOfRange(incoming, 8, 8 + 8)).getInt();
        byte[] terminalIDBytes = Arrays.copyOfRange(incoming, 8 + 8, 8 + 8 + IDLength);
        terminalID = terminalIDBytes;
        int amount = ByteBuffer.wrap(Arrays.copyOfRange(incoming, 8 + 8 + IDLength, 8 + 8 + IDLength + 8)).getInt();
        int signatureLength =  ByteBuffer.wrap(Arrays.copyOfRange(incoming, 8 + 8 + IDLength + 8, 8 + 8 + IDLength + 8 + 8)).getInt();
        byte[] signature = Arrays.copyOfRange(incoming, 8 + 8 + IDLength + 8 + 8, 8 + 8 + IDLength + 8 + 8 + signatureLength);

        //TODO: Verify the signature

        JCSystem.beginTransaction(); // Make Persistent Transaction
        balance += amount;
        JCSystem.commitTransaction();

        apdu.setOutgoing();

        counter += 1;

        byte[] counter = ByteBuffer.allocate(8).putInt(this.counter).array();
        byte[] cardIDBytes = cardID;
        byte[] cardIDLength = ByteBuffer.allocate(8).putInt(cardIDBytes.length).array();
        byte[] padding = {0};

        byte[] send = new byte[counter.length + 8 + cardIDBytes.length + 1];
        System.arraycopy(counter, 0, send, 0, 8);
        System.arraycopy(cardIDLength, 0, send, 8, 8);
        System.arraycopy(cardIDBytes, 0, send, 8 + 8, cardIDBytes.length);
        System.arraycopy(padding, 0, send, 8 + 8 + cardIDBytes.length, 1);
        apdu.setOutgoingLength((short) send.length);
        System.arraycopy(send, 0, buffer, 0, send.length);
        apdu.sendBytes((short) 0, (short) send.length);

        //store transaction
        int timestamp = (int)(System.currentTimeMillis() / 1000);
        byte[] timestampByte = new byte[]{
                (byte) (timestamp >> 24),
                (byte) (timestamp >> 16),
                (byte) (timestamp >> 8),
                (byte) timestamp
        };
        transactions[lastTransactionIndex][0] = timestampByte[0];
        transactions[lastTransactionIndex][1] = timestampByte[1];
        transactions[lastTransactionIndex][2] = timestampByte[2];
        transactions[lastTransactionIndex][3] = timestampByte[3];
        transactions[lastTransactionIndex][4] = cardID[0];
        transactions[lastTransactionIndex][5] = terminalID[0];
        transactions[lastTransactionIndex][6] = (byte)amount;
        transactions[lastTransactionIndex][7] = (byte)lastTransactionIndex;
        lastTransactionIndex+=1;

    }

    private void checkAmountAndDecreaseBalance(APDU apdu, byte[] buffer){
        //Get information out of incoming buffer
        counter = ByteBuffer.wrap(Arrays.copyOfRange(incoming, 0, 8)).getInt();
        int IDLength = ByteBuffer.wrap(Arrays.copyOfRange(incoming, 8, 8 + 8)).getInt();
        byte[] terminalIDBytes = Arrays.copyOfRange(incoming, 8 + 8, 8 + 8 + IDLength);
        terminalID = terminalIDBytes;
        int amount =  ByteBuffer.wrap(Arrays.copyOfRange(incoming, 8 + 8 + IDLength, 8 + 8 + IDLength + 8)).getInt();
        int signatureLength =  ByteBuffer.wrap(Arrays.copyOfRange(incoming, 8 + 8 + IDLength + 8, 8 + 8 + IDLength + 8 + 8)).getInt();
        byte[] signature = Arrays.copyOfRange(incoming, 8 + 8 + IDLength + 8 + 8, 8 + 8 + IDLength + 8 + 8 + signatureLength);

        //TODO: Verify the signature

        byte[] success = {(byte)0};

        JCSystem.beginTransaction();
        if(amount <= balance){
            success[0] = (byte)1;
            balance -= amount;
        }
        JCSystem.commitTransaction();

        apdu.setOutgoing();

        counter += 1;

        byte[] counter = ByteBuffer.allocate(8).putInt(this.counter).array();
        byte[] cardIDBytes = cardID;
        byte[] cardIDLength = ByteBuffer.allocate(8).putInt(cardIDBytes.length).array();

        byte[] send = new byte[counter.length + 8 + cardIDBytes.length + 1];
        System.arraycopy(counter, 0, send, 0, 8);
        System.arraycopy(cardIDLength, 0, send, 8, 8);
        System.arraycopy(cardIDBytes, 0, send, 8 + 8, cardIDBytes.length);
        System.arraycopy(success, 0, send, 8 + 8 + cardIDBytes.length, 1);
        apdu.setOutgoingLength((short) send.length);
        System.arraycopy(send, 0, buffer, 0, send.length);
        apdu.sendBytes((short) 0, (short) send.length);

        //store transaction
        int timestamp = (int)(System.currentTimeMillis() / 1000);
        byte[] timestampByte = new byte[]{
                (byte) (timestamp >> 24),
                (byte) (timestamp >> 16),
                (byte) (timestamp >> 8),
                (byte) timestamp
        };
        transactions[lastTransactionIndex][0] = timestampByte[0];
        transactions[lastTransactionIndex][1] = timestampByte[1];
        transactions[lastTransactionIndex][2] = timestampByte[2];
        transactions[lastTransactionIndex][3] = timestampByte[3];
        transactions[lastTransactionIndex][4] = (byte)0;
        transactions[lastTransactionIndex][5] = cardID[0]; //TODO: Add the correct cardID
        transactions[lastTransactionIndex][6] = terminalID[0]; //TODO: Add the correct terminalID
        transactions[lastTransactionIndex][7] = (byte)amount;
        lastTransactionIndex+=1;
    }

    private void view_balance(APDU apdu, byte[] buffer){
        short le = apdu.setOutgoing();
        byte[] send_balance = {(byte) balance};
        apdu.setOutgoingLength(le);
        System.arraycopy(send_balance, 0, buffer, 0, send_balance.length);
        apdu.sendBytes((short) 0, le);
    }

}
