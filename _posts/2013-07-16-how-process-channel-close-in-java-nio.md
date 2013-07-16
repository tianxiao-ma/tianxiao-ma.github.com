---
title: Java NIO中如何正确处理Channel关闭事件
layout: post
permalink: /2013/07/netty-channel-framework-pipeline/
date: Tue Jul 16 11:50:00 pm GMT+8 2013
published: true
---

当客户端主动关闭链接，服务端相应的Channel会被selecor过程选中，如果是要处理OP_READ，那么Channel.read方法会返回-1，表示已经读到channel的结尾了，通过判断Channel.read的返回值，我们就可以判断客户端是否已经主动关闭了请求，如果发现返回值是-1，那么服务端就可以去关闭相应的channel了。

如果要处理的是OP_WRITE，当客户端主动关闭请求的时候，如果服务端继续调用Channel.write方法，这个方法会抛出异常。另外，我们还可以通过限制Channel.write的执行次数，来处理一些“挂起”的链接。这种挂起的连接并没有关闭，但是由于各种异常情况，Channel.write方法总是无法写入数据(返回值为0)，当执行一定次数的write方法之后，服务端可以主动关闭Channel。

还有一点，如果客户端的Channel设置了SO_LINGER，并且将其值设置为0，当客户端主动关闭链接，服务端在调用Channel.read方法的时候可能会抛出异常(Connect is reset by peer)。所以，如果不是特殊的情况，客户端应该尽量避免设置SO_LINGER为0。

> SO_LINGER：这个属性用来设置等待接收关闭确认消息的时间，如果为0，则不等待，直接关闭链接。我们知道TCP在关闭的时候会发一个关闭请求给另外一端，当收到另外一端的响应或者超过等待时间之后才会真正执行关闭。SO_LINGER就是用来设置等待时间的，如果为0，则表示不等待另外一段的关闭响应而直接关闭链接。