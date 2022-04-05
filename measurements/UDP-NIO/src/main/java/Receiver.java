import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.DatagramChannel;

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
        final ByteBuffer data = ByteBuffer
                .allocateDirect(Sender.MEASURE_DATA_SIZE + Sender.PAYLOAD_SIZE)
                .order(ByteOrder.nativeOrder());

        final DatagramChannel channel;
        try {
            channel = DatagramChannel.open();
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        final DatagramSocket socket = channel.socket();

        // Bind the socket to the address of this receiver process.
        final SocketAddress receiver = new InetSocketAddress(Sender.TO_PORT);
        try {
            socket.bind(receiver);
        } catch (SocketException e) {
            e.printStackTrace();
            return;
        }

        // Get the address of the expected sender process so that we may connect()
        // it later on.
        final SocketAddress sender;
        try {
            sender = new InetSocketAddress(InetAddress.getByName(Sender.LOCALHOST), Sender.FROM_PORT);
        } catch (UnknownHostException e) {
            e.printStackTrace();
            return;
        }

        // Connect the address of the expected sender. Note that this is not strictly necessary; UDP does
        // not maintain connections like TCP does. However, if the channel is connected we can later
        // on use read() instead of receive() which skips a bit of unnecessary verification.
        try {
            channel.connect(sender);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        // Wait for the first packet to arrive. This can also
        // be done in the loop but this way we don't need
        // an additional condition for measuring the start time
        // once.
        try {

            // We limit() the data buffer before every read because read()
            // has a particular behaviour where it attempts to fill the entire
            // buffer with the contents of the channel. The weird thing is that
            // this seems to become very slow when the buffer is large.
            data.limit(Sender.PAYLOAD_SIZE);
            channel.read(data);

            System.out.println("Received first packet.");
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        final long startTime = System.currentTimeMillis();

        // Keep waiting for packets until enough bytes are read.
        while (data.position() < Sender.MEASURE_DATA_SIZE) {
            try {
                // TODO: Figure out why this limit() call speeds up performance so much.
                //  https://docs.oracle.com/javase/7/docs/api/java/nio/channels/ReadableByteChannel.html#read(java.nio.ByteBuffer)
                //  https://github.com/frohoff/jdk8u-jdk/blob/master/src/share/classes/sun/nio/ch/DatagramChannelImpl.java
                //  http://hg.openjdk.java.net/jdk8/jdk8/jdk/file/687fd7c7986d/src/share/classes/sun/nio/ch/IOUtil.java
                //  Potential clues: in the documentation
                //  it is stated that read() will attempt to read all remaining() bytes in the channel.
                //  If the buffer is 100mb, remaining() will on average be ~50mb, which perhaps affects performance.

                data.limit(data.position() + Sender.PAYLOAD_SIZE);
                channel.read(data);
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }
        }

        final long duration = System.currentTimeMillis() - startTime;

        System.out.format("Read %d bytes in %d milliseconds.\n", data.position(), duration);
        System.out.format("Throughput was %f megabytes per second.\n", throughputMbPerSecond(data.position(), duration));

        try {
            channel.close();
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        data.clear();
    }
}
