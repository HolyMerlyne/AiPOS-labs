#define _WINSOCK_DEPRECATED_NO_WARNINGS
#define _CRT_SECURE_NO_WARNINGS

#include <stdio.h>
#include <string.h>
#include <time.h>
#include <direct.h>
#include <conio.h>
#include <winsock2.h>
#include <windows.h>

#ifdef _MSC_VER
#pragma comment(lib, "ws2_32.lib")
#endif

#define PORT_DEFAULT 666
#define BUF_SIZE     8192
#define LINE_SIZE    4096
#define KEY_PGUP     73
#define LOG_NAME     "protocol.log"

struct Client
{
    SOCKET      sock;
    sockaddr_in server;
    char        host[256];
    int         port;
    int         connected;
    FILE       *log;
    char        line[LINE_SIZE];
    int         len;
};

static void timestamp(char *buf, int size)
{
    time_t now = time(NULL);
    struct tm *t = localtime(&now);
    if (t)
        strftime(buf, size, "%Y-%m-%d %H:%M:%S", t);
    else
        strncpy(buf, "unknown-time", size);
}

static void log_event(Client *c, const char *dir, const char *text)
{
    char ts[32];
    timestamp(ts, sizeof(ts));

    printf("[%s] %s: %s", ts, dir, text);
    if (!text[0] || text[strlen(text) - 1] != '\n')
        putchar('\n');

    if (!c->log)
        return;
    fprintf(c->log, "[%s] %s: %s", ts, dir, text);
    if (!text[0] || text[strlen(text) - 1] != '\n')
        fputc('\n', c->log);
    fflush(c->log);
}

static void print_prompt(const Client *c)
{
    if (c->connected)
        printf("[%s:%d] > ", c->host, c->port);
    else
        printf("[нет связи] > ");
}

static void print_help()
{
    printf("UDP-клиент\n");
    printf("  connect 127.0.0.1 666\n");
    printf("  save <файл>  ->  текст  ->  #end##\n");
    printf("  PgUp — отправка  |  Enter — новая строка  |  quit\n");
    printf("  лог: %s\n", LOG_NAME);
}

static int resolve_addr(const char *host, int port, sockaddr_in *out)
{
    memset(out, 0, sizeof(*out));
    out->sin_family = AF_INET;
    out->sin_port = htons((u_short)port);
    out->sin_addr.s_addr = inet_addr(host);

    if (out->sin_addr.s_addr != INADDR_NONE)
        return 1;

    HOSTENT *h = gethostbyname(host);
    if (!h)
        return 0;

    out->sin_addr.s_addr = *(unsigned long *)h->h_addr;
    return 1;
}

static void trim_newline(char *s)
{
    int n = (int)strlen(s);
    while (n > 0 && (s[n - 1] == '\n' || s[n - 1] == '\r'))
    {
        s[n - 1] = 0;
        n--;
    }
}

static int is_cmd(const char *line, const char *cmd)
{
    return _stricmp(line, cmd) == 0;
}

static void recv_from_server(Client *c)
{
    fd_set fds;
    timeval tv;
    char buf[BUF_SIZE];
    sockaddr_in from;
    int fromlen = sizeof(from);

    FD_ZERO(&fds);
    FD_SET(c->sock, &fds);
    tv.tv_sec = 0;
    tv.tv_usec = 50000;

    if (select(0, &fds, NULL, NULL, &tv) <= 0)
        return;

    int n = recvfrom(c->sock, buf, BUF_SIZE - 1, 0, (sockaddr *)&from, &fromlen);
    if (n <= 0)
        return;

    buf[n] = 0;
    putchar('\n');
    log_event(c, "RX", buf);
    print_prompt(c);
}

static void send_to_server(Client *c, const char *text)
{
    char packet[LINE_SIZE];
    strncpy(packet, text, LINE_SIZE - 2);
    packet[LINE_SIZE - 2] = 0;

    int n = (int)strlen(packet);
    if (n == 0)
        return;
    if (packet[n - 1] != '\n')
    {
        packet[n++] = '\n';
        packet[n] = 0;
    }

    if (sendto(c->sock, packet, n, 0,
               (const sockaddr *)&c->server, sizeof(c->server)) == SOCKET_ERROR)
    {
        printf("sendto error: %d\n", WSAGetLastError());
        return;
    }

    log_event(c, "TX", packet);
}

static int do_connect(Client *c, const char *line)
{
    char cmd[16];
    char host[256];
    int port = 0;

    if (sscanf(line, "%15s %255s %d", cmd, host, &port) != 3)
        return 0;
    if (_stricmp(cmd, "connect") != 0)
        return 0;

    if (port <= 0 || port > 65535)
    {
        printf("порт: 1..65535\n");
        return 1;
    }
    if (!resolve_addr(host, port, &c->server))
    {
        printf("хост неизвестен: %s\n", host);
        return 1;
    }

    strncpy(c->host, host, sizeof(c->host) - 1);
    c->host[sizeof(c->host) - 1] = 0;
    c->port = port;
    c->connected = 1;

    printf("ok %s:%d | дальше: save <файл> -> текст -> #end##\n", host, port);
    {
        char info[300];
        sprintf(info, "connect %s %d", host, port);
        log_event(c, "CONNECT", info);
    }
    return 1;
}

static int submit_line(Client *c)
{
    char cmd[LINE_SIZE];
    strncpy(cmd, c->line, LINE_SIZE - 1);
    cmd[LINE_SIZE - 1] = 0;
    trim_newline(cmd);

    if (cmd[0] == 0)
    {
        printf("пусто. connect 127.0.0.1 666\n");
        return 1;
    }
    if (is_cmd(cmd, "quit"))
        return 0;
    if (is_cmd(cmd, "help"))
    {
        print_help();
        return 1;
    }
    if (do_connect(c, cmd))
        return 1;

    if (!c->connected)
    {
        printf("нет связи. connect 127.0.0.1 666\n");
        return 1;
    }

    send_to_server(c, cmd);
    return 1;
}

int main()
{
    Client c;
    char cwd[260];
    WSADATA wsa;

    memset(&c, 0, sizeof(c));
    c.sock = INVALID_SOCKET;

    SetConsoleOutputCP(CP_UTF8);
    SetConsoleCP(CP_UTF8);
    setvbuf(stdout, NULL, _IONBF, 0);

    print_help();
    if (_getcwd(cwd, sizeof(cwd)))
        printf("  %s\\%s\n", cwd, LOG_NAME);

    if (WSAStartup(0x0202, &wsa))
    {
        printf("WSAStartup error: %d\n", WSAGetLastError());
        return -1;
    }

    c.sock = socket(AF_INET, SOCK_DGRAM, 0);
    if (c.sock == INVALID_SOCKET)
    {
        printf("socket error: %d\n", WSAGetLastError());
        WSACleanup();
        return -1;
    }

    c.log = fopen(LOG_NAME, "a");
    if (!c.log)
    {
        printf("лог %s недоступен, только экран\n", LOG_NAME);
    }
    else
    {
        char ts[32];
        timestamp(ts, sizeof(ts));
        fprintf(c.log, "\n========== session %s ==========\n", ts);
        fflush(c.log);
        printf("лог TX/RX включён\n");
    }

    print_prompt(&c);

    while (1)
    {
        recv_from_server(&c);

        if (!_kbhit())
            continue;

        int ch = _getch();
        if (ch == 0 || ch == 0xE0)
        {
            int key = _getch();
            if (key == KEY_PGUP)
            {
                c.line[c.len] = 0;
                putchar('\n');
                if (!submit_line(&c))
                    break;
                c.len = 0;
                print_prompt(&c);
            }
            continue;
        }

        if ((ch == 8 || ch == 127) && c.len > 0)
        {
            c.len--;
            printf("\b \b");
            continue;
        }

        if ((ch == '\r' || ch == '\n') && c.len < LINE_SIZE - 2)
        {
            c.line[c.len++] = '\n';
            putchar('\n');
            continue;
        }

        if (ch >= 32 && c.len < LINE_SIZE - 2)
        {
            c.line[c.len++] = (char)ch;
            putchar(ch);
        }
    }

    printf("Выход.\n");
    if (c.log)
        fclose(c.log);
    closesocket(c.sock);
    WSACleanup();
    return 0;
}
