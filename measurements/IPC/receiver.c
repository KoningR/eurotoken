#define _GNU_SOURCE
#define MIN(a, b) ((a) < (b) ? (a) : (b))

#include <stdio.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <time.h>
#include <unistd.h>

#define DATA_SIZE 100000000

char payload[DATA_SIZE];
char *fileName = "/tmp/throughputfifo";

struct timespec endTimeStruct;

/*
 * Calculates megabytes per second, given bytes and milliseconds.
 */
double throughputMbPerSecond(ssize_t bytes, double millis) {
    return ((double) bytes / 1000000) / (millis / 1000);
}

int main() {
    // Assuming this process is started after the sender process, this thread won't stall
    // because the FIFO is already opened on the other end.
    mkfifo(fileName, 0666);
    int fileHandle = open(fileName, O_RDONLY);
    if (fileHandle == -1) {
        perror("FIFO file could not be opened");
        return -1;
    }
    int pipeSize = fcntl(fileHandle, F_GETPIPE_SZ);

    // Receive the payload.
    ssize_t readBytes = 0;
    while (readBytes < DATA_SIZE) {
        ssize_t readResult = read(fileHandle, &payload[readBytes], MIN(DATA_SIZE - readBytes, pipeSize));

        if (readResult == -1) {
            perror("FIFO file could not be read");
            return -1;
        } else {
            readBytes += readResult;
        }
    }

    // Get the end time of the sending.
    if (clock_gettime(CLOCK_REALTIME, &endTimeStruct) == -1) {
        perror("Could not get system time");
        return -1;
    }
    double endTime = (double) endTimeStruct.tv_sec * 1000.0 + (double) endTimeStruct.tv_nsec * 1000.0 / 1000000000.0;

    // Get the start time of the sending from the sender process over the pipe.
    double startTime;
    if (read(fileHandle, &startTime, sizeof(startTime)) == -1) {
        perror("FIFO file could not be read (when reading timestamp)");
        return -1;
    }

    close(fileHandle);
    remove(fileName);

    printf("Read %zi bytes in total\n", readBytes);
    printf("Memory address of received data: %p\n", payload);
    printf("Last byte of received data (should be *): %c\n", payload[DATA_SIZE - 1]);
    printf("Delta time in milliseconds: %f\n", endTime - startTime);
    printf("Throughput was %f megabytes per second\n", throughputMbPerSecond(readBytes, endTime - startTime));
    printf("Receiver is done");

    return 0;
}