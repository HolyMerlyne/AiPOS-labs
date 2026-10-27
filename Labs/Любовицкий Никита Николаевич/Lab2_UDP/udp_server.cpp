#define _WINSOCK_DEPRECATED_NO_WARNINGS
#define _CRT_SECURE_NO_WARNINGS

#include <stdio.h>
#include <string.h>
#include <winsock2.h>
#include <windows.h>

#ifdef _MSC_VER
#pragma comment(lib, "ws2_32.lib")
#endif

#define PORT      666
#define BUF_SIZE  8192
#define DATA_SIZE 65536
#define NAME_SIZE 256
#define MARKER    "#end##"

struct SaveSession
{
    int  active;
    char filename[NAME_SIZE];
    char data[DATA_SIZE];
    int  length;
};

static void send_text(SOCKET sock, const sockaddr_in *to, int tolen, const char *text)
{
    sendto(sock, text, (int)strlen(text), 0, (const sockaddr *)to, tolen);
}

static void session_reset(SaveSession *s)
{
    s->active = 0;
    s->length = 0;
    s->filename[0] = 0;
    s->data[0] = 0;
}

static int session_append(SaveSession *s, const char *src, int n)
{
    if (n <= 0)
        return 1;
    if (s->length + n >= DATA_SIZE)
        return 0;
    memcpy(s->data + s->length, src, n);
    s->length += n;
    s->data[s->length] = 0;
    return 1;
}

static const char *after_save_command(const char *buf, char *filename)
{
    if (sscanf(buf, "save %255s", filename) != 1)
        return NULL;

    const char *p = buf;
    while (*p && *p != ' ')
        p++;
    while (*p == ' ' || *p == '\t')
        p++;
    while (*p && *p != ' ' && *p != '\t' && *p != '\n' && *p != '\r')
        p++;
    if (*p == '\r')
        p++;
    if (*p == '\n')
        p++;
    return p;
}

static void finish_save(SOCKET sock, const sockaddr_in *from, int fromlen, SaveSession *s)
{
    char *end = strstr(s->data, MARKER);
    char msg[300];

    if (!end)
        return;

    *end = 0;

    FILE *f = fopen(s->filename, "w");
    if (!f)
    {
        sprintf(msg, "ERROR: cannot create file %s\n", s->filename);
        send_text(sock, from, fromlen, msg);
        printf("%s", msg);
        session_reset(s);
        return;
    }

    fputs(s->data, f);
    fclose(f);

    sprintf(msg, "OK: file %s created\n", s->filename);
    send_text(sock, from, fromlen, msg);
        printf("ok: '%s'\n", s->filename);
    session_reset(s);
}

static void print_help()
{
    printf("UDP-сервер, порт %d\n", PORT);
    printf("  save <файл>  ->  текст  ->  #end##\n");
}

int main()
{
    char buf[BUF_SIZE];
    WSADATA wsa;
    SaveSession session;

    SetConsoleOutputCP(CP_UTF8);
    SetConsoleCP(CP_UTF8);
    setvbuf(stdout, NULL, _IONBF, 0);

    session_reset(&session);
    print_help();

    if (WSAStartup(0x0202, &wsa))
    {
        printf("WSAStartup error: %d\n", WSAGetLastError());
        return -1;
    }

    SOCKET sock = socket(AF_INET, SOCK_DGRAM, 0);
    if (sock == INVALID_SOCKET)
    {
        printf("socket error: %d\n", WSAGetLastError());
        WSACleanup();
        return -1;
    }

    sockaddr_in addr;
    memset(&addr, 0, sizeof(addr));
    addr.sin_family = AF_INET;
    addr.sin_port = htons(PORT);
    addr.sin_addr.s_addr = INADDR_ANY;

    if (bind(sock, (sockaddr *)&addr, sizeof(addr)))
    {
        printf("bind error: %d (порт %d занят?)\n", WSAGetLastError(), PORT);
        closesocket(sock);
        WSACleanup();
        return -1;
    }

    while (1)
    {
        sockaddr_in from;
        int fromlen = sizeof(from);
        int n = recvfrom(sock, buf, BUF_SIZE - 1, 0, (sockaddr *)&from, &fromlen);
        if (n <= 0)
            continue;

        buf[n] = 0;
        printf("C=>S [%s:%d] %s\n", inet_ntoa(from.sin_addr), ntohs(from.sin_port), buf);

        if (!session.active)
        {
            const char *rest = after_save_command(buf, session.filename);
            if (!rest)
            {
                printf("нужно: save <файл>\n");
                send_text(sock, &from, fromlen, "ERROR: expected save <filename>\n");
                continue;
            }

            FILE *probe = fopen(session.filename, "w");
            if (!probe)
            {
                char msg[300];
                sprintf(msg, "ERROR: cannot create file %s\n", session.filename);
                send_text(sock, &from, fromlen, msg);
                printf("ошибка: не создать '%s'\n", session.filename);
                session_reset(&session);
                continue;
            }
            fclose(probe);

            session.active = 1;
            session.length = 0;
            printf("приём '%s' до #end##\n", session.filename);

            if (!session_append(&session, rest, n - (int)(rest - buf)))
            {
                send_text(sock, &from, fromlen, "ERROR: data too large\n");
                session_reset(&session);
                continue;
            }
        }
        else if (!session_append(&session, buf, n))
        {
            send_text(sock, &from, fromlen, "ERROR: data too large\n");
            printf("ошибка: файл слишком большой\n");
            session_reset(&session);
            continue;
        }

        finish_save(sock, &from, fromlen, &session);
    }
}
