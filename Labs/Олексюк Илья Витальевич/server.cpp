#define _CRT_SECURE_NO_WARNINGS
#define _WINSOCK_DEPRECATED_NO_WARNINGS

#include <stdio.h>
#include <string.h>
#include <winsock2.h>
#include <windows.h>

#pragma comment(lib, "ws2_32.lib")
#define MY_PORT 666

DWORD WINAPI WorkWithClient(LPVOID client_socket);
int nclients = 0;

int main()
{
    char buff[1024];
    printf("TCP SERVER (variant 8: create <filename> <text>)\n");

    if (WSAStartup(0x0202, (WSADATA*)buff)) {
        printf("Error WSAStartup %d\n", WSAGetLastError());
        return -1;
    }

    SOCKET mysocket = socket(AF_INET, SOCK_STREAM, 0);
    if (mysocket == INVALID_SOCKET) {
        printf("Error socket %d\n", WSAGetLastError());
        WSACleanup(); return -1;
    }

    sockaddr_in local_addr;
    local_addr.sin_family = AF_INET;
    local_addr.sin_port = htons(MY_PORT);
    local_addr.sin_addr.s_addr = 0;

    if (bind(mysocket, (sockaddr*)&local_addr, sizeof(local_addr))) {
        printf("Error bind %d\n", WSAGetLastError());
        closesocket(mysocket); WSACleanup(); return -1;
    }

    if (listen(mysocket, 0x100)) {
        printf("Error listen %d\n", WSAGetLastError());
        closesocket(mysocket); WSACleanup(); return -1;
    }
    printf("Waiting for connections on port %d...\n", MY_PORT);

    SOCKET client_socket;
    sockaddr_in client_addr;
    int client_addr_size = sizeof(client_addr);

    while (1)
    {
        client_socket = accept(mysocket, (sockaddr*)&client_addr, &client_addr_size);

        if (client_socket == INVALID_SOCKET) {
            printf("Accept failed: %d\n", WSAGetLastError());
            break;
        }

        nclients++;

        HOSTENT* hst = gethostbyaddr((char*)&client_addr.sin_addr.s_addr, 4, AF_INET);
        printf("+%s [%s] new connect!\n%d user on-line\n",
            hst ? hst->h_name : "", inet_ntoa(client_addr.sin_addr), nclients);

        SOCKET* p_sock = new SOCKET(client_socket);

        DWORD thID;
        CreateThread(NULL, NULL, WorkWithClient, p_sock, NULL, &thID);
    }

    closesocket(mysocket);
    WSACleanup();
    return 0;
}

DWORD WINAPI WorkWithClient(LPVOID client_socket)
{
    SOCKET* p_sock = (SOCKET*)client_socket;
    SOCKET my_sock = *p_sock;
    delete p_sock;

    char buff[4096];
    int bytes_recv;

    send(my_sock, "Hello! Command: create <filename> <text>\r\n", 43, 0);

    int total = 0;
    while (1)
    {
        if (total >= (int)sizeof(buff) - 1) {
            printf("Buffer overflow! Terminating.\n");
            break;
        }

        bytes_recv = recv(my_sock, buff + total, sizeof(buff) - 1 - total, 0);

        if (bytes_recv <= 0) {
            closesocket(my_sock);
            nclients--; 
            printf("--disconnect\nNo User on line\n");
            return 0;
        }

        total += bytes_recv;
        buff[total] = 0;

        if (strstr(buff, "\r\n") || strchr(buff, '\n')) {
            break;
        }
    }

    for (int i = 0; i < total; i++)
        if (buff[i] == '\r' || buff[i] == '\n') { buff[i] = 0; break; }

    printf("Client command: '%s'\n", buff);

    const char* err = NULL;
    if (strncmp(buff, "create ", 7) != 0)
        err = "ERROR: unknown command. Use: create <filename> <text>\r\nSession terminated.\r\n";
    else {
        char* p = buff + 7;
        while (*p == ' ') p++;
        char* filename = p;
        while (*p && *p != ' ') p++;

        if (*p == 0)
            err = "ERROR: no text provided. Session terminated.\r\n";
        else {
            *p++ = 0;
            while (*p == ' ') p++;
            char* text = p;

            printf("Creating file '%s' with text '%s'\n", filename, text);

            FILE* f = fopen(filename, "wb");
            if (!f)
                err = "ERROR: cannot create file. Session terminated.\r\n";
            else {
                size_t written = fwrite(text, 1, strlen(text), f);
                fclose(f);

                if (written == 0) {
                    remove(filename);
                    err = "ERROR: file size is zero. Session terminated.\r\n";
                }
                else {
                    char answer[128];
                    sprintf(answer, "File '%s' created. Size = %u bytes.\r\n",
                        filename, (unsigned)written);
                    send(my_sock, answer, (int)strlen(answer), 0);
                    printf("File '%s' created (%u bytes)\n", filename, (unsigned)written);
                }
            }
        }
    }

    if (err) send(my_sock, err, (int)strlen(err), 0);

    closesocket(my_sock);

    nclients--;
    printf("--disconnect\nNo User on line\n");

    return 0;
}
