#include <iostream>
#include <fstream>
#include <string>
#include <winsock2.h>
#include <ctime>

#pragma comment(lib, "ws2_32.lib")

const int SERVER_PORT = 5555;

void logEvent(const std::string &direction, const std::string &data)
{
    std::ofstream logFile("server_protocol.txt", std::ios::app);
    if (logFile.is_open())
    {
        std::time_t now = std::time(0);
        char *dt = std::ctime(&now);
        std::string timeStr(dt);
        if (!timeStr.empty())
            timeStr.pop_back();

        logFile << "[" << timeStr << "] " << direction << ": " << data << std::endl;
        logFile.close();
    }
}

bool processAndSaveFile(const std::string &rawData, std::string &outResponse)
{
    if (rawData.find("save ") != 0)
    {
        return false;
    }

    size_t nameStart = 5;
    size_t nameEnd = rawData.find_first_of(" \r\n", nameStart);

    if (nameEnd == std::string::npos)
    {
        outResponse = "Ошибка: неверный формат команды.\n";
        return false;
    }

    std::string filename = rawData.substr(nameStart, nameEnd - nameStart);

    size_t dataEnd = rawData.find("#end##");
    if (dataEnd == std::string::npos || dataEnd <= nameEnd)
    {
        outResponse = "Ошибка: маркер конца данных не найден.\n";
        return false;
    }

    std::string fileContent = rawData.substr(nameEnd + 1, dataEnd - nameEnd - 1);

    std::ofstream outFile(filename.c_str(), std::ios::binary);
    if (outFile.is_open())
    {
        outFile << fileContent;
        outFile.close();
        outResponse = "Успех: Файл '" + filename + "' создан.\n";
        return true;
    }
    else
    {
        outResponse = "Ошибка: Не удалось создать файл.\n";
        return false;
    }
}

int main()
{
    WSADATA wsaData;
    if (WSAStartup(MAKEWORD(2, 2), &wsaData) != 0)
    {
        std::cerr << "Сбой инициализации Winsock." << std::endl;
        return 1;
    }

    SOCKET listenSocket = socket(AF_INET, SOCK_STREAM, IPPROTO_TCP);
    if (listenSocket == INVALID_SOCKET)
    {
        std::cerr << "Ошибка создания сокета." << std::endl;
        WSACleanup();
        return 1;
    }

    sockaddr_in serverService;
    serverService.sin_family = AF_INET;
    serverService.sin_addr.s_addr = INADDR_ANY;
    serverService.sin_port = htons(SERVER_PORT);

    if (bind(listenSocket, (sockaddr *)&serverService, sizeof(serverService)) == SOCKET_ERROR)
    {
        std::cerr << "Ошибка привязки к порту." << std::endl;
        closesocket(listenSocket);
        WSACleanup();
        return 1;
    }

    if (listen(listenSocket, SOMAXCONN) == SOCKET_ERROR)
    {
        std::cerr << "Ошибка прослушивания порта." << std::endl;
        closesocket(listenSocket);
        WSACleanup();
        return 1;
    }

    std::cout << "Сервер запущен и слушает порт " << SERVER_PORT << "..." << std::endl;

    sockaddr_in clientInfo;
    int clientInfoSize = sizeof(clientInfo);
    SOCKET connectionSocket = accept(listenSocket, (sockaddr *)&clientInfo, &clientInfoSize);

    if (connectionSocket != INVALID_SOCKET)
    {
        std::cout << "Клиент подключился!" << std::endl;

        std::string accumulatedData = "";
        char receiveBuffer[512];

        while (true)
        {
            int bytesRead = recv(connectionSocket, receiveBuffer, sizeof(receiveBuffer) - 1, 0);

            if (bytesRead <= 0)
            {
                std::cout << "Клиент отключился." << std::endl;
                break;
            }

            receiveBuffer[bytesRead] = '\0';
            accumulatedData.append(receiveBuffer);

            size_t markerPos = accumulatedData.find("#end##");
            if (markerPos != std::string::npos)
            {
                std::string responseMsg;
                std::string packet = accumulatedData.substr(0, markerPos + 6);

                // Логируем полученную от клиента строку и время приема
                logEvent("Принято от клиента", packet);

                bool success = processAndSaveFile(packet, responseMsg);

                send(connectionSocket, responseMsg.c_str(), responseMsg.length(), 0);

                // Логируем отправленную клиенту строку и время передачи
                logEvent("Передано клиенту", responseMsg);

                if (success)
                {
                    std::cout << "Файл успешно сохранен." << std::endl;
                    accumulatedData.erase(0, markerPos + 6);
                }
                else
                {
                    std::cerr << "Ошибка сохранения файла. Отключение клиента." << std::endl;
                    closesocket(connectionSocket);
                    break;
                }
            }
        }
        closesocket(connectionSocket);
    }

    closesocket(listenSocket);
    WSACleanup();
    return 0;
}