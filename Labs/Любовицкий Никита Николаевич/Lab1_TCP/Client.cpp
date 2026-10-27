#define _WINSOCK_DEPRECATED_NO_WARNINGS
#define _CRT_SECURE_NO_WARNINGS
#include <iostream>
#include <fstream>
#include <string>
#include <winsock2.h>
#include <conio.h>
#include <ctime>

#pragma comment(lib, "ws2_32.lib")

void appendLogEntry(const std::string &type, const std::string &payload)
{
    std::ofstream outFile("client_protocol.txt", std::ios::app);
    if (!outFile.is_open())
        return;

    std::time_t rawTime = std::time(nullptr);
    char *timeStr = std::ctime(&rawTime);
    std::string formattedTime(timeStr);
    if (!formattedTime.empty())
    {
        formattedTime.pop_back();
    }
    outFile << "[" << formattedTime << "] " << type << ": " << payload << std::endl;
    outFile.close();
}

int main()
{
    WSADATA wsaData;
    if (WSAStartup(MAKEWORD(2, 2), &wsaData) != 0)
    {
        std::cerr << "Ошибка инициализации Winsock." << std::endl;
        return 1;
    }

    std::string userCommand, serverIp;
    int serverPort;

    std::cout << "Введите команду подключения (пример: connect <IP> <PORT>)" << std::endl;
    std::cout << "Пример: connect 127.0.0.1 5555" << std::endl
              << "> ";

    while (true)
    {
        std::cin >> userCommand >> serverIp >> serverPort;
        if (userCommand == "connect")
        {
            std::cin.ignore(10000, '\n');
            break;
        }
        std::cout << "Неверная команда. Попробуйте снова: > ";
        std::cin.clear();
        std::cin.ignore(10000, '\n');
    }

    SOCKET clientSock = socket(AF_INET, SOCK_STREAM, IPPROTO_TCP);
    if (clientSock == INVALID_SOCKET)
    {
        std::cerr << "Не удалось создать сокет." << std::endl;
        WSACleanup();
        return 1;
    }

    sockaddr_in serverAddress;
    serverAddress.sin_family = AF_INET;
    serverAddress.sin_addr.s_addr = inet_addr(serverIp.c_str());
    serverAddress.sin_port = htons(serverPort);

    if (connect(clientSock, (sockaddr *)&serverAddress, sizeof(serverAddress)) == SOCKET_ERROR)
    {
        std::cerr << "Не удалось подключиться к серверу!" << std::endl;
        closesocket(clientSock);
        WSACleanup();
        return 1;
    }

    std::cout << "Соединение с сервером установлено!" << std::endl;
    std::cout << "Введите 'save <имя_файла> <содержимое>#end##' для теста." << std::endl;
    std::cout << "Нажмите 'PgUp' для отправки введенной строки." << std::endl;

    u_long nonBlocking = 1;
    ioctlsocket(clientSock, FIONBIO, &nonBlocking);

    std::string messageBuffer = "";

    while (true)
    {
        char networkBuffer[512];
        int receivedBytes = recv(clientSock, networkBuffer, sizeof(networkBuffer) - 1, 0);

        if (receivedBytes > 0)
        {
            networkBuffer[receivedBytes] = '\0';
            std::string serverMessage(networkBuffer);
            std::cout << "\n[Сервер]: " << serverMessage << "> " << messageBuffer;
            appendLogEntry("Получено", serverMessage);
        }
        else if (receivedBytes == 0 || (receivedBytes == SOCKET_ERROR && WSAGetLastError() != WSAEWOULDBLOCK))
        {
            std::cout << "\nСервер разорвал соединение." << std::endl;
            break;
        }

        if (_kbhit())
        {
            int inputChar = _getch();
            if (inputChar == 0 || inputChar == 224)
            {
                int extendedChar = _getch();
                if (extendedChar == 73)
                {
                    if (!messageBuffer.empty())
                    {
                        send(clientSock, messageBuffer.c_str(), messageBuffer.length(), 0);
                        appendLogEntry("Отправлено", messageBuffer);
                        std::cout << "\n[Отправлено]: " << messageBuffer << std::endl
                                  << "> ";
                        messageBuffer.clear();
                    }
                }
            }
            else if (inputChar == '\r')
            {
                messageBuffer += '\n';
                std::cout << std::endl;
            }
            else if (inputChar == '\b')
            {
                if (!messageBuffer.empty())
                {
                    messageBuffer.pop_back();
                    std::cout << "\b \b";
                }
            }
            else
            {
                messageBuffer += static_cast<char>(inputChar);
                std::cout << static_cast<char>(inputChar);
            }
        }
        Sleep(20);
    }

    closesocket(clientSock);
    WSACleanup();
    return 0;
}