public class AppUtil {
    enum AppMode {
        ADD((byte) 0x10),
        SPEND((byte) 0x20),
        VIEW((byte) 0x30),
        PERSONALIZE((byte) 0x40),
        DATA_SENDING((byte) 0x50);

        public final Byte mode;

        private AppMode(Byte mode) {
            this.mode = mode;
        }
    }

    // App communication state
    enum AppComState {
        SEND_CERTIFICATE((byte) 0x10),
        SEND_AMOUNT_CHECK((byte) 0x11),
        ACK_ONLINE((byte) 0x20),
        SEND_LENGTH((byte) 0x41),
        SEND_INFO((byte) 0x42),
        PROCESS_INFO((byte) 0x43);
        public final Byte mode;

        private AppComState(Byte mode) {
            this.mode = mode;
        }
    }
}
