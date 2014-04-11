---
title: 使用gen_tcp快速构建tcp服务器
layout: post
permalink: /2014/04/use-gen_tcp-to-construct-tcp-server-in-one-minute
date: Sun Apr 10 20:20:00 pm GMT+8 2014
published: true
---

`gen_tcp`是[erlang](http://www.erlang.org/)提供的一个tcp库，可以用来创建tcp服务端和客户端。使用这个库，只用少量的代码就可以创建一个tcp服务器。

### 示例代码

{% highlight erlang linenos %}

-module(socket_listener).

%% API
-export([start/1, listen/1]).

start(Port) ->
  erlang:spawn(?MODULE, listen, [Port]).

listen(Port) ->
  {ok, ListenSocket} = gen_tcp:listen(Port, []),
  accept_loop(ListenSocket).

accept_loop(ListenSocket) ->
  case gen_tcp:accept(ListenSocket) of
    {ok, Socket} ->
      io:format("accept incoming connetion from ~p.\n", [inet:peername(Socket)]),
      Pid = erlang:spawn(socket_processor, process, [Socket]),
      %% let the new spawned process control the socket, so when this process exit, the socket will be closed too. Otherwise, the socket will not be closed.
      ok = gen_tcp:controlling_process(Socket, Pid),
      Pid ! shoot;
    {error, Error} ->
      io:format("accept error:~p\n", [Error])
  end,
  accept_loop(ListenSocket).

{% endhighlight %}
上面代码的第10行通过`gen_tcp:listen(Port, Opt)`方法创建了一个监听端口，之后就进入了`accept_loop`方法。在`accept_loop`方法中，我们调用了`gen_tcp:accept(ListenSocket)`方法来接受连接，如上面第14行代码所示。

对于每一个进入的连接，上面的代码都会创建一个进程来处理这个在这个连接上的读写操作，如果上面第17行代码所示。值得注意的是第19和第20行代码，之所以会有这两行代码，与erlang在进程退出时候执行的操作有关。

当进程退出的时候，erlang会回收进程占用的资源，当然也包括网络连接。当进程退出的时候，erlang会关闭属于该进程的网络连接，也就是在Socket上执行close方法。由于我们会为每个进入的连接分配一个进程，因此我们希望当处理连接的进程退出的时候，erlang能够帮我们把连接关闭，而不需要显示地调用'gen_tcp:close(Socket)'。`gen_tcp:controlling_process(Socket, Pid)`就是来完成这个事情的。这个方法的底层实际上是将Socket的所有者改成新创建的那个进程，这样当进程退出的时候，Socket就会自动被关掉。

如果不使用`gen_tcp:controlling_process(Socket, Pid)`将Socket的所有者改成新创建的进程，那么当新创建的进程退出的时候，连接会被一直保留着，需要客户端发起连接才会关闭，造成资源泄漏。当然，我们也可以在负责处理连接的进程退出之前，主动调用`gen_tcp:close(Socket)`方法来关闭Socket，不过使用`gen_tcp:controlling_process(Socket, Pid)`方法可以防止因为忘记关闭连接而导致资源泄漏的情况发生。

对于第10行代码，我们先来看下socket_processor模块的代码，如下：

{% highlight erlang linenos %}

-module(socket_processor).

%% API
-export([process/1]).

process(Socket) ->
  receive
    shoot ->
      ok = inet:setopts(Socket, [{send_timeout, 2000}, {send_timeout_close, true}]),
      ok = inet:setopts(Socket, [binary, {active, false}, {packet, 4}]),
      loop(Socket)
  end.

loop(Socket) ->
  case gen_tcp:recv(Socket, 0) of
    {ok, Packet} ->
      io:format("~p:receive package ~p.\n", [self(), binary_to_list(Packet)]),
      loop(Socket);
    {error, closed} ->
      exit(normal);
    {error, Error} ->
      io:format("~p:receive packet error:~p.\n", [self(), Error]),
      exit(Error)
  end.

{% endhighlight %}

`process(Socket)`方法首先等待消息，只有当收到*shoot*消息之后，才会设置Socket的属性并进入`loop(Socket)`方法。

如果不是用这种消息传递的方法来进行通知，那么在socket_listener模块成功执行`gen_tcp:controlling_process(Socket, Pid)`之前，负责处理连接的进程可能已经由于种种原因而异常退出了，这就会导致`gen_tcp:controlling_process(Socket, Pid)`方法抛出异常，从而是的整个tcp服务器因此而退出。使用消息传递的小技巧可以避免这种情况发生，[Ranch](https://github.com/extend/ranch)框架也有类似的使用。

### 关于Socket属性
当我们成功accept了一个Socket之后，如果不设置任何属性而直接调用`gen_tcp:recv(Socket, Length)`方法，会接收到一个{error, einval}异常，告诉我们参数异常。这是因为，不管以什么样的方式从Socket上读取数据，必须要设置一下active属性，可以在调用`gen_tcp:connect/3,4`方法的时候设置，可以在调用`gen_tcp:listen/2`方法的时候设置，也可以通过`inet:setopts/2`设置，不过通过什么方法，active属性必须在从Socket接收数据前设置一次。

如果将active属性设置成false，那么只能通过`gen_tcp:recv/3,4`方法获取数据，如果设置成true或者once，那么可以通过接收消息的方式获取数据。关于active属性的具体介绍可以参考[erlang的文档](http://www.erlang.org/doc/man/inet.html#setopts-2)。

另外，在写数据的时候，`gen_tcp:send/2`方法会在发送的数据之前加上4字节的长度信息，相当于对Socket添加了{packet, 4}属性。如果需要按照其他的方式来读写数据，可以修改packet的设置。关于Socket的属性详细介绍可以参考[官方文档](http://www.erlang.org/doc/man/inet.html#setopts-2)。

### 关于controlling_process与active属性
只有Socket的拥有者才可以通过消息的方式获取从Socket接收到的数据，也就是说当Socket的active被设置成true或者once的时候，拥有Socket的进程才可以通过消息的方式从Socket读取数据，以及接收tcp_closed消息。

有两种方式可以让进程成为Socket的拥有者。第一，Socket的创建者默认就是Socket的拥有者，第二，通过`gen_tcp:controlling_process/2`方法设置Socket的拥有者。

当active属性被设置成false的时候，任何进程都可以调用`gen_tcp:recv/2,3`方法来读取数据。当Socket被关闭时，这两个方法会返回{error, closed}。

