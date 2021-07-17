import javacard.framework.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.EncodedKeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import static javacard.framework.JCSystem.*;

public class LoyaltyApplet extends Applet implements ISO7816 {
    private AppUtil.AppMode currentMode;

    private short balance = 0; // Initial card balance

    boolean personalized = false;

    byte[] cardID = null;
    byte[] terminalID = makeTransientByteArray((short) 4, CLEAR_ON_DESELECT);
    byte[] certificate = null;
    PublicKey publicKey = null;
    PrivateKey privateKey = null;
    PublicKey publicKeyCA = null;

    byte[] transaction = makeTransientByteArray((short)2500, CLEAR_ON_DESELECT);
    byte[] scratchpad = makeTransientByteArray((short) 30, CLEAR_ON_DESELECT);

    int incomingIndex = 0;
    int lengthIncoming = 0;

    int sendingIndex = 0;
    int lengthSending = 0;

    int counter = 1;

    int sequenceNumber = 0; // Total amount of transactions
    int lastTransactionIndex = 0; // Card keeps track of the most recent 100 transactions
    Byte [][] transactions = new Byte[100][4];

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
            scratchpad[0] = 0;
            System.arraycopy(scratchpad, 0, buffer, 0, 1);
            apdu.sendBytes((short) 0, le);
            return;
        }

        //Retrieve the length of the incoming message and creating a new byte[] for that message
        lengthIncoming = ByteBuffer.wrap(Arrays.copyOfRange(buffer, 5, 5+ 8)).getInt();
        incomingIndex = 0;

        //Return with 1
        short le = apdu.setOutgoing();
        scratchpad[0] = 1;
        apdu.setOutgoingLength(le);
        System.arraycopy(scratchpad, 0, buffer, 0, 1);
        apdu.sendBytes((short) 0, le);
    }

    private void receiveInfo(APDU apdu, byte[] buffer){
        //Copy the incoming buffer into the previously made byte[] at the right position of incomingIndex
        if(lengthIncoming - incomingIndex >= 250){
            System.arraycopy(buffer, 5, transaction, incomingIndex, 250);
        } else {
            System.arraycopy(buffer, 5, transaction, incomingIndex, lengthIncoming - incomingIndex);
        }
        incomingIndex += 250;

        //Return with 1
        short le = apdu.setOutgoing();
        scratchpad[0] = 1;
        apdu.setOutgoingLength(le);
        System.arraycopy(scratchpad, 0, buffer, 0, 1);
        apdu.sendBytes((short) 0, le);
    }

    private void acceptInfoPersonalize(APDU apdu, byte[] buffer) {
        //Get information out of incoming
        int IDLength = ByteBuffer.wrap(Arrays.copyOfRange(transaction, 0, 8)).getInt();
        cardID = Arrays.copyOfRange(transaction, 8, 8 + IDLength);
        int certLength =  ByteBuffer.wrap(Arrays.copyOfRange(transaction, 8 + IDLength, 8 + IDLength + 8)).getInt();
        certificate = Arrays.copyOfRange(transaction, 8 + IDLength + 8, 8 + IDLength + 8 + certLength);

        int pubKeyLength =  ByteBuffer.wrap(Arrays.copyOfRange(transaction, 8 + IDLength + 8 + certLength, 8 + IDLength + 8 + certLength + 8)).getInt();
        byte[] pubKeyBytes = Arrays.copyOfRange(transaction, 8 + IDLength + 8 + certLength + 8, 8 + IDLength + 8 + certLength + 8 + pubKeyLength);
        int privKeyLength =  ByteBuffer.wrap(Arrays.copyOfRange(transaction, 8 + IDLength + 8 + certLength + 8 + pubKeyLength, 8 + IDLength + 8 + certLength + 8 + pubKeyLength + 8)).getInt();
        byte[] privKeyBytes = Arrays.copyOfRange(transaction, 8 + IDLength + 8 + certLength + 8 + pubKeyLength + 8, 8 + IDLength + 8 + certLength + 8 + pubKeyLength + 8 + privKeyLength);
        int pubCALength = ByteBuffer.wrap(Arrays.copyOfRange(transaction, 8 + IDLength + 8 + certLength + 8 + pubKeyLength + 8 + privKeyLength, 8 + IDLength + 8 + certLength + 8 + pubKeyLength + 8 + privKeyLength + 8)).getInt();
        byte[] pubCABytes = Arrays.copyOfRange(transaction, 8 + IDLength + 8 + certLength + 8 + pubKeyLength + 8 + privKeyLength + 8, 8 + IDLength + 8 + certLength + 8 + pubKeyLength + 8 + privKeyLength + 8 + pubCALength);

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
        scratchpad[0] = 1;
        apdu.setOutgoingLength(le);
        System.arraycopy(scratchpad, 0, buffer, 0, 1);
        apdu.sendBytes((short) 0, le);
    }

    private void sendCertificateAndCounter(APDU apdu) {
        //Get information out of incoming
        int IDLength = ByteBuffer.wrap(Arrays.copyOfRange(transaction, 0, 8)).getInt();
        byte[] terminalIDBytes = Arrays.copyOfRange(transaction, 8, 8 + IDLength);
        terminalID = terminalIDBytes;
        int certLength =  ByteBuffer.wrap(Arrays.copyOfRange(transaction, 8 + IDLength, 8 + IDLength + 8)).getInt();
        byte[] certificateBytesTerminal = Arrays.copyOfRange(transaction, 8 + IDLength + 8, 8 + IDLength + 8 + certLength);

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
        }

        //return counter + certificate with multiple APDUs
        apdu.setOutgoing();
        lengthSending = 8 + certificate.length;
        byte[] counterArray = ByteBuffer.allocate(8).putInt(counter).array();
        System.arraycopy(counterArray, 0, transaction, 0, 8);
        System.arraycopy(certificate, 0, transaction, 8, certificate.length);

        apdu.setOutgoingLength((short) 250);
        apdu.sendBytesLong(transaction, (short) sendingIndex, (short) 250);
        sendingIndex += 250;
        ISOException.throwIt((short) 0x6100);
    }

    private void sendData(APDU apdu){
        apdu.setOutgoing();
        if((lengthSending - sendingIndex) > 250){
            apdu.setOutgoingLength((short) 250);
            apdu.sendBytesLong(transaction, (short) sendingIndex, (short) 250);
            sendingIndex += 250;
            ISOException.throwIt((short) 0x6100);
        }
        apdu.setOutgoingLength((short) (lengthSending - sendingIndex));
        apdu.sendBytesLong(transaction, (short) sendingIndex, (short) (lengthSending - sendingIndex));
        ISOException.throwIt((short) 0x9000);
    }

    private void IncreaseBalance(APDU apdu, byte[] buffer){
        //Get information out of incoming buffer
        counter = ByteBuffer.wrap(Arrays.copyOfRange(transaction, 0, 8)).getInt();
        int IDLength = ByteBuffer.wrap(Arrays.copyOfRange(transaction, 8, 8 + 8)).getInt();
        byte[] terminalIDBytes = Arrays.copyOfRange(transaction, 8 + 8, 8 + 8 + IDLength);
        terminalID = terminalIDBytes;
        int amount = ByteBuffer.wrap(Arrays.copyOfRange(transaction, 8 + 8 + IDLength, 8 + 8 + IDLength + 8)).getInt();
        int signatureLength =  ByteBuffer.wrap(Arrays.copyOfRange(transaction, 8 + 8 + IDLength + 8, 8 + 8 + IDLength + 8 + 8)).getInt();
        byte[] signature = Arrays.copyOfRange(transaction, 8 + 8 + IDLength + 8 + 8, 8 + 8 + IDLength + 8 + 8 + signatureLength);

        JCSystem.beginTransaction(); // Make Persistent Transaction
        balance += amount;

        sequenceNumber += 1;
        counter += 1;

        byte[] counter = ByteBuffer.allocate(8).putInt(this.counter).array();
        byte[] cardIDBytes = cardID;
        byte[] cardIDLength = ByteBuffer.allocate(8).putInt(cardIDBytes.length).array();
        byte[] padding = {0};

        //store transaction
        transactions[lastTransactionIndex][0] = (byte)sequenceNumber;
        transactions[lastTransactionIndex][1] = cardID[0];
        transactions[lastTransactionIndex][2] = terminalID[0];
        transactions[lastTransactionIndex][3] = (byte)amount;

        lastTransactionIndex = (lastTransactionIndex + 1) % 100;

        JCSystem.commitTransaction();

        apdu.setOutgoing();

        int lengthOfSend = counter.length + 8 + cardIDBytes.length + 1;
        System.arraycopy(counter, 0, scratchpad, 0, 8);
        System.arraycopy(cardIDLength, 0, scratchpad, 8, 8);
        System.arraycopy(cardIDBytes, 0, scratchpad, 8 + 8, cardIDBytes.length);
        System.arraycopy(padding, 0, scratchpad, 8 + 8 + cardIDBytes.length, 1);
        apdu.setOutgoingLength((short) lengthOfSend);
        System.arraycopy(scratchpad, 0, buffer, 0, lengthOfSend);
        apdu.sendBytes((short) 0, (short) lengthOfSend);
    }

    private void checkAmountAndDecreaseBalance(APDU apdu, byte[] buffer){
        //Get information out of incoming buffer
        counter = ByteBuffer.wrap(Arrays.copyOfRange(transaction, 0, 8)).getInt();
        int IDLength = ByteBuffer.wrap(Arrays.copyOfRange(transaction, 8, 8 + 8)).getInt();
        byte[] terminalIDBytes = Arrays.copyOfRange(transaction, 8 + 8, 8 + 8 + IDLength);
        terminalID = terminalIDBytes;
        int amount =  ByteBuffer.wrap(Arrays.copyOfRange(transaction, 8 + 8 + IDLength, 8 + 8 + IDLength + 8)).getInt();
        int signatureLength =  ByteBuffer.wrap(Arrays.copyOfRange(transaction, 8 + 8 + IDLength + 8, 8 + 8 + IDLength + 8 + 8)).getInt();
        byte[] signature = Arrays.copyOfRange(transaction, 8 + 8 + IDLength + 8 + 8, 8 + 8 + IDLength + 8 + 8 + signatureLength);

        byte[] success = {(byte)0};

        JCSystem.beginTransaction();
        if(amount <= balance){
            success[0] = (byte)1;
            balance -= amount;
        }

        sequenceNumber += 1;
        counter += 1;

        byte[] counter = ByteBuffer.allocate(8).putInt(this.counter).array();
        byte[] cardIDBytes = cardID;
        byte[] cardIDLength = ByteBuffer.allocate(8).putInt(cardIDBytes.length).array();

        //store transaction
        transactions[lastTransactionIndex][0] = (byte)sequenceNumber;
        transactions[lastTransactionIndex][1] = cardID[0];
        transactions[lastTransactionIndex][2] = terminalID[0];
        transactions[lastTransactionIndex][3] = (byte)amount;
        lastTransactionIndex = (lastTransactionIndex + 1) % 100;

        JCSystem.commitTransaction();

        apdu.setOutgoing();

        int lengthOfSend = counter.length + 8 + cardIDBytes.length + 1;
        System.arraycopy(counter, 0, scratchpad, 0, 8);
        System.arraycopy(cardIDLength, 0, scratchpad, 8, 8);
        System.arraycopy(cardIDBytes, 0, scratchpad, 8 + 8, cardIDBytes.length);
        System.arraycopy(success, 0, scratchpad, 8 + 8 + cardIDBytes.length, 1);
        apdu.setOutgoingLength((short) lengthOfSend);
        System.arraycopy(scratchpad, 0, buffer, 0, lengthOfSend);
        apdu.sendBytes((short) 0, (short) lengthOfSend);
    }

    private void view_balance(APDU apdu, byte[] buffer){
        short le = apdu.setOutgoing();
        byte[] send_balance = {(byte) balance};
        apdu.setOutgoingLength(le);
        System.arraycopy(send_balance, 0, buffer, 0, send_balance.length);
        apdu.sendBytes((short) 0, le);
    }
}
