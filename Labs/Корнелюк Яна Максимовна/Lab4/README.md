## Часть 1. Локальная сеть

### 1. Топология
Создана топология из 4 коммутаторов (Switch PT) и 7 рабочих станций (PC0, PC1, PC2, PC3, PC4, PC5, PC6). Кабели: *Copper Straight-Through* (ПК–Коммутатор) и *Copper Cross-Over* (Коммутатор–Коммутатор).

![1](<1_1.png>)
> *Рисунок 1. Топология локальной сети.*

Таблица адресов:
| Устройство | IP-адрес | Маска подсети |
| --- | --- | --- |
| PC1 | 1.1.1.1 | 255.255.255.0 |
| PC2 | 1.1.1.2 | 255.255.255.0 |
| PC3 | 1.1.1.3 | 255.255.255.0 |
| PC4 | 1.1.1.4 | 255.255.255.0 |
| PC5 | 1.1.1.5 | 255.255.255.0 |
| PC6 | 1.1.1.6 | 255.255.255.0 |
| PC7 | 1.1.1.7 | 255.255.255.0 |

### 2. Вывод консоли: проверка конфигурации (ipconfig)
```
PC>ipconfig

IP Address......................: 1.1.1.1
Subnet Mask.....................: 255.255.255.0
Default Gateway.................: 0.0.0.0
```
![2](<1_2.png>)
> *Рисунок 2. Проверка сетевой конфигурации.*

### 3. Вывод консоли: проверка связности (ping)
```
PC>ping 1.1.1.2

Pinging 1.1.1.2 with 32 bytes of data:

Reply from 1.1.1.2: bytes=32 time=129ms TTL=128
Reply from 1.1.1.2: bytes=32 time=64ms TTL=128
Reply from 1.1.1.2: bytes=32 time=43ms TTL=128
Reply from 1.1.1.2: bytes=32 time=64ms TTL=128

Ping statistics for 1.1.1.2:
    Packets: Sent = 4, Received = 4, Lost = 0 (0% loss),
Approximate round trip times in milli-seconds:
    Minimum = 43ms, Maximum = 129ms, Average = 75ms

PC>ping 1.1.1.5

Pinging 1.1.1.5 with 32 bytes of data:

Reply from 1.1.1.5: bytes=32 time=193ms TTL=128
Reply from 1.1.1.5: bytes=32 time=129ms TTL=128
Reply from 1.1.1.5: bytes=32 time=127ms TTL=128
Reply from 1.1.1.5: bytes=32 time=127ms TTL=128

Ping statistics for 1.1.1.5:
    Packets: Sent = 4, Received = 4, Lost = 0 (0% loss),
Approximate round trip times in milli-seconds:
    Minimum = 127ms, Maximum = 193ms, Average = 144ms

PC>ping 1.1.1.7

Pinging 1.1.1.7 with 32 bytes of data:

Reply from 1.1.1.7: bytes=32 time=257ms TTL=128
Reply from 1.1.1.7: bytes=32 time=159ms TTL=128
Reply from 1.1.1.7: bytes=32 time=159ms TTL=128
Reply from 1.1.1.7: bytes=32 time=145ms TTL=128

Ping statistics for 1.1.1.7:
    Packets: Sent = 4, Received = 4, Lost = 0 (0% loss),
Approximate round trip times in milli-seconds:
    Minimum = 145ms, Maximum = 257ms, Average = 180ms
```
![3](<1_3.png>)
> *Рисунок 3. проверки работоспособности сети .*

### 4. Разъяснение
* **ipconfig**: показывает настройки сетевого интерфейса (IP-адрес, маску подсети, шлюз по умолчанию). Шлюз `0.0.0.0`, так как все узлы находятся в пределах одной локальной сети и маршрутизация наружу не требуется.
* **ping**: использует протокол ICMP (Echo Request/Echo Reply). 0% потерь пакетов и минимальное время задержки подтверждают корректность коммутации на уровне L2 и IP-адресации на уровне L3 между всеми сегментами локальной сети.

---

## Часть 2. Конфигурация маршрутизаторов

### 1. Топология
3 маршрутизатора (модель Cisco 2621XM). R1 и R2 соединены через интерфейсы FastEthernet (кабель Copper Cross-Over). R2 и R3 соединены через интерфейсы Serial (кабель Serial DTE/DCE, синхронизация DCE задана на R2).

![4](<2_1.png>)
> *Рисунок 4. Топология сети с маршрутизаторами.*

### 2. Таблица сетевых адресов
| Устройство | Интерфейс | IP-адрес | Маска подсети | Роль |
| --- | --- | --- | --- | --- |
| **R1** | FastEthernet 0/0 | 10.1.1.1 | 255.255.255.252 | DTE |
| **R2** | FastEthernet 0/0 | 10.1.1.2 | 255.255.255.252 | DTE |
| **R2** | Serial 0/0 | 10.1.2.1 | 255.255.255.252 | DCE |
| **R3** | Serial 0/0 | 10.1.2.2 | 255.255.255.252 | DTE |

### 3. Ход конфигурирования
```
! Настройка R1
Router>enable
Router#conf t
Router(config)#hostname R1
R1(config)#interface FastEthernet0/0
R1(config-if)#ip address 10.1.1.1 255.255.255.252
R1(config-if)#no shutdown
R1(config-if)#exit
R1(config)#ip route 10.1.2.0 255.255.255.252 10.1.1.2
R1(config)#exit
R1#write

! Настройка R2 (DCE)
Router>enable
Router#conf t
Router(config)#hostname R2
R2(config)#interface FastEthernet0/0
R2(config-if)#ip address 10.1.1.2 255.255.255.252
R2(config-if)#no shutdown
R2(config-if)#exit
R2(config)#interface Serial0/0
R2(config-if)#ip address 10.1.2.1 255.255.255.252
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
R3(config-if)#ip address 10.1.2.2 255.255.255.252
R3(config-if)#no shutdown
R3(config-if)#exit
R3(config)#ip route 10.1.1.0 255.255.255.252 10.1.2.1
R3(config)#exit
R3#write
```

### 4. Вывод консоли: статус интерфейсов и тестирование

Команда `show ip interface brief` на R1:

![5](<2_2.png>)
> *Рисунок 5. Вывод команды и пинг.*

| Интерфейс | IP-адрес | OK? | Метод | Статус | Протокол |
|---|---|---|---|---|---|
| FastEthernet0/0 | 10.1.1.1 | YES | manual | up | up |
| FastEthernet0/1 | unassigned | YES | unset | administratively down | down |

Статус `up/up` для FastEthernet0/0 означает, что интерфейс физически включен и канальный протокол активен. Интерфейс FastEthernet0/1 на R1 не задействован в текущей топологии, поэтому находится в состоянии `administratively down`.

```
R1#ping 10.1.2.2

Type escape sequence to abort.
Sending 5, 100-byte ICMP Echos to 10.1.2.2, timeout is 2 seconds:
.!!!!
Success rate is 80 percent (4/5), round-trip min/avg/max = 48/59/64 ms
```

Первый пакет (`.`) потерян из-за разрешения адреса (ARP-запрос), последующие 4 пакета (`!!!!`) успешно доставлены. Статическая маршрутизация между подсетями функционирует корректно.