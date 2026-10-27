#define _CRT_SECURE_NO_WARNINGS

#include <iostream>
#include <fstream>
#include <string>
#include <ctime>
#include <conio.h>
#include <winsock2.h>
#include <ws2tcpip.h>

#pragma comment(lib, "ws2_32.lib")

using namespace std;

#define SERVER_IP "127.0.0.1"
#define PORT 2000

string getTime() {
    time_t now = time(0);
    tm* ltm = localtime(&now);
    char buf[80];
    strftime(buf, sizeof(buf), "%Y-%m-%d %H:%M:%S", ltm);
    return string(buf);
}

void writeLog(string text) {
    ofstream log("log.txt", ios::app);
    if (log.is_open()) {
        log << "[" << getTime() << "] " << text << endl;
        log.close();
    }
}

const string TOTAL_FILE = "total_checksum.txt";

long long loadTotal() {
    ifstream in(TOTAL_FILE);
    long long value = 0;
    if (in.is_open()) {
        in >> value;
        in.close();
    }
    return value;
}

void saveTotal(long long value) {
    ofstream out(TOTAL_FILE);
    if (out.is_open()) {
        out << value;
        out.close();
    }
}

int main() {
    setlocale(LC_ALL, "Russian");

    long long persistentTotal = loadTotal();
    cout << "Persistent total checksum: " << persistentTotal << endl;

    WSADATA wsaData;
    if (WSAStartup(MAKEWORD(2, 2), &wsaData) != 0) {
        cerr << "Error WSAStartup" << endl;
        return 1;
    }

    SOCKET clientSocket = socket(AF_INET, SOCK_STREAM, IPPROTO_TCP);
    if (clientSocket == INVALID_SOCKET) {
        cerr << "Error socket" << endl;
        WSACleanup();
        return 1;
    }

    sockaddr_in serverAddr;
    serverAddr.sin_family = AF_INET;
    serverAddr.sin_port = htons(PORT);
    inet_pton(AF_INET, SERVER_IP, &serverAddr.sin_addr);

    cout << "Connecting to server..." << endl;

    if (connect(clientSocket, (sockaddr*)&serverAddr, sizeof(serverAddr)) == SOCKET_ERROR) {
        cerr << "Connection failed." << endl;
        closesocket(clientSocket);
        WSACleanup();
        return 1;
    }

    cout << "Connected successfully!" << endl;
    writeLog("Start connection with " + string(SERVER_IP) + ":" + to_string(PORT));

    cout << "Enter text (Press PgDn to send, Esc to exit, F2 to clear total):" << endl;

    string currentLine = "";
    bool connected = true;

    cout << "> ";

    while (true) {
        if (_kbhit()) {
            int ch = _getch();

            if (ch == 27) {
                break;
            }
            else if (ch == 0 || ch == 224) {
                int specKey = _getch();

                if (specKey == 81) {
                    if (!connected) {
                        cout << "\nNot connected." << endl << "> ";
                        continue;
                    }

                    if (currentLine == "disconnect") {
                        cout << "\nDisconnecting..." << endl;
                        writeLog("End connection (disconnect)");
                        closesocket(clientSocket);
                        connected = false;
                        currentLine = "";
                        cout << "> ";
                        continue;
                    }

                    if (currentLine.empty()) {
                        cout << "\nEmpty string." << endl << "> ";
                        continue;
                    }

                    cout << "\n[PgDn] Sending line..." << endl;

                    writeLog("Send string to server: " + currentLine);

                    int lastAccumulatedChecksum = 0;

                    for (size_t i = 0; i < currentLine.length(); i += 10) {
                        string chunk = currentLine.substr(i, 10);

                        send(clientSocket, chunk.c_str(), chunk.length(), 0);

                        int accumulatedChecksum = 0;
                        int bytesReceived = recv(clientSocket, (char*)&accumulatedChecksum, sizeof(accumulatedChecksum), 0);

                        if (bytesReceived > 0) {
                            lastAccumulatedChecksum = accumulatedChecksum;
                            cout << "Chunk: " << chunk << " | Total Checksum: " << accumulatedChecksum << endl;
                        }
                        else {
                            cout << "Server disconnected." << endl;
                            connected = false;
                            break;
                        }
                    }

                    if (connected) {
                        persistentTotal += lastAccumulatedChecksum;
                        saveTotal(persistentTotal);
                        cout << "Persistent total checksum: " << persistentTotal << endl;
                    }

                    currentLine = "";
                    cout << "> ";
                }
                else if (specKey == 60) { // F2
                    persistentTotal = 0;
                    saveTotal(persistentTotal);
                    cout << "\nPersistent total checksum cleared." << endl << "> ";
                }
            }
            else if (ch == 8) {
                if (!currentLine.empty()) {
                    currentLine.pop_back();
                    cout << "\b \b";
                }
            }
            else if (ch == 13) {
                cout << endl << "> ";
            }
            else {
                currentLine += (char)ch;
                cout << (char)ch;
            }
        }
    }

    if (connected) {
        writeLog("End connection");
        closesocket(clientSocket);
    }

    WSACleanup();
    return 0;
}