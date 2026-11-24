## Лабораторная работа 5

### 1. Выполнить этапы настройки статической маршрутизации на устройствах Cisco, изложенные в документе lab5.pdf. 

#### 1. Откройте программу Packet Tracer и загрузите сетевую топологию, которая получилась в результате успешного выполнения лабораторной №4.
3 маршрутизатора (модель Cisco 2621XM). R1 и R2 соединены через интерфейсы FastEthernet (кабель Copper Cross-Over). R2 и R3 соединены через интерфейсы Serial (кабель Serial DTE/DCE, синхронизация DCE задана на R2).

#### 2. Проверьте правильность начальной конфигурации устройств:

2.1. Проверьте конфигурацию и активность интерфейсов маршрутизатора R1.
```
R1>show ip interface brief 
Interface              IP-Address      OK? Method Status                Protocol 
FastEthernet0/0        172.16.0.1      YES manual up                    up 
FastEthernet0/1        unassigned      YES unset  administratively down down
```
2.2. Проверьте информацию в таблице маршрутизации R1.
```
R1>show ip route
Codes: C - connected, S - static, I - IGRP, R - RIP, M - mobile, B - BGP
       D - EIGRP, EX - EIGRP external, O - OSPF, IA - OSPF inter area
       N1 - OSPF NSSA external type 1, N2 - OSPF NSSA external type 2
       E1 - OSPF external type 1, E2 - OSPF external type 2, E - EGP
       i - IS-IS, L1 - IS-IS level-1, L2 - IS-IS level-2, ia - IS-IS inter area
       * - candidate default, U - per-user static route, o - ODR
       P - periodic downloaded static route

Gateway of last resort is not set

     172.16.0.0/30 is subnetted, 2 subnets
C       172.16.0.0 is directly connected, FastEthernet0/0
S       172.16.0.4 [1/0] via 172.16.0.2
```


#### 3. Настройте статическую маршрутизацию на устройстве R1
```
R1(config)#ip route 172.16.0.0 255.255.255.252 172.16.0.5
```
#### 4. Настройте статическую маршрутизацию на устройстве R3
```
R1(config)#ip route 172.16.0.0 255.255.255.252 172.16.0.5
```
#### 5. Проверьте правильность конфигурации статической маршрутизации.

5.1. С устройства R1 проверьте достижимость устройства R3 с помощью команды ping.
```
R1(config)#ip route 172.16.0.0 255.255.255.252 172.16.0.5
```
5.2. С помощью команды show ip route убедитесь, что статические маршруты появились в
таблицах маршрутизации R1 и R2.
```
R1>show ip route
Codes: C - connected, S - static, I - IGRP, R - RIP, M - mobile, B - BGP
       D - EIGRP, EX - EIGRP external, O - OSPF, IA - OSPF inter area
       N1 - OSPF NSSA external type 1, N2 - OSPF NSSA external type 2
       E1 - OSPF external type 1, E2 - OSPF external type 2, E - EGP
       i - IS-IS, L1 - IS-IS level-1, L2 - IS-IS level-2, ia - IS-IS inter area
       * - candidate default, U - per-user static route, o - ODR
       P - periodic downloaded static route

Gateway of last resort is not set

     172.16.0.0/30 is subnetted, 2 subnets
C       172.16.0.0 is directly connected, FastEthernet0/0
S       172.16.0.4 [1/0] via 172.16.0.2
```

### 2. Собрать схему сети согласно выданному варианту задания; распределить IP-адреса по аналогии с примером в lab5-a.pdf; составить таблицу сетевых адресов; сконфигурировать устройства.

### 3. Для собранной схемы сети выполнить настройку статической маршрутизации.
#### ТАБЛИЦА АДРЕСАЦИИ
| Устройство | Интерфейс | IP-адрес | Маска подсети | Шлюз (Gateway) | Подключение |
|---|---|---|---|---|---|
| PC1 | FastEthernet0 | 192.168.1.10 | 255.255.255.0 | 192.168.1.1 | R1 (Fa0/0) |
| PC2 | FastEthernet0 | 192.168.5.10 | 255.255.255.0 | 192.168.5.1 | R2 (Fa0/1) |
| PC3 | FastEthernet0 | 192.168.6.10 | 255.255.255.0 | 192.168.6.1 | R3 (Fa0/0) |
| PC4 | FastEthernet0 | 192.168.8.10 | 255.255.255.0 | 192.168.8.1 | R4 (Fa0/1) |
| R1 | Fa0/0 | 192.168.1.1 | 255.255.255.0 | N/A | PC1 |
| R1 | Fa0/1 | 192.168.2.1 | 255.255.255.0 | N/A | R2 (Fa0/0) |
| R1 | Se0/1/0 | 192.168.3.1 | 255.255.255.0 | N/A | R3 (Se0/1/0) |
| R2 | Fa0/0 | 192.168.2.2 | 255.255.255.0 | N/A | R1 (Fa0/1) |
| R2 | Fa0/1 | 192.168.5.1 | 255.255.255.0 | N/A | PC2 |
| R2 | Se0/1/0 | 192.168.4.1 | 255.255.255.0 | N/A | R3 (Se0/1/1) |
| R3 | Se0/1/0 | 192.168.3.2 | 255.255.255.0 | N/A | R1 (Se0/1/0) |
| R3 | Se0/1/1 | 192.168.4.2 | 255.255.255.0 | N/A | R2 (Se0/1/0) |
| R3 | Fa0/0 | 192.168.6.1 | 255.255.255.0 | N/A | PC3 |
| R3 | Fa0/1 | 192.168.7.1 | 255.255.255.0 | N/A | R4 (Fa0/0) |
| R4 | Fa0/0 | 192.168.7.2 | 255.255.255.0 | N/A | R3 (Fa0/1) |
| R4 | Fa0/1 | 192.168.8.1 | 255.255.255.0 | N/A | PC4 |
#### БАЗОВАЯ НАСТРОЙКА (на примере R1)
```
enable
configure terminal
hostname R1
interface FastEthernet0/0
ip address 192.168.1.1 255.255.255.0
no shutdown
exit
interface FastEthernet0/1
ip address 192.168.2.1 255.255.255.0
no shutdown
exit
interface Serial0/1/0
ip address 192.168.3.1 255.255.255.0
clock rate 64000
no shutdown
exit
```
#### НАСТРОЙКА СТАТИЧЕСКОЙ МАРШРУТИЗАЦИИ
Настройка R1

```
enable
configure terminal
ip route 192.168.4.0 255.255.255.0 192.168.3.2
ip route 192.168.5.0 255.255.255.0 192.168.2.2
ip route 192.168.6.0 255.255.255.0 192.168.3.2
ip route 192.168.7.0 255.255.255.0 192.168.3.2
ip route 192.168.8.0 255.255.255.0 192.168.3.2
end
write memory
```
Настройка R2

```
enable
configure terminal
ip route 192.168.1.0 255.255.255.0 192.168.2.1
ip route 192.168.3.0 255.255.255.0 192.168.2.1
ip route 192.168.6.0 255.255.255.0 192.168.4.2
ip route 192.168.7.0 255.255.255.0 192.168.4.2
ip route 192.168.8.0 255.255.255.0 192.168.4.2
end
write memory
```
Настройка R3

```
enable
configure terminal
ip route 192.168.1.0 255.255.255.0 192.168.3.1
ip route 192.168.2.0 255.255.255.0 192.168.3.1
ip route 192.168.5.0 255.255.255.0 192.168.4.1
ip route 192.168.8.0 255.255.255.0 192.168.7.2
end
write memory
```
Настройка R4

```
enable
configure terminal
ip route 192.168.1.0 255.255.255.0 192.168.7.1
ip route 192.168.2.0 255.255.255.0 192.168.7.1
ip route 192.168.3.0 255.255.255.0 192.168.7.1
ip route 192.168.4.0 255.255.255.0 192.168.7.1
ip route 192.168.5.0 255.255.255.0 192.168.7.1
ip route 192.168.6.0 255.255.255.0 192.168.7.1
end
write memory
```
#### ПРОВЕРКА КОНФИГУРАЦИИ
Команда show ip route (на примере R1):
```
R1>show ip route
Codes: C - connected, S - static, I - IGRP, R - RIP, M - mobile, B - BGP
       D - EIGRP, EX - EIGRP external, O - OSPF, IA - OSPF inter area
       N1 - OSPF NSSA external type 1, N2 - OSPF NSSA external type 2
       E1 - OSPF external type 1, E2 - OSPF external type 2, E - EGP
       i - IS-IS, L1 - IS-IS level-1, L2 - IS-IS level-2, ia - IS-IS inter area
       * - candidate default, U - per-user static route, o - ODR
       P - periodic downloaded static route

Gateway of last resort is not set

C    192.168.1.0/24 is directly connected, FastEthernet0/0
C    192.168.2.0/24 is directly connected, FastEthernet0/1
C    192.168.3.0/24 is directly connected, Serial0/0
S    192.168.4.0/24 [1/0] via 192.168.3.2
S    192.168.5.0/24 [1/0] via 192.168.2.2
S    192.168.6.0/24 [1/0] via 192.168.3.2
S    192.168.7.0/24 [1/0] via 192.168.3.2
S    192.168.8.0/24 [1/0] via 192.168.3.2
```
#### ПИНГ 
P1-P4:
```
R1>ping 192.168.8.10

Type escape sequence to abort.
Sending 5, 100-byte ICMP Echos to 192.168.8.10, timeout is 2 seconds:
..!!!
Success rate is 60 percent (3/5), round-trip min/avg/max = 6/7/9 ms

R1>ping 192.168.8.10

Type escape sequence to abort.
Sending 5, 100-byte ICMP Echos to 192.168.8.10, timeout is 2 seconds:
!!!!!
Success rate is 100 percent (5/5), round-trip min/avg/max = 8/10/17 ms
```

