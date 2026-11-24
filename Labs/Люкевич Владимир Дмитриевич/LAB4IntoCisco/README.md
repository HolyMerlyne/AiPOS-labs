## Часть 1. Локальная сеть

### 1. Топология
Создана топология из 4 коммутаторов (Switch 2960) и 7 рабочих станций (PC0, PC1, PC2, PC3, PC4, PC5, PC6). Кабели: *Copper Straight-Through* (ПК–Коммутатор) и *Copper Cross-Over* (Коммутатор–Коммутатор).


### 2. ipconfig
```
C:\>ipconfig

FastEthernet0 Connection:(default port)

   Connection-specific DNS Suffix..: 
   Link-local IPv6 Address.........: FE80::260:3EFF:FE7E:62C5
   IPv6 Address....................: ::
   IPv4 Address....................: 4.1.1.1
   Subnet Mask.....................: 255.255.255.0
   Default Gateway.................: ::
                                     0.0.0.0

Bluetooth Connection:

   Connection-specific DNS Suffix..: 
   Link-local IPv6 Address.........: ::
   IPv6 Address....................: ::
   IPv4 Address....................: 0.0.0.0
   Subnet Mask.....................: 0.0.0.0
   Default Gateway.................: ::
                                     0.0.0.0
```

### 3. Вывод консоли: проверка связности (ping)
```
C:\>ping 4.1.1.3

Pinging 4.1.1.3 with 32 bytes of data:

Reply from 4.1.1.3: bytes=32 time<1ms TTL=128
Reply from 4.1.1.3: bytes=32 time<1ms TTL=128
Reply from 4.1.1.3: bytes=32 time<1ms TTL=128
Reply from 4.1.1.3: bytes=32 time<1ms TTL=128

Ping statistics for 4.1.1.3:
    Packets: Sent = 4, Received = 4, Lost = 0 (0% loss),
Approximate round trip times in milli-seconds:
    Minimum = 0ms, Maximum = 0ms, Average = 0ms

C:\>ping 4.1.1.4

Pinging 4.1.1.4 with 32 bytes of data:

Reply from 4.1.1.4: bytes=32 time<1ms TTL=128
Reply from 4.1.1.4: bytes=32 time<1ms TTL=128
Reply from 4.1.1.4: bytes=32 time<1ms TTL=128
Reply from 4.1.1.4: bytes=32 time<1ms TTL=128

Ping statistics for 4.1.1.4:
    Packets: Sent = 4, Received = 4, Lost = 0 (0% loss),
Approximate round trip times in milli-seconds:
    Minimum = 0ms, Maximum = 0ms, Average = 0ms

C:\>ping 4.1.1.7

Pinging 4.1.1.7 with 32 bytes of data:

Reply from 4.1.1.7: bytes=32 time<1ms TTL=128
Reply from 4.1.1.7: bytes=32 time<1ms TTL=128
Reply from 4.1.1.7: bytes=32 time=1ms TTL=128
Reply from 4.1.1.7: bytes=32 time=1ms TTL=128

Ping statistics for 4.1.1.7:
    Packets: Sent = 4, Received = 4, Lost = 0 (0% loss),
Approximate round trip times in milli-seconds:
    Minimum = 0ms, Maximum = 1ms, Average = 0ms
```

### 4. Разъяснение
* **ipconfig**: показывает настройки сетевого интерфейса (IP-адрес, маску подсети, шлюз по умолчанию). Шлюз `0.0.0.0`, так как все узлы находятся в пределах одной локальной сети и маршрутизация наружу не требуется.
* **ping**: использует протокол ICMP (Echo Request/Echo Reply). 0% потерь пакетов и минимальное время задержки подтверждают корректность коммутации на уровне L2 и IP-адресации на уровне L3 между всеми сегментами локальной сети.

<br>

## Часть 2. Конфигурация маршрутизаторов

### 1. Топология
3 маршрутизатора (модель Cisco 2621XM). R1 и R2 соединены через интерфейсы FastEthernet (кабель Copper Cross-Over). R2 и R3 соединены через интерфейсы Serial (кабель Serial DTE/DCE, синхронизация DCE задана на R2).


### 2. Таблица сетевых адресов
| Устройство | Интерфейс | IP-адрес | Маска подсети | Роль |
| :--- | :--- | :--- | :--- | :--- |
| **R1** | FastEthernet 0/0 | 172.16.0.1 | 255.255.255.252 | DTE |
| **R2** | FastEthernet 0/0 | 172.16.0.2 | 255.255.255.252 | DTE |
| **R2** | Serial 0/0 | 172.16.0.5 | 255.255.255.252 | DCE |
| **R3** | Serial 0/0 | 172.16.0.6 | 255.255.255.252 | DTE |

### 3. Ход конфигурирования
```
! Настройка R1
enable
configure terminal
hostname R1
interface FastEthernet0/0
ip address 172.16.0.1 255.255.255.252
no shutdown
exit
ip route 172.16.0.4 255.255.255.252 172.16.0.2
exit
write

! Настройка R2 (DCE)
enable
configure terminal
hostname R2
interface FastEthernet0/0
ip address 172.16.0.2 255.255.255.252
no shutdown
exit
interface Serial0/0
ip address 172.16.0.5 255.255.255.252
clock rate 64000
no shutdown
exit
exit
write

! Настройка R3
enable
configure terminal
hostname R3
interface Serial0/0
ip address 172.16.0.6 255.255.255.252
no shutdown
exit
ip route 172.16.0.0 255.255.255.252 172.16.0.5
exit
write
```

### 4. Вывод консоли: статус интерфейсов и тестирование

Команда `show ip interface brief`:

| Устройство | Интерфейс | IP-адрес | OK? | Метод | Статус | Протокол |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **R1** | FastEthernet0/0 | 172.16.0.1 | YES | manual | **up** | **up** |
| **R1** | FastEthernet0/1 | unassigned | YES | unset | administratively down | down |
| **R2** | FastEthernet0/0 | 172.16.0.2 | YES | manual | **up** | **up** |
| **R2** | FastEthernet0/1 | unassigned | YES | unset | administratively down | down |
| **R2** | Serial0/0 | 172.16.0.5 | YES | manual | **up** | **up** |
| **R3** | FastEthernet0/0 | unassigned | YES | unset | administratively down | down |
| **R3** | FastEthernet0/1 | unassigned | YES | unset | administratively down | down |
| **R3** | Serial0/0 | 172.16.0.6 | YES | manual | **up** | **up** |

Статус `up/up` для FastEthernet0/0 означает, что интерфейс физически включен и канальный протокол активен. Интерфейс FastEthernet0/1 на R1 не задействован в текущей топологии, поэтому находится в состоянии `administratively down`.

```
R1#ping 172.16.0.6

Type escape sequence to abort.
Sending 5, 100-byte ICMP Echos to 172.16.0.6, timeout is 2 seconds:
.!!!!
Success rate is 80 percent (4/5), round-trip min/avg/max = 4/7/10 ms
```

Первый пакет (`.`) потерян из-за разрешения адреса (ARP-запрос), последующие 4 пакета (`!!!!`) успешно доставлены. Маршрутизация между подсетями функционирует корректно.