#define _GNU_SOURCE

#include <stdio.h>
#include <fcntl.h>
#include <time.h>
#include <unistd.h>
#include <sys/socket.h>
#include <arpa/inet.h>

#define MEASURE_DATA_SIZE 100000000
#define PAYLOAD_SIZE 1472

#define TO_PORT 25535

// The sender process sends messages of PAYLOAD_SIZE bytes.
// In total, it can thus send a bit more than MEASURE_DATA_SIZE bytes.
char data[MEASURE_DATA_SIZE + PAYLOAD_SIZE];

struct sockaddr_in senderAddress, receiverAddress;
socklen_t addressLength = sizeof(senderAddress);

struct timespec startTimeStruct, endTimeStruct;

/*
 * Calculates megabytes per second, given bytes and milliseconds.
 */
double throughputMbPerSecond(ssize_t bytes, double millis) {
    return ((double) bytes / 1000000) / (millis / 1000);
}

int main() {
    // Create a UDP socket.
    int socketHandle = socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP);
    if (socketHandle == -1) {
        perror("Could not get socket handle");
        return -1;
    }

    // Create a listening address.
    receiverAddress.sin_family = AF_INET;
    receiverAddress.sin_port = htons(TO_PORT);
    receiverAddress.sin_addr.s_addr = inet_addr("127.0.0.1");

    // Bind the address to the socket.
    if (bind(socketHandle, (struct sockaddr *) &receiverAddress,
             addressLength) == -1) {
        perror("Could not bind to socket");
        return -1;
    }

    // Wait for the first UDP message.
    if (recvfrom(socketHandle, data, PAYLOAD_SIZE, 0,
                 (struct sockaddr *) &senderAddress, &addressLength) == -1) {
        perror("Could not receive the first message");
        return -1;
    }

    // Get the start time of the receiving.
    if (clock_gettime(CLOCK_REALTIME, &startTimeStruct) == -1) {
        perror("Could not get start time");
        return -1;
    }

    // Set receiveBytes to PAYLOAD_SIZE because we have already received
    // that many bytes.
    ssize_t receiveBytes = PAYLOAD_SIZE;

    // Wait until enough bytes are received.
    while (receiveBytes < MEASURE_DATA_SIZE) {
        ssize_t receiveResult = recvfrom(socketHandle, &data[receiveBytes], PAYLOAD_SIZE, 0,
                                         (struct sockaddr *) &senderAddress, &addressLength);

        if (receiveResult == -1) {
            perror("Could not receive message");
            return -1;
        } else {
            receiveBytes += receiveResult;
        }
    }

    // Get the end time of the receiving.
    if (clock_gettime(CLOCK_REALTIME, &endTimeStruct) == -1) {
        perror("Could not get end time");
        return -1;
    }

    // Convert the measured times to milliseconds.
    double startTime =
            (double) startTimeStruct.tv_sec * 1000.0 + (double) startTimeStruct.tv_nsec * 1000.0 / 1000000000.0;
    double endTime = (double) endTimeStruct.tv_sec * 1000.0 + (double) endTimeStruct.tv_nsec * 1000.0 / 1000000000.0;

    // Close the socket when we are done.
    close(socketHandle);

    printf("Read %zi bytes in total\n", receiveBytes);
    printf("Last byte of received data (should be *) is: %c\n", data[receiveBytes - 1]);
    printf("Delta time in milliseconds: %f\n", endTime - startTime);
    printf("Throughput was %f megabytes per second\n", throughputMbPerSecond(receiveBytes, endTime - startTime));
    printf("Receiver is done\n");

    return 0;
}