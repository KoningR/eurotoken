package measurements

import nl.tudelft.ipv8.messaging.Serializable
import nl.tudelft.ipv8.messaging.eva.EVAProtocol
import nl.tudelft.ipv8.messaging.udp.UdpEndpoint

class StressPayload : Serializable {

    override fun serialize(): ByteArray {
        return TEST_PAYLOAD
    }

    companion object TestPacket {
        // Set the size of our test payload equal to the maximum allowed
        // packet size specified by EVA.
        internal val TEST_PAYLOAD = ByteArray(EVAProtocol.BLOCK_SIZE)

        init {
            TEST_PAYLOAD.fill(42)
        }
    }
}
