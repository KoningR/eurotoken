package stresstest

import nl.tudelft.ipv8.messaging.Serializable
import nl.tudelft.ipv8.messaging.udp.UdpEndpoint

class StressPayload : Serializable {

    override fun serialize(): ByteArray {
        return TEST_PAYLOAD
    }

    companion object TestPacket {
        // The UDP_PAYLOAD_LIMIT does not correspond to the maximal payload allowed by ipv8,
        // due to possible encryption and signing.
        private val TEST_PAYLOAD = ByteArray(UdpEndpoint.UDP_PAYLOAD_LIMIT - 219)

        init {
            TEST_PAYLOAD.fill(42)
        }
    }
}
