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

SOCKET connect_to(const char* server, int port)
{
    SOCKET s = socket(AF_INET, SOCK_STREAM, 0);
    if (s == INVALID_SOCKET) return INVALID_SOCKET;

    sockaddr_in a;
    a.sin_family = AF_INET;
    a.sin_port = htons((u_short)port);
    a.sin_addr.s_addr = (inet_addr(server) != INADDR_NONE)
        ? inet_addr(server)
        : ((unsigned long**)gethostbyname(server)->h_addr_list)[0][0];

    if (connect(s, (sockaddr*)&a, sizeof(a))) {
        closesocket(s);
        return INVALID_SOCKET;
    }
    printf("Connected to %s:%d\n", server, port);
    log_event("CONNECT to %s:%d", server, port);
    return s;
}

void disconnect_sock(SOCKET* s)
{
    if (*s != INVALID_SOCKET) {
        log_event("DISCONNECT");
        closesocket(*s);
        *s = INVALID_SOCKET;
        printf("Disconnected.\n");
    }
}

int main()
{
    char buff[1024];
    printf("TCP CLIENT (variant 8, task 1)\n");
    printf("Type text. Press HOME to send. 'disconnect <addr> <port>' + HOME to exit.\n\n");

    g_log = fopen(LOGFILE, "a");
    if (!g_log) return -1;
    log_event("=== SESSION START ===");

    if (WSAStartup(0x202, (WSADATA*)buff)) {
        printf("WSAStartup error %d\n", WSAGetLastError());
        fclose(g_log); return -1;
    }

    SOCKET my_sock = connect_to(SERVERADDR, PORT);
    if (my_sock == INVALID_SOCKET) {
        WSACleanup(); fclose(g_log); return -1;
    }

    int nsize = recv(my_sock, buff, sizeof(buff) - 1, 0);
    if (nsize > 0) { buff[nsize] = 0; printf("S=>C: %s", buff); }

    char line[1024] = { 0 };
    int  linelen = 0;
    printf("C<=S: ");

    while (1)
    {
        int ch = _getch();

        // HOME = отправка
        if (ch == 0 || ch == 0xE0) {
            if (_getch() != 71) continue;       // 71 = Home

            if (linelen > 0) {
                line[linelen] = 0;
                printf("\n");
                log_event("SEND: %s", line);

                if (!strncmp(line, "disconnect ", 11)) {
                    disconnect_sock(&my_sock);
                    goto done;
                }

                if (send(my_sock, line, linelen, 0) == SOCKET_ERROR) break;

                nsize = recv(my_sock, buff, sizeof(buff) - 1, 0);
                if (nsize > 0) { buff[nsize] = 0; printf("S=>C: %s", buff); }
                else if (nsize == 0) { printf("[Server closed]\n"); break; }
            }
            linelen = 0; line[0] = 0;
            printf("C<=S: ");
            continue;
        }

        // Enter — НЕ отправляет
        if (ch == '\r' || ch == '\n') {
            printf("\n(press HOME to send)\nC<=S: %s", line);
            continue;
        }

        // Backspace
        if (ch == 8) {
            if (linelen > 0) { line[--linelen] = 0; printf("\b \b"); }
            continue;
        }

        // обычный символ
        if (ch >= 32 && ch < 127 && linelen < (int)sizeof(line) - 1) {
            line[linelen++] = (char)ch;
            line[linelen] = 0;
            putchar(ch);
        }
    }

done:
    if (my_sock != INVALID_SOCKET) disconnect_sock(&my_sock);
    log_event("=== SESSION END ===");
    fclose(g_log);
    WSACleanup();
    return 0;
}
