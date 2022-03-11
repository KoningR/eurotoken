#define _GNU_SOURCE
#define MIN(a, b) ((a) < (b) ? (a) : (b))

#include <stdio.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <time.h>
#include <unistd.h>
#include <errno.h>

#define DATA_SIZE 100000000

char payload[DATA_SIZE];
char *fileName = "/tmp/throughputfifo";

struct timespec startTimeStruct;

int main() {
    // Fill the payload array with '*' chars.
    for (int i = 0; i < sizeof(payload); i++) {
        payload[i] = '*';
    }

    printf("Opening FIFO, then waiting for a receiver process to start\n");

    // Because the file is a FIFO, this thread stalls until it is opened elsewhere.
    mkfifo(fileName, 0666);
    int fileHandle = open(fileName, O_WRONLY);
    if (fileHandle == -1) {
        perror("FIFO file could not be opened");
        return -1;
    }
    int pipeSize = fcntl(fileHandle, F_GETPIPE_SZ);

    // Get the start time of the sending.
    if (clock_gettime(CLOCK_REALTIME, &startTimeStruct) == -1) {
        perror("Could not get system time");
        return -1;
    }

    // Send the payload.
    ssize_t sentBytes = 0;
    while (sentBytes < DATA_SIZE) {
        ssize_t writeResult = write(fileHandle, &payload[sentBytes], MIN(DATA_SIZE - sentBytes, pipeSize));

        if (writeResult == -1) {
            perror("FIFO file could not be written to");
            printf("Error is %i", errno);
            printf("Wrote %zi bytes in total\n", sentBytes);
            return -1;
        } else {
            sentBytes += writeResult;
        }
    }

    // Send the previously measured start time over the pipe. The end time will be measured by the receiver process.
    double startTime =
            (double) startTimeStruct.tv_sec * 1000 + (double) startTimeStruct.tv_nsec * 1000.0 / 1000000000.0;
    if (write(fileHandle, &startTime, sizeof(startTime)) == -1) {
        perror("FIFO file could not be written to (when writing timestamp).");
        return -1;
    }

    close(fileHandle);

    printf("Wrote %zi bytes in total\n", sentBytes);
    printf("Start time in milliseconds: %f\n", startTime);
    printf("Memory address of sent data: %p\n", payload);
    printf("Sender is done\n");

    return 0;
}