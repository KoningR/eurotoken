import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;

public class Receiver {

    /**
     * Convert bytes per millisecond to megabytes per second.
     */
    public static double throughputMbPerSecond(int bytes, long millis) {
        return ((double) bytes / 1000000) / ((double) millis / 1000);
    }

    public static void main(String[] args) {
        // The sender process will sometimes send packets of the same
        // size and thus send a bit more than SEND_DATA_SIZE bytes.
        byte[] data = new byte[Sender.MEASURE_DATA_SIZE + Sender.PAYLOAD_SIZE];

        // Bind the receiver process's destination port.
        DatagramSocket socket;
        try {
            socket = new DatagramSocket(Sender.TO_PORT);
        } catch (SocketException e) {
            e.printStackTrace();
            return;
        }

        // Wait for the first packet to arrive. This can also
        // be done in the loop but this way we don't need
        // an additional condition for measuring the start time
        // once.
        int readBytes = 0;
        DatagramPacket firstPacket = new DatagramPacket(data, readBytes, Sender.PAYLOAD_SIZE);
        try {
            socket.receive(firstPacket);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        long startTime = System.currentTimeMillis();

        // Keep waiting for packets until enough bytes are read.
        readBytes = firstPacket.getLength();
        while (readBytes < Sender.MEASURE_DATA_SIZE) {
            DatagramPacket packet = new DatagramPacket(data, readBytes, Sender.PAYLOAD_SIZE);
            try {
                socket.receive(packet);
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }

            readBytes += packet.getLength();
        }

        long duration = System.currentTimeMillis() - startTime;

        System.out.format("Read %d bytes in %d milliseconds.\n", readBytes, duration);
        System.out.format("Throughput was %f megabytes per second.\n", throughputMbPerSecond(readBytes, duration));
    }
}
