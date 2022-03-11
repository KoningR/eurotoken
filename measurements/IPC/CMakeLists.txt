cmake_minimum_required(VERSION 3.21)
project(Throughput C)

set(CMAKE_C_STANDARD 23)

add_executable(Sender sender.c)
add_executable(Receiver receiver.c)
