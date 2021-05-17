import javacard.framework.*;

import javax.smartcardio.CardException;
import javax.sound.midi.SysexMessage;

import java.math.BigInteger;
import java.util.Arrays;

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
                break;

            // Andrius: These are not instructions. They can be considered as INS parameters, for example P1 or P2
//            //cases for protocol spending points
//            case ACK_ONLINE: ack_online(apdu); break;
//            case DECREASE_BALANCE: decrease_balance(apdu); break;

            case SPEND:
                currentMode= AppUtil.AppMode.SPEND;

                if (P1 == AppUtil.AppComState.SEND_CERTIFICATE.mode){
                    System.out.println("C receives: " + buffer[OFFSET_CDATA]);
                    sendCertificateAndNonce(apdu);
                    System.out.println("");
                }

                break;

            case VIEW:
                //instruction: VIEW
                currentMode= AppUtil.AppMode.VIEW;
                break;
            default:
                ISOException.throwIt(SW_INS_NOT_SUPPORTED);
        }
    }

    private void sendCertificateAndNonce(APDU apdu){
        byte[] buffer = apdu.getBuffer();

        short le = -1;
        le = apdu.setOutgoing();
        if (le < 30) {
            ISOException.throwIt((short) (SW_WRONG_LENGTH | 30));
        }

        //System.out.println("STEP 1 - send certificate and nonce");
        //TODO: send certificate of card back with counter+1
        short buffer_size = (short) buffer[4];
        short counter = (short) buffer[5];
        String certificate_terminal = new String(Arrays.copyOfRange(buffer,6,buffer_size+5));
        System.out.println(certificate_terminal);
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
