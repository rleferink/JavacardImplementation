import javacard.framework.*;
import jdk.nashorn.internal.objects.annotations.Constructor;

import javax.smartcardio.ResponseAPDU;

public class LoyaltyApplet extends Applet implements ISO7816 {
    private static final byte X = 0;
    private static final byte Y = 1;

    private short[] xy;
    private short m;
    private byte[] lastOp;
    private boolean[] lastKeyWasDigit;

    public LoyaltyApplet() {
        xy = JCSystem.makeTransientShortArray((short) 2, JCSystem.CLEAR_ON_RESET);
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
        short le = -1;

        /* Ignore the APDU that selects this applet... */
        if (selectingApplet()) {
            return;
        }

        switch (ins) {
            case '0':
                digit((byte) 0);
                break;
            case '1':
                digit((byte) 1);
                break;
            case '2':
                digit((byte) 2);
                break;
            case '3':
                digit((byte) 3);
                break;
            case '4':
                digit((byte) 4);
                break;
            case '5':
                digit((byte) 5);
                break;
            case '6':
                digit((byte) 6);
                break;
            case '7':
                digit((byte) 7);
                break;
            case '8':
                digit((byte) 8);
                break;
            case '9':
                digit((byte) 9);
                break;
            case 'S': //apdu.setOutgoingAndSend((short) s, (short) 1);
                break;
            case '<':
            case 'X':
            case 'V':
            case '*':
            case '#':
                operator(ins);
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
        Util.setShort(buffer, (short) 3, xy[X]);
        apdu.setOutgoingLength((short) 5);
        apdu.sendBytes((short) 0, (short) 5);
    }

    void digit(byte d) {
        xy[X] = (short) ((short) (xy[X] * 10) + (short) (d & 0x00FF));
        System.out.println(d + " digit pressed: " + xy[X]);
    }

    void operator(byte op) throws ISOException {
        System.out.println(op + " operator pressed");
    }
}
