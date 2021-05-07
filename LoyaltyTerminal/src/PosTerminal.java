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
import java.util.Enumeration;
import java.util.List;

import javax.smartcardio.Card;
import javax.smartcardio.CardChannel;
import javax.smartcardio.CardException;
import javax.smartcardio.CardTerminal;
import javax.smartcardio.CardTerminals;
import javax.smartcardio.CommandAPDU;
import javax.smartcardio.ResponseAPDU;
import javax.smartcardio.TerminalFactory;
import javax.swing.*;

import com.licel.jcardsim.smartcardio.CardTerminalSimulator;
import com.licel.jcardsim.smartcardio.CardSimulator;

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
            switch (e.getActionCommand()){
                case "Add":
                    appMode = AppUtil.AppMode.ADD;
                    setText(String.format("ADD  : %20s", 0));
                    break;
                case "Spend":
                    appMode = AppUtil.AppMode.SPEND;
                    setText(String.format("SPEND: %20s", 0));
                    break;
                case "View":
                    appMode = AppUtil.AppMode.VIEW;
                    // Reset the input screen
                    setText(String.format("VIEW : %20s", 0));
                    break;
            }
            //sendKey((byte) 'X'); // Reset the input screen
        });

    }

    String getText() {
        return display.getText();
    }

    void setText(String txt) {
        display.setText(txt);
    }

    void setText(int n) {
        setText(Integer.toString(n));
    }

    void setText(ResponseAPDU apdu) {
        byte[] data = apdu.getData();
        int sw = apdu.getSW();
        if (sw != 0x9000 || data.length < 5) {
            setText(MSG_ERROR);
        } else {
            if (data[0] == AppUtil.AppMode.ADD.ordinal()){
                setText(String.format("ADD  : %20s", (short) (((data[3] & 0x000000FF) << 8) | (data[4] & 0x000000FF))));
            } else if (data[0] == AppUtil.AppMode.SPEND.ordinal()){
                setText(String.format("SPEND: %20s", (short) (((data[3] & 0x000000FF) << 8) | (data[4] & 0x000000FF))));
            } else {
                setText(String.format("VIEW : %20s", (short) (((data[3] & 0x000000FF) << 8) | (data[4] & 0x000000FF))));
            }
        }
    }

    public void setEnabled(boolean b) {
        super.setEnabled(b);
        if (b) {
            setText(String.format("ADD  : %20s", 0));
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
                char c = ((JButton) src).getText().charAt(0);
                setText(sendKey((byte) c));
                if (c=='O'){
                    switch (appMode){
                        case ADD:
                            break;
                        case SPEND:
                            spendingPoints();
                            break;
                        case VIEW:
                            break;
                    }
                }
                else if (c=='X'){  // Reset environment
                    for (Enumeration<AbstractButton> buttons = rbModeGroup.getElements(); buttons.hasMoreElements();) {
                        AbstractButton button = buttons.nextElement();
                        if (button.getText()=="Add"){
                            button.setSelected(true);
                        }
                        else {
                            button.setSelected(false);
                        }
                    }
                }
            }
        } catch (Exception ex) {
            System.out.println(MSG_ERROR);
        }
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

        //step 8: c -> t: nonce_1
        CommandAPDU apdu = new CommandAPDU(0, (byte) 0x20, 0,0,0);
        try {
            ResponseAPDU resp = applet.transmit(apdu);
            byte[] data = resp.getData();
            System.out.println(data);
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

