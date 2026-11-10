#include <iostream>
#include <winsock2.h>
#include <ws2tcpip.h>

#pragma comment(lib, "ws2_32.lib")

using namespace std;

#define PORT 2000

int main() {
    setlocale(LC_ALL, "Russian");

    WSADATA wsaData;
    if (WSAStartup(MAKEWORD(2, 2), &wsaData) != 0) {
        cerr << "Error WSAStartup" << endl;
        return 1;
    }

    SOCKET serverSocket = socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP);
    if (serverSocket == INVALID_SOCKET) {
        cerr << "Error socket" << endl;
        WSACleanup();
        return 1;
    }

    sockaddr_in serverAddr;
    serverAddr.sin_family = AF_INET;
    serverAddr.sin_addr.s_addr = INADDR_ANY;
    serverAddr.sin_port = htons(PORT);

    if (bind(serverSocket, (sockaddr*)&serverAddr, sizeof(serverAddr)) == SOCKET_ERROR) {
        cerr << "Error bind" << endl;
        closesocket(serverSocket);
        WSACleanup();
        return 1;
    }

    cout << "UDP Server started on port " << PORT << "..." << endl;

    char buffer[11];
    int totalChecksum = 0;

    while (true) {
        sockaddr_in clientAddr;
        int clientAddrSize = sizeof(clientAddr);

        int bytesReceived = recvfrom(serverSocket, buffer, 10, 0, (sockaddr*)&clientAddr, &clientAddrSize);
        if (bytesReceived > 0) {
            buffer[bytesReceived] = '\0';
            cout << "Received chunk: " << buffer << endl;

            for (int i = 0; i < bytesReceived; i++) {
                totalChecksum += (unsigned char)buffer[i];
            }

            cout << "Accumulated checksum: " << totalChecksum << endl;

            sendto(serverSocket, (char*)&totalChecksum, sizeof(totalChecksum), 0, (sockaddr*)&clientAddr, clientAddrSize);
        }
        else {
            cerr << "recvfrom error" << endl;
        }
    }

    closesocket(serverSocket);
    WSACleanup();

    return 0;
}