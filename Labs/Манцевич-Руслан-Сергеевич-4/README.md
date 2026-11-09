## Часть 1. Локальная сеть

### 1. Топология
Создана топология из 4 коммутаторов (Switch 2960) и 6 рабочих станций (PC0, PC1, PC2, PC3, PC4, PC6). Кабели: *Copper Straight-Through* (ПК–Коммутатор) и *Copper Cross-Over* (Коммутатор–Коммутатор). Сеть: `7.1.1.0/24`.

![1](<d7b00577-1d6e-4c48-92e6-f7373ee75cff.png>)
> *Рисунок 1. Топология локальной сети.*

### 2. Вывод консоли: проверка конфигурации (ipconfig)
```
C:\>ipconfig

FastEthernet0 Connection:(default port)
   Connection-specific DNS Suffix..: 
   Link-local IPv6 Address.........: FE80::202:16FF:FE08:DEDE
   IPv6 Address....................: ::
   IPv4 Address....................: 7.1.1.1
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
C:\>ping 7.1.1.2

Pinging 7.1.1.2 with 32 bytes of data:

Reply from 7.1.1.2: bytes=32 time<1ms TTL=128
Reply from 7.1.1.2: bytes=5ms TTL=128
Reply from 7.1.1.2: bytes=32 time<1ms TTL=128
Reply from 7.1.1.2: bytes=32 time<1ms TTL=128

Ping statistics for 7.1.1.2:
    Packets: Sent = 4, Received = 4, Lost = 0 (0% loss),
Approximate round trip times in milli-seconds:
    Minimum = 0ms, Maximum = 5ms, Average = 1ms

C:\>ping 7.1.1.5

Pinging 7.1.1.5 with 32 bytes of data:

Reply from 7.1.1.5: bytes=32 time<1ms TTL=128
Reply from 7.1.1.5: bytes=1ms TTL=128
Reply from 7.1.1.5: bytes=32 time<1ms TTL=128
Reply from 7.1.1.5: bytes=5ms TTL=128

Ping statistics for 7.1.1.5:
    Packets: Sent = 4, Received = 4, Lost = 0 (0% loss),
Approximate round trip times in milli-seconds:
    Minimum = 0ms, Maximum = 5ms, Average = 1ms

C:\>ping 7.1.1.7

Pinging 7.1.1.7 with 32 bytes of data:

Reply from 7.1.1.7: bytes=32 time<1ms TTL=128
Reply from 7.1.1.7: bytes=1ms TTL=128
Reply from 7.1.1.7: bytes=32 time<1ms TTL=128
Reply from 7.1.1.7: bytes=32 time<1ms TTL=128

Ping statistics for 7.1.1.7:
    Packets: Sent = 4, Received = 4, Lost = 0 (0% loss),
Approximate round trip times in milli-seconds:
    Minimum = 0ms, Maximum = 0ms, Average = 0ms
```

### 4. Разъяснение
* **ipconfig**: показывает настройки сетевого интерфейса (IP-адрес, маску подсети, шлюз по умолчанию). Шлюз `0.0.0.0`, так как все узлы находятся в пределах одной локальной сети и маршрутизация наружу не требуется.
* **ping**: использует протокол ICMP (Echo Request/Echo Reply). 0% потерь пакетов и минимальное время задержки подтверждают корректность коммутации на уровне L2 и IP-адресации на уровне L3 между всеми сегментами локальной сети.

---

## Часть 2. Конфигурация маршрутизаторов

### 1. Топология
3 маршрутизатора (модель Cisco 2621XM). R1 и R2 соединены через интерфейсы FastEthernet (кабель Copper Cross-Over). R2 и R3 соединены через интерфейсы Serial (кабель Serial DTE/DCE, синхронизация DCE задана на R2).

![2](<f133c0c4-1e50-4e1d-aa6c-ea4174dfcbe0.png>)
> *Рисунок 2. Топология сети с маршрутизаторами.*

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
Router>enable
Router#conf t
Router(config)#hostname R1
R1(config)#interface FastEthernet0/0
R1(config-if)#ip address 172.16.0.1 255.255.255.252
R1(config-if)#no shutdown
R1(config-if)#exit
R1(config)#ip route 172.16.0.4 255.255.255.252 172.16.0.2
R1(config)#exit
R1#write

! Настройка R2 (DCE)
Router>enable
Router#conf t
Router(config)#hostname R2
R2(config)#interface FastEthernet0/0
R2(config-if)#ip address 172.16.0.2 255.255.255.252
R2(config-if)#no shutdown
R2(config-if)#exit
R2(config)#interface Serial0/0
R2(config-if)#ip address 172.16.0.5 255.255.255.252
R2(config-if)#clock rate 64000
R2(config-if)#no shutdown
R2(config-if)#exit
R2(config)#exit
R2#write

! Настройка R3
Router>enable
Router#conf t
Router(config)#hostname R3
R3(config)#interface Serial0/0
R3(config-if)#ip address 172.16.0.6 255.255.255.252
R3(config-if)#no shutdown
R3(config-if)#exit
R3(config)#ip route 172.16.0.0 255.255.255.252 172.16.0.5
R3(config)#exit
R3#write
```

### 4. Вывод консоли: статус интерфейсов и тестирование

Команда `show ip interface brief` на R1:

| Интерфейс | IP-адрес | OK? | Метод | Статус | Протокол |
|---|---|---|---|---|---|
| FastEthernet0/0 | 172.16.0.1 | YES | manual | up | up |
| FastEthernet0/1 | unassigned | YES | unset | administratively down | down |

Статус `up/up` для FastEthernet0/0 означает, что интерфейс физически включен и канальный протокол активен. Интерфейс FastEthernet0/1 на R1 не задействован в текущей топологии, поэтому находится в состоянии `administratively down`.

```
R1#ping 172.16.0.6

Type escape sequence to abort.
Sending 5, 100-byte ICMP Echos to 172.16.0.6, timeout is 2 seconds:
.!!!!
Success rate is 80 percent (4/5), round-trip min/avg/max = 6/6/7 ms
```

Первый пакет (`.`) потерян из-за разрешения адреса (ARP-запрос), последующие 4 пакета (`!!!!`) успешно доставлены. Статическая маршрутизация между подсетями функционирует корректно.
