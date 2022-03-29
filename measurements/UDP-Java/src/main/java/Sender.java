import java.io.IOException;
import java.net.*;
import java.util.Arrays;

public class Sender {
    private static final String RECEIVER_ADDRESS = "localhost";
    private static final int FROM_PORT = 25595;
    protected static final int TO_PORT = 25535;
    protected static final int PAYLOAD_SIZE = 1472;

    protected static final int MEASURE_DATA_SIZE = 100000000;
    // Because UDP loses messages, the sender process
    // sends more data than the receiver process will receive.
    private static final int TOTAL_DATA_SIZE = 2 * MEASURE_DATA_SIZE;

    /**
     * Resend the same UDP packet each time.
     */
    private static void useSamePacket() {
        // Fill the array that will be sent.
        final byte[] data = new byte[TOTAL_DATA_SIZE];
        Arrays.fill(data, (byte) 0x42);

        // Bind the port and create the fixed packet.
        final InetAddress receiver;
        try {
            receiver = InetAddress.getByName(RECEIVER_ADDRESS);
        } catch (UnknownHostException e) {
            e.printStackTrace();
            return;
        }

        final DatagramPacket packet = new DatagramPacket(data, PAYLOAD_SIZE, receiver, TO_PORT);
        final DatagramSocket socket;
        try {
            socket = new DatagramSocket(FROM_PORT);
        } catch (SocketException e) {
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
                socket.send(packet);
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }

            sentBytes += PAYLOAD_SIZE;
        }
    }

    /**
     * Create a new UDP packet each time.
     */
    private static void createNewPacket() {
        // Fill the array that will be sent.
        final byte[] data = new byte[TOTAL_DATA_SIZE];
        Arrays.fill(data, (byte) 0x42);

        // Bind the port.
        final InetAddress receiver;
        try {
            receiver = InetAddress.getByName(RECEIVER_ADDRESS);
        } catch (UnknownHostException e) {
            e.printStackTrace();
            return;
        }

        final DatagramSocket socket;
        try {
            socket = new DatagramSocket(FROM_PORT);
        } catch (SocketException e) {
            e.printStackTrace();
            return;
        }

        // Keep sending packets until enough bytes are sent.
        // Note that there is no guarantee that MEASURE_DATA_SIZE
        // bytes will actually arrive; we simply assume that
        // sending TOTAL_DATA_SIZE bytes is sufficient.
        int sentBytes = 0;
        while (sentBytes < TOTAL_DATA_SIZE) {
            // Recreate the packet with each send.
            // The packet's length is either PAYLOAD_SIZE
            // or smaller when we send the very last packet.
            final DatagramPacket packet = new DatagramPacket(
                    data, sentBytes,
                    Math.min(PAYLOAD_SIZE, TOTAL_DATA_SIZE - sentBytes),
                    receiver, TO_PORT);

            try {
                socket.send(packet);
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }

            sentBytes += PAYLOAD_SIZE;
        }
    }

    public static void main(String[] args) {
        useSamePacket();
//        createNewPacket();
    }
}
