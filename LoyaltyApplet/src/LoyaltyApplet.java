import javacard.framework.*;

public class LoyaltyApplet extends Applet implements ISO7816 {


    private static final byte X = 0;
    private AppUtil.AppMode currentMode;

    private short[] enteredValue;
    private short m;
    private byte[] lastOp;
    private boolean[] lastKeyWasDigit;
    private short balance = 100; // Initial card balance

    public LoyaltyApplet() {
        enteredValue = JCSystem.makeTransientShortArray((short) 1, JCSystem.CLEAR_ON_RESET);
        lastOp = JCSystem.makeTransientByteArray((short) 1, JCSystem.CLEAR_ON_RESET);
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
        short le = -1;

        /* Ignore the APDU that selects this applet... */
        if (selectingApplet()) {
            return;
        }

        switch (ins) {
            case 'A':
                currentMode= AppUtil.AppMode.ADD;
                break;
            case 'S':
                currentMode= AppUtil.AppMode.SPEND;
                break;
            case 'V':
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
        buffer[0] = (m == 0) ? (byte) 0x00 : (byte) 0x01;
        Util.setShort(buffer, (short) 1, (short) 0);
        Util.setShort(buffer, (short) 3, enteredValue[X]);
        apdu.setOutgoingLength((short) 5);
        apdu.sendBytes((short) 0, (short) 5);
    }

    void digit(byte d, APDU apdu) {
        enteredValue[X] = (short) ((short) (enteredValue[X] * 10) + (short) (d & 0x00FF));
        System.out.println(d + " digit pressed: " + enteredValue[X]);


    }

    void operator(byte op) throws ISOException {
        switch (op){
            case 'O':
                executeOperation();
                break;
            case 'X':
                enteredValue[X] = 0;
                break;
            case '<':
                enteredValue[X] = (short) (enteredValue[X] / 10);
                break;
            default:
                break;
        }
    }

    // TODO: implement based on "currentMode"
    void executeOperation(){
        switch (currentMode){
            case ADD:
                break;
            case SPEND:
                break;
            case VIEW:
                enteredValue[X] = balance;
                break;
        }
    }
}
