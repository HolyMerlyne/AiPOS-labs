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

    SOCKET listenSocket = socket(AF_INET, SOCK_STREAM, IPPROTO_TCP);
    if (listenSocket == INVALID_SOCKET) {
        cerr << "Error socket" << endl;
        WSACleanup();
        return 1;
    }

    sockaddr_in serverAddr;
    serverAddr.sin_family = AF_INET;
    serverAddr.sin_addr.s_addr = INADDR_ANY;
    serverAddr.sin_port = htons(PORT);

    if (bind(listenSocket, (sockaddr*)&serverAddr, sizeof(serverAddr)) == SOCKET_ERROR) {
        cerr << "Error bind" << endl;
        closesocket(listenSocket);
        WSACleanup();
        return 1;
    }

    if (listen(listenSocket, SOMAXCONN) == SOCKET_ERROR) {
        cerr << "Error listen" << endl;
        closesocket(listenSocket);
        WSACleanup();
        return 1;
    }

    cout << "Server started on port " << PORT << "..." << endl;

    sockaddr_in clientAddr;
    int clientAddrSize = sizeof(clientAddr);
    SOCKET clientSocket = accept(listenSocket, (sockaddr*)&clientAddr, &clientAddrSize);

    if (clientSocket == INVALID_SOCKET) {
        closesocket(listenSocket);
        WSACleanup();
        return 1;
    }

    cout << "Client connected!" << endl;

    char buffer[11];
    int totalChecksum = 0;

    while (true) {
        int bytesReceived = recv(clientSocket, buffer, 10, 0);
        if (bytesReceived > 0) {
            buffer[bytesReceived] = '\0';
            cout << "Received chunk: " << buffer << endl;

            for (int i = 0; i < bytesReceived; i++) {
                totalChecksum += (unsigned char)buffer[i];
            }

            cout << "Accumulated checksum: " << totalChecksum << endl;

            send(clientSocket, (char*)&totalChecksum, sizeof(totalChecksum), 0);
        }
        else {
            cout << "Client disconnected." << endl;
            break;
        }
    }

    closesocket(clientSocket);
    closesocket(listenSocket);
    WSACleanup();

    return 0;
}