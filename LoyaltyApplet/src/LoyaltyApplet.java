import javacard.framework.*;

import javax.sound.midi.SysexMessage;

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
        }

        switch (P1) {
            case '0':
                digit((byte) 0, apdu);
                break;
            case '1':
                digit((byte) 1, apdu);
                break;
            case '2':
                digit((byte) 2, apdu);
                break;
            case '3':
                digit((byte) 3, apdu);
                break;
            case '4':
                digit((byte) 4, apdu);
                break;
            case '5':
                digit((byte) 5, apdu);
                break;
            case '6':
                digit((byte) 6, apdu);
                break;
            case '7':
                digit((byte) 7, apdu);
                break;
            case '8':
                digit((byte) 8, apdu);
                break;
            case '9':
                digit((byte) 9, apdu);
                break;
            case '<':
            case 'X':
            case 'O':
            case '*':
            case '#':
                operator(P1);
                break;
            default:
                ISOException.throwIt(SW_INS_NOT_SUPPORTED);
        }

        le = apdu.setOutgoing();
        if (le < 5) {
            ISOException.throwIt((short) (SW_WRONG_LENGTH | 5));
        }
        buffer[0] = (byte) currentMode.ordinal();
        Util.setShort(buffer, (short) 1, (short) 0);
        Util.setShort(buffer, (short) 3, enteredValue[X]);
        apdu.setOutgoingLength((short) 5);
        apdu.sendBytes((short) 0, (short) 5);
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

    void digit(byte d, APDU apdu) {
        enteredValue[X] = (short) ((short) (enteredValue[X] * 10) + (short) (d & 0x00FF));
    }

    void operator(byte op) throws ISOException {
        switch (op){
            case 'O':
                //execute operation
                executeOperation();
                break;
            case 'X':
                //remove inserted value
                enteredValue[X] = 0;
                break;
            case '<':
                //remove last digit
                enteredValue[X] = (short) (enteredValue[X] / 10);
                break;
            default:
                break;
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
        short nonce = (short) 12;
        short le = apdu.setOutgoing();
        apdu.setOutgoingLength((short)1);
        buffer[0]=(byte) nonce;
        apdu.sendBytes((short) 0, (short)1);
        System.out.println("Bytes sent back to Terminal\n");
    }

    private void ack_online(APDU apdu){
        System.out.println("ack online");
    }

    private void decrease_balance(APDU apdu){
        System.out.println("decrease balance");
    }

    // TODO: complete implementation based on "currentMode"
    void executeOperation(){
        switch (currentMode){
            case ADD:
                // TODO: add check if balance can be increased
                balance += enteredValue[X];
                enteredValue[X] = 0;
                break;
            case SPEND:
                //receive nonce/counter
                int counter = 0;



                if(enteredValue[X] <= balance){
                    balance -= enteredValue[X];
                    enteredValue[X] = 0;
                }

                break;
            case VIEW:
                enteredValue[X] = balance;
                break;
        }
    }
}
