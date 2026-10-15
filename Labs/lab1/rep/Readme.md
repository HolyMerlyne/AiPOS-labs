# Лабораторная работа №1

**Дисциплина:** Аппаратное и программное обеспечение сетей
**Тема:** Организация TCP – сервера/клиента
**Вариант:** 6

**Выполнил:** студент 3 курса, группы ИИ-28, Malich
**Проверила:** Прохорова С.С.

**Брест, 2026**

---

## Цель работы

Изучить основы программирования сетевых приложений на базе библиотеки `java.net`.
Приобрести навыки практического использования библиотеки для реализации сетевых приложений на базе протоколов TCP и UDP.

## Задание на выполнение

1. Изучить теоретический материал, классы и методы пакета `java.net`, а также листинг программы реализации TCP-сервера.
2. Разработать программу работы TCP-сервера, выполняющую функции согласно варианту задания.
3. Выполнить проверку программы с использованием TCP-клиента.
4. Представить отчёт, содержащий титульный лист, листинг программы с подробными комментариями основных фрагментов.
5. Подготовиться к защите лабораторной работы по теоретическому материалу, функциям и полученным результатам.

## Индивидуальное задание (вариант 6)

### Задание для сервера (№4)

После приёма каждой группы из **64 символов** от клиента сервер формирует и отсылает клиенту строку вида:

<символ1-количество1> <символ2-количество2> ...


где `<символ>` — встреченный в группе символ, `<количество>` — количество его вхождений в группу.

Если в очередной группе число **различных символов меньше 3**, сервер отсылает клиенту соответствующее сообщение и **разрывает соединение**.

### Задание для клиента (№2)

- Ввод символов с отсылкой введённой строки серверу (в методичке — по нажатию **PgUp**; в консоли Java реализовано по нажатию **Enter**, см. примечание).
- Ведение файла протокола событий, включающего:
  1. время начала и окончания соединения;
  2. принимаемую от сервера строку и время её приёма.
- Команда подключения к серверу: `connect <адрес> <порт>`.
- Разрыв соединения по команде в клиенте **не предусмотрен**.

> **Примечание.** В консольном приложении Java перехватить нажатие клавиши PgUp без подключения сторонних библиотек (например, JLine) невозможно. В данной реализации отправка строки серверу осуществляется по нажатию клавиши Enter. Семантика отправки сохраняется: строка, введённая пользователем, целиком передаётся серверу.

## Краткие теоретические сведения

**Сокет** — высокоуровневый унифицированный интерфейс взаимодействия с телекоммуникационными протоколами. В Java сокеты представлены классами пакета `java.net`.

Основные классы:

| Класс | Назначение |
|---|---|
| `InetAddress` | IP-адрес узла |
| `ServerSocket` | Серверный сокет (TCP) для приёма входящих соединений |
| `Socket` | Клиентский сокет (TCP) для установки соединения |
| `DatagramSocket` / `DatagramPacket` | UDP |

Потоковые (TCP) сокеты работают с установкой соединения и гарантируют доставку данных. После установки соединения обмен данными идёт через потоки `InputStream` и `OutputStream`, которые оборачиваются в `BufferedReader` и `PrintWriter` для удобной построчной передачи.

Многопоточность (`Thread`) позволяет серверу обслуживать несколько клиентов одновременно: на каждого подключившегося клиента создаётся отдельный поток.

## Структура проекта

eports/malich/lab1/
├── rep/
│ └── README.md ← отчёт
└── src/
├── TcpServer.java ← сервер
└── TcpClient.java ← клиент


## Листинг серверной части (`TcpServer.java`)

```java
import java.io.*;
import java.net.*;
import java.util.*;

/**
 * TCP-сервер, вариант 6.
 * Принимает группы по 64 символа, присылает клиенту статистику:
 * <символ-количество> для каждого символа в группе.
 * Если уникальных символов < 3 — разрыв соединения.
 */
public class TcpServer {
    public static final int PORT = 6666;

    public static void main(String[] args) throws IOException {
        ServerSocket serverSocket = new ServerSocket(PORT);
        System.out.println("TCP Server запущен на порту " + PORT);
        System.out.println("Ожидание подключения...");

        while (true) {
            Socket client = serverSocket.accept();
            System.out.println("Клиент подключён: " + client.getRemoteSocketAddress());
            new Thread(new ClientHandler(client)).start();
        }
    }
}

class ClientHandler implements Runnable {
    private final Socket socket;

    ClientHandler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        try (BufferedReader in = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), "UTF-8"));
             PrintWriter out = new PrintWriter(
                    new BufferedWriter(
                            new OutputStreamWriter(socket.getOutputStream(), "UTF-8")), true)) {

            out.println("Hello, Student!");

            char[] buffer = new char[64];
            int read;

            while ((read = readFully(in, buffer)) > 0) {
                String group = new String(buffer, 0, read);

                Map<Character, Integer> counts = new LinkedHashMap<>();
                for (char c : group.toCharArray()) {
                    counts.merge(c, 1, Integer::sum);
                }

                if (counts.size() < 3) {
                    out.println("ERROR: в группе меньше 3 различных символов. Соединение закрывается.");
                    System.out.println("Разрыв: <3 уникальных символов");
                    break;
                }

                StringBuilder sb = new StringBuilder();
                for (Map.Entry<Character, Integer> e : counts.entrySet()) {
                    char c = e.getKey();
                    String sym = (c == '\n') ? "\\n"
                               : (c == '\r') ? "\\r"
                               : (c == '\t') ? "\\t"
                               : String.valueOf(c);
                    sb.append("<").append(sym).append("-").append(e.getValue()).append("> ");
                }

                System.out.println("Отправлено: " + sb);
                out.println(sb.toString());
            }

        } catch (IOException e) {
            System.err.println("Ошибка при работе с клиентом: " + e.getMessage());
        } finally {
            try { socket.close(); } catch (IOException ignored) {}
            System.out.println("Клиент отключён.");
        }
    }

    private static int readFully(Reader in, char[] buffer) throws IOException {
        int total = 0;
        while (total < buffer.length) {
            int r = in.read(buffer, total, buffer.length - total);
            if (r == -1) break;
            total += r;
        }
        return total;
    }
}

Листинг клиентской части (TcpClient.java)

import java.io.*;
import java.net.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Scanner;

public class TcpClient {

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static PrintWriter logFile;

    public static void main(String[] args) throws IOException {
        Scanner console = new Scanner(System.in);
        Socket socket = null;
        BufferedReader in = null;
        PrintWriter out = null;

        new File("logs").mkdirs();
        logFile = new PrintWriter(new FileWriter("logs/client.log", true), true);
        log("=== Запуск клиента ===");

        System.out.println("TCP Client (вариант 6)");
        System.out.println("Введите: connect <адрес> <порт>");
        System.out.println("Для отправки строки серверу введите её и нажмите Enter.");
        System.out.println("Для выхода введите: exit");

        while (true) {
            System.out.print("> ");
            if (!console.hasNextLine()) break;
            String line = console.nextLine().trim();

            if (line.isEmpty()) continue;

            if (line.equals("exit")) {
                log("=== Завершение клиента ===");
                break;
            }

            if (line.startsWith("connect ")) {
                String[] parts = line.split("\\s+");
                if (parts.length != 3) {
                    System.out.println("Использование: connect <адрес> <порт>");
                    continue;
                }
                String host = parts[1];
                int port;
                try {
                    port = Integer.parseInt(parts[2]);
                } catch (NumberFormatException e) {
                    System.out.println("Порт должен быть числом.");
                    continue;
                }

                try {
                    socket = new Socket(host, port);
                    in = new BufferedReader(
                            new InputStreamReader(socket.getInputStream(), "UTF-8"));
                    out = new PrintWriter(
                            new BufferedWriter(
                                    new OutputStreamWriter(socket.getOutputStream(), "UTF-8")), true);

                    String startTime = LocalDateTime.now().format(FMT);
                    System.out.println("Соединение установлено с " + host + ":" + port);
                    System.out.println("Время начала соединения: " + startTime);
                    log("Начало соединения: " + host + ":" + port + " в " + startTime);

                    String greeting = in.readLine();
                    if (greeting != null) {
                        System.out.println("Сервер: " + greeting);
                        log("Принято: " + greeting + " в " + LocalDateTime.now().format(FMT));
                    }
                } catch (IOException e) {
                    System.out.println("Не удалось подключиться: " + e.getMessage());
                    log("Ошибка подключения: " + e.getMessage());
                }
                continue;
            }

            if (socket == null || socket.isClosed()) {
                System.out.println("Сначала подключитесь командой connect <адрес> <порт>");
                continue;
            }

            try {
                out.println(line);
                System.out.println("Отправлено: " + line);

                String response = in.readLine();
                if (response != null) {
                    String time = LocalDateTime.now().format(FMT);
                    System.out.println("Сервер: " + response);
                    log("Принято: " + response + " в " + time);

                    if (response.startsWith("ERROR")) {
                        System.out.println("Сервер разорвал соединение.");
                        log("Разрыв соединения по инициативе сервера в " + time);
                        socket.close();
                        socket = null;
                    }
                } else {
                    System.out.println("Сервер закрыл соединение.");
                    log("Сервер закрыл соединение в " + LocalDateTime.now().format(FMT));
                    socket.close();
                    socket = null;
                }
            } catch (IOException e) {
                System.out.println("Ошибка при обмене: " + e.getMessage());
                log("Ошибка обмена: " + e.getMessage());
                try { socket.close(); } catch (IOException ignored) {}
                socket = null;
            }
        }

        if (socket != null && !socket.isClosed()) {
            log("Конец соединения в " + LocalDateTime.now().format(FMT));
            socket.close();
        }
        logFile.close();
        console.close();
    }

    private static void log(String msg) {
        String time = LocalDateTime.now().format(FMT);
        logFile.println("[" + time + "] " + msg);
    }
}

Сервер
Открывает ServerSocket на порту 6666.

В цикле accept() принимает входящие соединения. Для каждого запускает отдельный поток ClientHandler (многопоточная обработка).

Поток читает от клиента данные ровно по 64 символа (readFully).

Для каждой группы подсчитывает вхождения каждого символа (LinkedHashMap).

Если уникальных символов меньше 3 — отправляет ERROR и закрывает соединение.

Иначе формирует строку <символ-количество> ... и отсылает клиенту.

Клиент
Открывает лог-файл logs/client.log (append-режим).

В цикле читает строки из консоли.

Команда connect <адрес> <порт> устанавливает TCP-соединение.

Любая другая строка отправляется серверу; в лог пишется ОТПРАВЛЕНО: <строка>.

При получении ответа от сервера пишет в лог ПОЛУЧЕНО: <строка> с меткой времени.

При получении ERROR или разрыве соединения — корректно закрывает сокет.

Сервер:
PS C:\Users\anton\aipos\AIPOS_II28\reports\malich\lab1\src> java -D"file.encoding=UTF-8" TcpServer
TCP Server ??????? ?? ????? 6666
???????? ???????????...
?????? ?????????: /127.0.0.1:65450
??????????: <H-1> <e-4> <l-4> <o-3> < -11> <w-2> <r-4> <d-2> <t-6> <h-3> <i-5> <s-5> <a-3> <n-4> <g-2> <m-1> <y-1> <f-2> <c-1> 
??????????: < -1> <1-1> <2-1> <3-1> <4-1> <5-1> <\r-1> <\n-1> <a-56> 
?????? ??? ?????? ? ????????: Connection reset
?????? ????????.

Клиент:
PS C:\Users\anton\aipos\AIPOS_II28\reports\malich\lab1\src> java TcpClient
TCP Client (??????? 6)
???????: connect <?????> <????>
??? ???????? ?????? ??????? ??????? ?? ? ??????? Enter.
??? ?????? ???????: exit
> connect 127.0.0.1 6666
?????????? ??????????? ? 127.0.0.1:6666
????? ?????? ??????????: 2026-10-07 00:54:45
??????: Hello, Student!