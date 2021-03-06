---
title: TCP TIMEWAIT状态解析2-解决方案
layout: post
permalink: /2014/04/coping-with-tcp-time-wait-state-part-2
date: Sun Apr 6 22:15:56 pm GMT+8 2014
published: true
---
### TIMEWAIT存在的意义
TIMEWAIT状态的存在是用来保证连接的可靠关闭，主要是为了防止2类问题的出现，

1.最后一个ACK包丢失。我们知道，TCP的关闭过程总共需要连接的两端交换4个数据包，如下图所示：

![tcp_close_package_exchange](/images/2014-04/tcp-close-package-exchange.png)

当Client发送了ACK-2之后，为了尽量保证这个包被Server接收，Client会等待一段时间(两倍的MSL)。如果在这段时间内没有收到重传ACK-2请求，那么就认为ACK-2已经被Server端接收了。在这段等待时间内，连接一直处于`TIMEWAIT`状态。

2.延迟的数据包被错误地接收。如果没有`TIMEWAIT`状态，在一个连接被关闭之后，允许立即重用这个连接，那么就有可能会发生延迟的数据包被错误接收的情况，如下图所示：

   ![tcp_package_miss_accept](/images/2014-04/tcp-package-miss-accept.png)
   
在上面的图中，SEQ=3的包被重传了一次，但是最初的那个包最终还是到达了目的地。可是此时的连接已经不是发送SEQ=3这个包时候的那个连接了，而这个新的连接上面有正好传送了SEQ=1和SEQ=2这两个包，因此SEQ=3这包就错误地被新的连接给接收了。虽然这种情况发生的概率很小，可是仍然是存在的。`TIMEWAIT`状态的存在，并且其时长是2倍的MSL就是为了应对这种情况。MSL是包在网络上的最大存活时间，如果在2倍的MSL中没有收到另一端任何新的数据包，那么重用当前连接是安全的。因为，这种情况下，即使有延迟的数据包存在，那么这些数据包也会因为存活时间的原因而失效(一个MSL用来等待自己发出去的最后一个ACK失效，另外一个MSL用来保证延迟数据包的失效)。

### 禁用SO_LINGER
当调用Socket的close()方法时，应用程序会立即返回。如果被关闭的Socket的发送缓冲区中仍然有尚未被发送的数据，内核会启动一个后台线程继续完成发送，当所有数据都被发送完成之后，就会进入正常的TCP关闭过程，Socket最终会进入到`TIMEWAIT`状态。这个机制叫做*Lingering*。

在Java中，`Socket`类有一个叫做`setSoLinger`的方法，可以禁用*Lingering*机制。当TCP的*Lingering*机制被禁用后，在Socket被关闭时，连接会被立即销毁，还没有被发送的数据也会被丢弃，TCP连接的另一端会收到一个*RST*数据包，导致另一端的TCP连接连接抛出异常。在其他的语言中，也会有类似的关闭*Lingering*机制的方法。

由于禁用*Lingering*机制的Socket在关闭时不会走正常的TCP关闭过程，因此Socket也就不会进入到`TIMEWAIT`状态，所以这种这个方法是可以用来减少处在`TIMEWAIT`的连接的。但是，这种方式有一个最大的问题，那就是会丢弃数据，在很多场景下数据丢弃是无法被接受的，因此禁用*Lingering*机制并不是解决TIMEWAIT状态的好办法，应该慎用。

### 设置内核的net.ipv4.tcp_tw_reuse选项
这个选项利用了[RFC 1323](http://tools.ietf.org/html/rfc1323)对TCP协议增加的时间戳扩展。当这个选项被开启的时候，发起连接的一方可以快速重用处在'TIMEWAIT'状态的连接来建立新的连接。

这个选项对于服务端是没有用处的，因为服务端是接受连接的一方。另外，这个选项在内核的tcp_timestamps选项被开启的情况下才会生效。

### 设置内核的net.ipv4.tcp_tw_recycle选项
这个选项也是利用了[RFC 1323](http://tools.ietf.org/html/rfc1323)对TCP协议增加的时间戳扩展，所以只有当内核开启tcp_timestamps选项时，打开这个选项才会生效。

如果这个选项被开启，那么当一个连接进入到`TIMEWAIT`状态时，从该连接收到的最后一个数据包的时间戳以及连接另一端的主机信息将会被内核记录下来，在这之后从另一端主机收到的所有时间戳小于被记录下来的时间戳的数据包都将会被丢弃，同时该连接将会在一个RTO(retransmission timeout)之后被回收。由于RTO一般都要比2被的MSL时间要短，因此开启这个选项有助于加快处在`TIMEWAIT`状态的连接被回收。

但是，这个选项无法正确处理通过NAT设备(网络地址转换设备，比如路由器)发起连接的主机。这是因为内核是以主机的IP地址来识别主机的，由于通过NAT设备访问internet的主机的时间戳都是不一样的(启动时间不同)，但是他们的出口IP都是一样的，就是NAT设备的IP地址，这就会导致主机数据包被误丢弃的情况发生。比如，有两台主机A和B，通过路由器R访问服务器C，服务器C先断开了主机A的连接，从而是该连接进入到了`TIMEWAIT`状态，此时服务器C在内核中会记录下从主机A收到的最后一个数据包的时间戳T，并将这个时间戳与路由器R的IP地址关联起来(A和B的出口IP都是路由器R的IP)。之后，如果主机B继续向服务器C发送数据，由于主机B和A经过路由器访问网络，在服务器C看来，他们的数据包都是来自同一个IP地址的，因此，如果发现B发来的数据包的时间戳小于之前记录下来的数据包的时间戳T，那么服务器C会丢弃这些数据包。这种情况的发生概率是很高的，因为时间戳的计算与主机的启动时间有关系，如果主机A的启动时间比主机B的启动时间早，那么当服务器关闭了与主机A的连接之后，主机B向服务器C发送的数据包都会被丢弃。如果有一台通过路由器R访问网络的主机D，在服务器关闭了与主机A的连接之后，发起对服务器C的连接，在主机D的启动时间晚于主机A的情况下，是无法建立与服务器C的连接的，因为发起连接用的SYN包会被服务器C丢弃掉。只有在经过了一个RTO，处在`TIMEWAIT`状态的连接被服务器C回收了之后，主机B的数据包才会重新被服务器C接收，而主机D才能与服务器C建立连接。

### 总结
从上面的分析，以及前一篇文章的讨论来看，对于一台Web服务器来说，当大部分的连接是由服务器来主动关闭的情况下，基本上是没有什么有效的办法可以减少处在`TIMEWAIT`状态的连接的。好在目前的服务器内存都非常大，而处在`TIMEWAIT`状态的连接占用的内存也并不是很多，因此也就不是什么太大的问题了。

对于客户端来说(发起连接的一方)，通过调大可用端口的数量(修改内核的ip_local_port_range)，或者增加IP地址，或者访问提供相同服务的不同服务器，或者开启内核的net.ipv4.tcp_tw_reuse选项等等方法，都是可以解决`TIMEWAIT`状态问题的。

最后，TIMEWAIT状态的存在是为了保证TCP连接的可靠关闭，因此，它的存在并不是一件坏事。当我们对它有了足够的了解，根据不同具体情况，进行具体的分析，是不用担心处在`TIMEWAIT`状态的连接带来的问题的。

### 参考文献
1. [http://vincent.bernat.im/en/blog/2014-tcp-time-wait-state-linux.html](http://vincent.bernat.im/en/blog/2014-tcp-time-wait-state-linux.html)
