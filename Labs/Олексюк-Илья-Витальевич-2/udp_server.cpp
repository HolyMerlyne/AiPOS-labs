#define _CRT_SECURE_NO_WARNINGS
#define _WINSOCK_DEPRECATED_NO_WARNINGS

#include <stdio.h>
#include <string.h>
#include <winsock2.h>
#include <windows.h>

#pragma comment(lib, "ws2_32.lib")
#define MY_PORT 666

int main()
{
    char buff[4096];
    printf("UDP SERVER (variant 8: create <filename> <text>)\n");

    WSADATA wsa;
    if (WSAStartup(0x0202, &wsa)) {
        printf("Error WSAStartup %d\n", WSAGetLastError());
        return -1;
    }

    SOCKET mysocket = socket(AF_INET, SOCK_DGRAM, 0);
    if (mysocket == INVALID_SOCKET) {
        printf("Error socket %d\n", WSAGetLastError());
        WSACleanup(); return -1;
    }

    sockaddr_in local_addr;
    local_addr.sin_family = AF_INET;
    local_addr.sin_port = htons(MY_PORT);
    local_addr.sin_addr.s_addr = INADDR_ANY;

    if (bind(mysocket, (sockaddr*)&local_addr, sizeof(local_addr))) {
        printf("Error bind %d\n", WSAGetLastError());
        closesocket(mysocket); WSACleanup(); return -1;
    }
    printf("Waiting for datagrams on port %d...\n", MY_PORT);

    while (1)
    {
        sockaddr_in client_addr;
        int client_addr_size = sizeof(client_addr);

        int bytes_recv = recvfrom(mysocket, buff, sizeof(buff) - 1, 0,
            (sockaddr*)&client_addr, &client_addr_size);

        if (bytes_recv == SOCKET_ERROR) {
            printf("recvfrom error: %d\n", WSAGetLastError());
            continue;
        }
        buff[bytes_recv] = 0;

        for (int i = 0; i < bytes_recv; i++)
            if (buff[i] == '\r' || buff[i] == '\n') { buff[i] = 0; break; }

        printf("Received from %s:%d -> '%s'\n",
            inet_ntoa(client_addr.sin_addr), ntohs(client_addr.sin_port), buff);

        const char* err = NULL;
        if (strncmp(buff, "create ", 7) != 0) {
            err = "ERROR: unknown command. Use: create <filename> <text>\r\n";
        }
        else {
            char* p = buff + 7;
            while (*p == ' ') p++;
            char* filename = p;
            while (*p && *p != ' ') p++;

            if (*p == 0) {
                err = "ERROR: no text provided. Session terminated.\r\n";
            }
            else {
                *p++ = 0;
                while (*p == ' ') p++;
                char* text = p;

                FILE* f = fopen(filename, "wb");
                if (!f) {
                    err = "ERROR: cannot create file. Session terminated.\r\n";
                }
                else {
                    size_t written = fwrite(text, 1, strlen(text), f);
                    fclose(f);
                    if (written == 0) {
                        remove(filename);
                        err = "ERROR: file size is zero. Session terminated.\r\n";
                    }
                    else {
                        char answer[128];
                        sprintf(answer, "File '%s' created. Size = %u bytes.\r\n", filename, (unsigned)written);
                        sendto(mysocket, answer, (int)strlen(answer), 0, (sockaddr*)&client_addr, client_addr_size);
                        printf("Success: %s", answer);
                    }
                }
            }
        }

        if (err) {
            sendto(mysocket, err, (int)strlen(err), 0, (sockaddr*)&client_addr, client_addr_size);
            printf("Error sent: %s", err);
        }
    }

    closesocket(mysocket);
    WSACleanup();
    return 0;
}
