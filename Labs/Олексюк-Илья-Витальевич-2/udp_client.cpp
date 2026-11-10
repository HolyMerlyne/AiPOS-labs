#define _CRT_SECURE_NO_WARNINGS
#define _WINSOCK_DEPRECATED_NO_WARNINGS

#include <stdio.h>
#include <string.h>
#include <stdarg.h>
#include <time.h>
#include <conio.h>
#include <winsock2.h>
#include <windows.h>

#pragma comment(lib, "ws2_32.lib")

#define PORT        666
#define SERVERADDR  "127.0.0.1"
#define LOGFILE     "client_log.txt"

FILE* g_log = NULL;
sockaddr_in g_server;

void log_event(const char* fmt, ...)
{
    if (!g_log) return;
    char tbuf[32];
    time_t t = time(NULL);
    strftime(tbuf, sizeof(tbuf), "%Y-%m-%d %H:%M:%S", localtime(&t));
    fprintf(g_log, "[%s] ", tbuf);

    va_list ap;
    va_start(ap, fmt);
    vfprintf(g_log, fmt, ap);
    va_end(ap);
    fprintf(g_log, "\n");
    fflush(g_log);
}

void set_server(const char* server, int port)
{
    memset(&g_server, 0, sizeof(g_server));
    g_server.sin_family = AF_INET;
    g_server.sin_port = htons((u_short)port);

    if (inet_addr(server) != INADDR_NONE)
        g_server.sin_addr.s_addr = inet_addr(server);
    else {
        HOSTENT* hst = gethostbyname(server);
        if (hst)
            g_server.sin_addr.s_addr = ((unsigned long**)hst->h_addr_list)[0][0];
    }

    printf("Server set to %s:%d\n", server, port);
    log_event("CONNECT to %s:%d", server, port);
}

int main()
{
    char buff[4096];
    printf("UDP CLIENT (variant 8, task 1)\n");
    printf("Type text. Press HOME to send. 'disconnect <addr> <port>' + HOME to exit.\n\n");

    g_log = fopen(LOGFILE, "a");
    if (!g_log) return -1;
    log_event("=== SESSION START ===");

    if (WSAStartup(0x202, (WSADATA*)buff)) {
        printf("WSAStartup error %d\n", WSAGetLastError());
        fclose(g_log); return -1;
    }

    SOCKET my_sock = socket(AF_INET, SOCK_DGRAM, 0);
    if (my_sock == INVALID_SOCKET) {
        printf("Socket() error %d\n", WSAGetLastError());
        WSACleanup(); fclose(g_log); return -1;
    }

    set_server(SERVERADDR, PORT);

    char line[1024] = { 0 };
    int  linelen = 0;
    printf("C<=S: ");

    while (1)
    {
        int ch = _getch();

        if (ch == 0 || ch == 0xE0) {
            if (_getch() != 71) continue;

            if (linelen > 0) {
                line[linelen] = 0;
                printf("\n");
                log_event("SEND: %s", line);

                if (!strncmp(line, "disconnect ", 11)) {
                    log_event("DISCONNECT");
                    goto done;
                }

                if (sendto(my_sock, line, linelen, 0,
                    (sockaddr*)&g_server, sizeof(g_server)) == SOCKET_ERROR)
                {
                    printf("sendto error %d\n", WSAGetLastError());
                    break;
                }

                sockaddr_in from;
                int from_len = sizeof(from);
                int n = recvfrom(my_sock, buff, sizeof(buff) - 1, 0,
                    (sockaddr*)&from, &from_len);

                if (n == SOCKET_ERROR) {
                    printf("[recvfrom error: %d]\n", WSAGetLastError());
                }
                else if (n > 0) {
                    buff[n] = 0;
                    printf("S=>C: %s", buff);
                }
            }
            linelen = 0;
            line[0] = 0;
            printf("C<=S: ");
            continue;
        }

        if (ch == '\r' || ch == '\n') {
            printf("\n(press HOME to send)\nC<=S: %s", line);
            continue;
        }

        if (ch == 8) {
            if (linelen > 0) { line[--linelen] = 0; printf("\b \b"); }
            continue;
        }

        if (ch >= 32 && ch < 127 && linelen < (int)sizeof(line) - 1) {
            line[linelen++] = (char)ch;
            line[linelen] = 0;
            putchar(ch);
        }
    }

done:
    log_event("=== SESSION END ===");
    fclose(g_log);
    closesocket(my_sock);
    WSACleanup();
    return 0;
}
