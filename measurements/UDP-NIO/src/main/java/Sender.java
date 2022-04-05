import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.DatagramChannel;

public class Sender {
    protected static final String LOCALHOST = "localhost";
    protected static final int FROM_PORT = 25595;
    protected static final int TO_PORT = 25535;
    protected static final int PAYLOAD_SIZE = 1492;

    protected static final int MEASURE_DATA_SIZE = 100000000;
    // Because UDP loses messages, the sender process
    // sends more data than the receiver process will receive.
    private static final int TOTAL_DATA_SIZE = 2 * MEASURE_DATA_SIZE;

    /**
     * Resend the same UDP packet each time.
     */
    private static void useSamePacket() {
        // Create a buffer that will contain the payload.
        final ByteBuffer buffer = ByteBuffer
                .allocateDirect(PAYLOAD_SIZE)
                .order(ByteOrder.nativeOrder());

        // Fill the buffer with the payload.
        for (int i = 0; i < buffer.limit(); i++) {
            buffer.put((byte) 0x42);
        }
        buffer.position(0);

        final DatagramChannel channel;
        try {
            channel = DatagramChannel.open();
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        final DatagramSocket socket = channel.socket();

        final SocketAddress sender = new InetSocketAddress(FROM_PORT);
        try {
            socket.bind(sender);
        } catch (SocketException e) {
            e.printStackTrace();
            return;
        }

        // Get the address of the receiver process.
        final SocketAddress receiver;
        try {
            receiver = new InetSocketAddress(InetAddress.getByName(LOCALHOST), TO_PORT);
        } catch (UnknownHostException e) {
            e.printStackTrace();
            return;
        }

        // Connect to the receiver process. Note that this is not strictly necessary; UDP does
        // not maintain connections like TCP does. However, if the channel is connected we can later
        // on use write() instead of send() which skips a bit of unnecessary verification.
        try {
            channel.connect(receiver);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        // Keep sending packets until enough bytes are sent.
        // Note that there is no guarantee that MEASURE_DATA_SIZE
        // bytes will actually arrive; we simply assume that
        // sending TOTAL_DATA_SIZE bytes is sufficient.
        int sentBytes = 0;
        while (sentBytes < TOTAL_DATA_SIZE) {
            try {
                channel.write(buffer);

                // We send the same payload each time, thus we reset the buffer
                // position after each send.
                buffer.position(0);
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }
            sentBytes += PAYLOAD_SIZE;
        }

        try {
            channel.close();
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        buffer.clear();
    }

    public static void main(String[] args) {
        useSamePacket();
    }
}
