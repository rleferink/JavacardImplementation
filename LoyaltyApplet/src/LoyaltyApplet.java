import javacard.framework.*;

import javax.smartcardio.CardException;
import javax.sound.midi.SysexMessage;

import java.math.BigInteger;

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
    final static byte SEND_CERTIFICATE_AND_NONCE = (byte) 0x20;
    final static byte ACK_ONLINE = (byte) 0x21;
    final static byte DECREASE_BALANCE = (byte) 0x22;

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
        byte P2 = buffer[OFFSET_P2];
        short le = -1;
        byte[] tmp = JCSystem.makeTransientByteArray((short) (buffer[ISO7816.OFFSET_LC] & 0x00FF), JCSystem.CLEAR_ON_DESELECT);

        /* Ignore the APDU that selects this applet... */
        if (selectingApplet()) {
            return;
        }

        short bytesLeft = (short) (buffer[OFFSET_LC] & 0x00FF);

        switch (ins) {
            case 'A':
                //instruction: ADD
                currentMode= AppUtil.AppMode.ADD;
                break;

            //cases for protocol spending points
            case SEND_CERTIFICATE_AND_NONCE:
                readBuffer(apdu, tmp, (short) 0, bytesLeft);
                send_certificate_and_nonce(tmp, apdu);
                break;
            case ACK_ONLINE: ack_online(apdu); break;
            case DECREASE_BALANCE: decrease_balance(apdu); break;


            case 'S':
                //instruction: SPEND
                currentMode= AppUtil.AppMode.SPEND;
                break;

            case 'V':
                //instruction: VIEW
                currentMode= AppUtil.AppMode.VIEW;
                break;
            default:
                ISOException.throwIt(SW_INS_NOT_SUPPORTED);
        }
    }

    private void readBuffer(APDU apdu, byte[] dest, short offset, short length) {
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

    private void send_certificate_and_nonce(byte[] buffer, APDU apdu){
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
