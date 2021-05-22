import javacard.framework.AID;
import javacard.framework.ISO7816;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.*;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Collections;
import java.nio.ByteBuffer;
import java.util.List;

import javax.smartcardio.Card;
import javax.smartcardio.CardChannel;
import javax.smartcardio.CardException;
import javax.smartcardio.CardTerminal;
import javax.smartcardio.CardTerminals;
import javax.smartcardio.CommandAPDU;
import javax.smartcardio.ResponseAPDU;
import javax.smartcardio.TerminalFactory;
import javax.sound.midi.SysexMessage;
import javax.swing.*;

import com.licel.jcardsim.smartcardio.CardTerminalSimulator;
import com.licel.jcardsim.smartcardio.CardSimulator;
import javacard.framework.ISOException;

import java.math.BigInteger;
/**
 * POS terminal for the Loyalty Applet.
 **
 * @author Andrius Kuprys
 * @author Roland Leferink
 * @author Marc van de Werfhorst
 * @author Romy Stähli
 *
 */
public class PosTerminal extends JPanel implements ActionListener {

    //private JavaxSmartCardInterface simulatorInterface; // SIM
    private AppUtil.AppMode appMode = AppUtil.AppMode.ADD;

    private static final long serialVersionUID = 1L;
    static final String TITLE = "Loyalty Applet";
    static final Font FONT = new Font("Monospaced", Font.BOLD, 24);
    static final Dimension PREFERRED_SIZE = new Dimension(400, 300);

    static final int DISPLAY_WIDTH = 20;
    static final String MSG_ERROR = "    -- error --     ";
    static final String MSG_DISABLED = " -- insert card --  ";
    static final String MSG_INVALID = " -- invalid card -- ";

    static final byte[] CALC_APPLET_AID = { (byte) 0x3B, (byte) 0x29,
            (byte) 0x63, (byte) 0x61, (byte) 0x6C, (byte) 0x63, (byte) 0x01 };
    static final String CALC_APPLET_AID_string = "3B2963616C6301";

    static final CommandAPDU SELECT_APDU = new CommandAPDU(
            (byte) 0x00, (byte) 0xA4, (byte) 0x04, (byte) 0x00, CALC_APPLET_AID);

    String POSTerminal = "certificate POSTerminal";

    int lastTransactionIndex = 0;
    String [] transactions = new String[4];
    //create an array consisting of timestamp, card number, terminalID, amount of points.

    private short enteredValue=0;


    JTextField display;
    JPanel keypad;

    CardChannel applet;

    public PosTerminal(JFrame parent) {
        //simulatorInterface = new JavaxSmartCardInterface(); // SIM
        buildGUI(parent);
        setEnabled(false);
        (new SimulatedCardThread()).start();
    }

    void buildGUI(JFrame parent) {
        setLayout(new BorderLayout());
        display = new JTextField(DISPLAY_WIDTH);
        display.setHorizontalAlignment(JTextField.RIGHT);
        display.setEditable(false);
        display.setFont(FONT);
        display.setBackground(Color.darkGray);
        display.setForeground(Color.green);
        add(display, BorderLayout.NORTH);
        keypad = new JPanel(new GridLayout(4, 5));
        key("1", Color.black);
        key("2", Color.black);
        key("3", Color.black);
        key("X", Color.red);
        checkBox("Add", true);
        key("4", Color.black);
        key("5", Color.black);
        key("6", Color.black);
        key("<", Color.orange);
        checkBox("Spend", false);
        key("7", Color.black);
        key("8", Color.black);
        key("9", Color.black);
        key("OK", Color.green);
        checkBox("View", false);
        key(null, null);
        key("0", Color.black);
        key(null, null);
        key(null, null);
        key(null, null);
        add(keypad, BorderLayout.CENTER);
        parent.addWindowListener(new CloseEventListener());
    }

    void key(String txt, Color c) {
        if (txt == null) {
            keypad.add(new JLabel());
        } else {
            JButton button = new JButton(txt);
            button.setOpaque(true);
            button.setForeground(c);
            button.addActionListener(this);
            keypad.add(button);
        }
    }

    ButtonGroup rbModeGroup = new ButtonGroup();

    void checkBox(String txt, boolean enable) {
        JRadioButton rb = new JRadioButton(txt, enable);
        rbModeGroup.add(rb);
        keypad.add(rb);
        rb.addActionListener(e -> {
            enteredValue = 0;
            switch (e.getActionCommand()){
                case "Add":
                    appMode = AppUtil.AppMode.ADD;
                    break;
                case "Spend":
                    appMode = AppUtil.AppMode.SPEND;
                    break;
                case "View":
                    appMode = AppUtil.AppMode.VIEW;
                    break;
            }
            setText(enteredValue);
        });
    }

    String getText() {
        return display.getText();
    }

    void setText(String txt) {
        try {
            if (appMode == AppUtil.AppMode.ADD) {
                txt = String.format("ADD  : %20s", txt);
            } else if (appMode == AppUtil.AppMode.SPEND) {
                txt = String.format("SPEND: %20s", txt);
            } else {
                txt = String.format("VIEW : %20s", txt);
            }
        } catch (NumberFormatException nfe) {
            txt = String.format("MSG  : %20s", txt);
        }
        display.setText(txt);
    }

    void setText(int n) {
        setText(Integer.toString(n));
    }

    public void setEnabled(boolean b) {
        super.setEnabled(b);
        if (b) {
            setText(0);
        } else {
            setText(MSG_DISABLED);
        }
        Component[] keys = keypad.getComponents();
        for (int i = 0; i < keys.length; i++) {
            keys[i].setEnabled(b);
        }
    }

    /* Connect the terminal with a simulated smartcard JCardSim
     */
    class SimulatedCardThread extends Thread {
        public void run() {
            // Obtain a CardTerminal
            CardTerminals cardTerminals = CardTerminalSimulator.terminals("My terminal 1");
            CardTerminal terminal1 = cardTerminals.getTerminal("My terminal 1");

            // Create simulator and install applet
            CardSimulator simulator = new CardSimulator();
            AID calcAppletAID = new AID(CALC_APPLET_AID,(byte)0,(byte)7);
            // @Andrius: This inserts a card
            simulator.installApplet(calcAppletAID, LoyaltyApplet.class);

            // Insert Card into "My terminal 1"
            simulator.assignToTerminal(terminal1);

            try {
                Card card = terminal1.connect("*");

                applet = card.getBasicChannel();
                ResponseAPDU resp = applet.transmit(SELECT_APDU);
                if (resp.getSW() != 0x9000) {
                    throw new Exception("Select failed");
                }
                setEnabled(true);
            } catch (Exception e) {
                System.err.println("Card status problem!");
            }
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        try {
            Object src = e.getSource();
            if (src instanceof JButton) {
                String c = ((JButton) src).getText();
                if (c=="OK"){
                    switch (appMode){
                        case ADD:
                            break;
                        case SPEND:
                            System.out.println("Terminal: spending points");
                            byte[] counter = getBytes(BigInteger.valueOf(3));
                            byte[] received = {};

                            //step 3: amount to spend is entered
                            short amount = enteredValue;
                            System.out.println("Amount entered: " + amount);

                            //step 4: send certificate
                            received = sendAndCheckCertificate(counter, received);

                            //step 12: any revoked transactions?
                            //TODO: !!!!!!!

                            //step 13: is balance >= n? | counter +=1
                            received = checkBalanceAndDecrease(counter, received,amount);

                            //step 16: store transaction?
                            //TODO if we still think this is useful

                            setText("Remove card");
                            System.out.println("End spending protocol");
                            break;
                        case VIEW:
                            byte[] empty_data = {(byte) 0};
                            CommandAPDU apdu_view = new CommandAPDU(0x00, AppUtil.AppMode.VIEW.mode, 0, 0, empty_data, 1);
                            ResponseAPDU res_view = null;
                            try {
                                res_view = sendCommandAPDU(apdu_view);
                            } catch (CardException ex) {
                                System.out.println((MSG_ERROR));
                                ex.printStackTrace();
                            }
                            received = res_view.getData();
                            setText((short) received[0]);
                            break;
                    }
                }
                else if (c=="X"){  // Reset environment
                    for (Enumeration<AbstractButton> buttons = rbModeGroup.getElements(); buttons.hasMoreElements();) {
                        enteredValue=0;
                        AbstractButton button = buttons.nextElement();
                        if (button.getText()=="Add"){
                            button.setSelected(true);
                        }
                        else {
                            button.setSelected(false);
                        }
                    }
                    setText(enteredValue);
                }
                else if (c=="<"){ // Remove one digit
                    enteredValue = (short) (enteredValue/10);
                    setText(enteredValue);
                }
                else { // Print digits
                    enteredValue = (short)(enteredValue * 10 + Integer.parseInt(c));
                    setText(enteredValue);
                }

            }
        } catch (Exception ex) {
            System.out.println(MSG_ERROR);
            System.out.println(ex);
        }
    }

    byte[] checkBalanceAndDecrease(byte[] counter, byte[] received, short amount){
        counter[0] = (byte) (received[0] + 1);
        System.out.println("T -> C: balance >= amount? | " + (counter[0]));
        byte[] amountArray = {(byte) amount};
        byte[] balance_check = new byte[counter.length + amountArray.length];
        System.arraycopy(counter, 0, balance_check, 0, counter.length);
        System.arraycopy(amountArray, 0, balance_check, counter.length, amountArray.length);
        CommandAPDU apdu_amount_check = new CommandAPDU(0x00, AppUtil.AppMode.SPEND.mode, AppUtil.AppComState.SEND_AMOUNT_CHECK.mode, 0, balance_check, 2);

        ResponseAPDU res_amount_check = null;
        try {
            res_amount_check = sendCommandAPDU(apdu_amount_check);
        } catch (CardException e) {
            System.out.println((MSG_ERROR));
            e.printStackTrace();
        }

        received = res_amount_check.getData();
        if(received[0] == counter[0] + 1){
            System.out.println("Counter CORRECT");
        }
        else{
            System.out.println("Counter INCORRECT");
            //TODO: stop protocol if incorrect
        }

        if(received[1] == (byte)1){
            System.out.println("Balance high enough");
        }
        else{
            System.out.println("Balance NOT high enough");
            //TODO: stop protocol if balance not high enough
        }
        return received;
    }

    byte[] sendAndCheckCertificate(byte[] counter, byte[] received){
        byte[] certificate = POSTerminal.getBytes();
        byte[] send = new byte[counter.length + certificate.length];
        System.arraycopy(counter, 0, send, 0, counter.length);
        System.arraycopy(certificate, 0, send, counter.length, certificate.length);
        CommandAPDU apdu_certificate = new CommandAPDU(0x00, AppUtil.AppMode.SPEND.mode, AppUtil.AppComState.SEND_CERTIFICATE.mode, 0, send, 30);

        //step 6: receive certificate and counter = 1
        ResponseAPDU res_certificate = null;
        try {
            res_certificate = sendCommandAPDU(apdu_certificate);
        } catch (CardException e) {
            System.out.println((MSG_ERROR));
            e.printStackTrace();
        }
        received = res_certificate.getData();
        short buffer_size = (short) received.length;
        String certificate_card = new String(Arrays.copyOfRange(received,1,buffer_size-13));
        if(received[0] == counter[0] + 1){
            System.out.println("Counter CORRECT");
        }
        else{
            System.out.println("Counter INCORRECT");
            //TODO: stop protocol if incorrect
        }

        if(certificate_card.equals("certificate card")){
            System.out.println("Certificate card CORRECT");
        }
        else{
            System.out.println("certificate INCORRECT");
            //TODO: stop protocol if incorrect
        }
        return received;
    }

    ResponseAPDU sendCommandAPDU(CommandAPDU capdu) throws CardException {
        ResponseAPDU rapdu = applet.transmit(capdu);

        byte[] data = rapdu.getData();
        int sw = rapdu.getSW();
        if (sw != 0x9000) {
            setText(MSG_ERROR);
        } else if (data.length > 4) {
            setText((short) (((data[1] & 0x000000FF) << 32) | ((data[2] & 0x000000FF) << 16) |
                    ((data[3] & 0x000000FF) << 8) | (data[4] & 0x000000FF)));
        }
        return rapdu;
    }

    void log(ResponseAPDU obj){
        //display.append(obj.toString() + ", Data="+ toHexString(obj.getData()) + "\n");
        System.out.println(obj.toString() + ", Data=" + toHexString(obj.getData()));
    }

    void log(CommandAPDU obj){
        //display.append(obj.toString() + ", Data="+ toHexString(obj.getData()) + "\n");
        System.out.println(obj.toString() + ", Data=" + toHexString(obj.getData()));
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

    String toHexString(byte[] in) {
        //System.out.println("len: " + in.length);
        StringBuilder out = new StringBuilder(2*in.length);
        for(int i = 0; i < in.length; i++) {
            out.append(String.format("%02x ", (in[i] & 0xFF)));
        }
        return out.toString().toUpperCase();
    }

    class CloseEventListener extends WindowAdapter {
        public void windowClosing(WindowEvent we) {
            System.exit(0);
        }
    }

    public ResponseAPDU sendKey(byte P1) {
        CommandAPDU apdu = new CommandAPDU(0, (byte)appMode.toString().toCharArray()[0], P1, 0, 5);
        try {
            return applet.transmit(apdu);
        } catch (CardException e) {
            return null;
        }
    }

    public Dimension getPreferredSize() {
        return PREFERRED_SIZE;
    }

    void spendingPoints() {
        //step 3: enter amount of points to spend, now set to 100
        //int points = 100;

        //step 8: t -> c: nonce_1 (ins = 0x20 = send certificate and nonce)
        CommandAPDU apdu = new CommandAPDU((byte)0xb0, (byte) 0x20, 0,0,0);
        try {
            ResponseAPDU resp = applet.transmit(apdu);
            byte[] data = resp.getData();
            if(data.length == 0){
                System.out.println("Received buffer is empty");
            } else{
                System.out.println("Received buffer: " + data + "\nlength: " + data.length);
            }
        } catch (CardException e) {
            return;
        }

        //step 9: t -> c: nonce_1 | online/offline | nonce_2

        //step 11: c -> t: nonce_2

        //step 12: t -> d: any revoked transaction?

        //step 13: d -> t: yes/no

        //step 15: t -> c: balance at least n?

        //step 16: c -> t: yes/no | nonce_3

        //step 17: if yes: t -> c: decrease with n points

        //step 19: c -> t: balance decreased

        //step 20: t -> u: card can be removed

        //step 24: t -> d: decrease with n points | timestamp

        //step 26: d -> t: balance is decreased
    }

    public static void main(String[] args) {
        JFrame frame = new JFrame(TITLE);
        Container c = frame.getContentPane();
        PosTerminal panel = new PosTerminal(frame);
        c.add(panel);
        frame.setResizable(false);
        frame.pack();
        frame.setVisible(true);
    }
}

