public class AppUtil {
    enum AppMode {
        ADD((byte) 0x10),
        SPEND((byte) 0x20),
        VIEW((byte) 0x30);

        public final Byte mode;

        private AppMode(Byte mode) {
            this.mode = mode;
        }
    }

    // App communication state
    enum AppComState {
        SEND_CERTIFICATE((byte) 0x10),
        ACK_ONLINE((byte) 0x20);
        public final Byte mode;

        private AppComState(Byte mode) {
            this.mode = mode;
        }
    }
}
