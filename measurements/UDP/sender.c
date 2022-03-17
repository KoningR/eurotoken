#define _GNU_SOURCE

#include <stdio.h>
#include <unistd.h>
#include <sys/socket.h>
#include <arpa/inet.h>

#define MEASURE_DATA_SIZE 100000000
// Because UDP loses messages, we send more messages than we
// measure on the other size.
#define TOTAL_DATA_SIZE 2 * MEASURE_DATA_SIZE

#define TO_PORT 25535
#define PAYLOAD_SIZE 1472

char data[PAYLOAD_SIZE];

struct sockaddr_in receiverAddress;
socklen_t addressLength = sizeof(receiverAddress);

int main() {
    // Fill the data array with '*' chars.
    for (int i = 0; i < sizeof(data); i++) {
        data[i] = '*';
    }

    // Create a UDP socket.
    int socketHandle = socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP);
    if (socketHandle == -1) {
        perror("Could not get socket handle");
        return -1;
    }

    // Create a target address.
    receiverAddress.sin_family = AF_INET;
    receiverAddress.sin_port = htons(TO_PORT);
    receiverAddress.sin_addr.s_addr = inet_addr("127.0.0.1");

    // Keep sending until we have sent enough bytes.
    ssize_t sentBytes = 0;
    while (sentBytes < TOTAL_DATA_SIZE) {
        // Keep resending the same payload.
        ssize_t sendResult = sendto(socketHandle, data, PAYLOAD_SIZE, 0,
                                    (struct sockaddr *) &receiverAddress, addressLength);

        if (sendResult == -1) {
            perror("Could not send message");
            return -1;
        } else {
            sentBytes += sendResult;
        }
    }

    // Close the socket when we are done.
    close(socketHandle);

    printf("Sender is done\n");

    return 0;
}