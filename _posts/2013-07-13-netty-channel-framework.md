---
title: Netty入门-Channel框架介绍
layout: post
permalink: /2013/07/netty-intro-channel-framwork/
date: Thu Jul 18 21:00:00 pm GMT+8 2013
published: true
---

[Netty](http://netty.io/)是一个网络通讯框架，提供了对多种传输层以及应用层协议的支持，并且具有很好的可扩展性。本文将会介绍*Netty*的`Channel`框架及其工作方式。读这篇文章需要一点[Java NIO](http://docs.oracle.com/javase/6/docs/technotes/guides/io/index.html)的背景知识。

`Channel`接口及其子类用来被用来实现各种不同的传输层和应用层协议，他们的类结构如下图所示：

![Channel Class Hierarchy](/images/2013-07/netty/channel-class-hierarchy.png)

各个不同的`Channel`具体实现类包含各个协议相关细节，包括如何建立链接、如果接收数据、如何发送数据等等。由于各个具体协议的`Channel`类已经帮助我们屏蔽了大量的底层通讯细节，使得应用程序员可以专注于业务逻辑，接收和发送数据都会变的非常的简单，只需要实现符合业务需要的`ChannelHandler`接口的实现类就可以了。

*Netty*中，`ChannelHandler`接口及其实现类向应用程序员暴露了操作网络数据的接口，根据用途不同，*Netty*中有多种不同的类型`ChannelHandler`可供我们选择和实现，其类层次结构如下图所示：

![Channel Handler Hierarchy](/images/2013-07/netty/channel-handler-hierarchy.png)

上图中的`ChannelOperationHandler`接口及其子接口和子类定义了处理数据输出的回调接口，而`ChannelStatHandler`接口及其子接口则定义了处理数据输入的回调接口。但是有一点需要注意，对channel的读写操作都是定义在`ChannelOperationHandler`中的，也就是说，如果我们想从channel直接读写数据而不是接收Netty为我们准备好的数据，那么就必须定义一个实现了`ChannelOperationHandler`接口的具体类型。

我们可以为某个`Channel`添加多个不同的`ChannelHandler`，每个`ChannelHandler`负责消息处理过程中的某个一个独立的部分，比如我们可以定义一个专门负责记录日志的`ChannelHandler`，一个转发负责编码的`ChannelHandler`，一个专门真正业务逻辑的`ChannelHandler`等等。Netty会将这些不同的`ChannelHandler`以添加时的顺序组成一个`ChannelHandler`链，一个接一个地调用这些`ChannelHandler`，而这个链由`ChannelPipeline`负责构建和管理。

下面的代码说明了如何使用Netty的`Channel`框架中的不同部分实现一个简单的服务端程序，这个程序以NIO的方式处理链接和通讯请求。

{% highlight java linenos %}

    public static void main(String... args) {
        EventLoopGroup eventLoop = new NioEventLoopGroup();
        EventLoopGroup childEventLoop = new NioEventLoopGroup();

        NioServerSocketChannel serverChannel = new NioServerSocketChannel();
        serverChannel.pipeline().addLast(new ServerHandler(childEventLoop));

        ChannelPromise regPromise = serverChannel.newPromise();
        eventLoop.register(serverChannel, regPromise);

        ChannelPromise bindPromis = serverChannel.newPromise();
        serverChannel.bind(new InetSocketAddress("localhost", 8080), bindPromis);
        try {
            bindPromis.sync();
        } catch (InterruptedException e) {
            e.printStackTrace(System.out);
            serverChannel.close();
            eventLoop.shutdownGracefully();
            childEventLoop.shutdownGracefully();
        }
    }

{% endhighlight %}

我们先不去看关于`EventLoop`部分的代码，重点来看下如何声明和使用`Channel`框架中的不同的类。`EventLoop`是Netty的另外一个核型组件，Netty的运行机制就是建立在事件循环上的，这部分将会单独进行介绍。

我们重点关注代码中的5，6两行，第5行创建了一个`NioServerSocketChannel`类，而第6行则是为这个channel添加一个`ChannelHandler`。`NioServerSocketChannel`的主要作用就是负责监听客户端的连接请求，当有新的链接进来的时候，`NioServerSocketChannel`会为每一个链接创建一个`NioSocketChannel`对象，然后启动`ChannelHanler`链的处理，将有新链接创建这个消息告诉`ChannelHandler`链上每一个感兴趣的`ChannelHandler`。在第6行代码中，我们定义了一个`ServerHandler`类，并把这个类添加到了`NioServerSocketChannel`类的`ChannelHandler`链中，在这个handler中，会将新创建的`NioSocketChannel`放到另外一个专门负责通讯的`EventLoop`中，代码如下：

{% highlight java linenos %}

    public class ServerHandler extends ChannelStateHandlerAdapter implements ChannelInboundMessageHandler<Channel> {
        private EventLoopGroup eventLoop;

        ServerHandler(EventLoopGroup eventLoop) {
            this.eventLoop = eventLoop;
        }

        @Override
        public void inboundBufferUpdated(ChannelHandlerContext ctx) throws Exception {
            MessageBuf<Channel> in = ctx.inboundMessageBuffer();
            for (;;) {
                Channel child = in.poll();
                if (child == null) {
                    break;
                }

                child.pipeline().addLast(new EchoServerHandler());

                try {
                    eventLoop.register(child);
                } catch (Throwable t) {
                    child.unsafe().closeForcibly();
                    eventLoop.shutdownGracefully();
                    System.out.println("Failed to register an accepted channel: " + child);
                    t.printStackTrace(System.out);
                }
            }

            ctx.fireInboundBufferUpdated();
        }

        @Override
        public MessageBuf<Channel> newInboundBuffer(ChannelHandlerContext ctx) throws Exception {
            return Unpooled.messageBuffer();
        }
    }

{% endhighlight %}

除了在第20行将用来真正完成通讯的`NioSocketChannel`放到`EventLoop`之外，我们还在新创建的channel的`ChannelHandler`链中添加了一个handler，在这个handler中我们就可以编写代码来处理读到的数据，如果有返回数据，也可以在这个handler中完成写数据的操作，`EchoServerHandler`的代码如下，这个handler将在标准输出打印接收到的数据，然后将相同的数据返回给客户端，如果发现收到的消息是`quit`，那么就会关闭对应的channel。

{% highlight java linenos %}

    public class EchoServerHandler extends ChannelInboundByteHandlerAdapter {
        @Override
        protected void inboundBufferUpdated(ChannelHandlerContext ctx, ByteBuf in) throws Exception {
            byte[] input = new byte[in.readableBytes()];
            in.readBytes(input, 0, in.readableBytes());
            String message = new String(input);

            if ("quit".equalsIgnoreCase(message)) {
                ctx.pipeline().close();
            } else {
                System.out.println("received message:" + message);
                ByteBuf out = ctx.channel().outboundByteBuffer();
                out.writeBytes(input);
                ctx.pipeline().flush();
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            cause.printStackTrace(System.out);
            ctx.pipeline().close();
        }
    }
    
{% endhighlight %}

上面这个例子说明了如何使*Netty*来创建一个服务端程序，当发现有新的链接请求之后，会创建对应的`Channel`负责与客户端通讯。同时，这个例子使用了两个**事件循环**，一个负责监听客户端的连接请求，另外一个负责处理与客户端的通信，这也是目前比较通行的做法。

我们看到，使用Netty来创建一个服务端程序非常简单的，只要了解了如何使用各个类，我们要做的基本上就只剩下写一个`ChannelHandler`来处理消息了，其余的部分Netty都已经帮我搞定了。如果是自己通过Java NIO来实现类似的功能，是需要花不少时间写不少代码的。

上面的服务端程序运行起来之后，就可以通过*telnet*命令与其进行通信了，当然要写一个客户端程序也是比较简单的，这里就不再详细说明了，要做的事情简单说来就三个，一是创建一个`NioSocketChannel`对象，二是实现一个`ChannelHandler`来处理从服务端读到的数据以及往服务端写数据，三就是将这个对象注册到一个`EventLoop`，直接上代码。

{% highlight java linenos %}

	public class NIOEchoClient {
	    private static class EchoClientHandler extends ChannelInboundByteHandlerAdapter {
	        @Override
	        protected void inboundBufferUpdated(ChannelHandlerContext ctx, ByteBuf in) throws Exception {
	            String message = null;
	            if (in.hasArray()) {
	                message = new String(in.array(), in.readerIndex(), in.readableBytes());
	            } else {
	                byte[] input = new byte[in.readableBytes()];
	                in.readBytes(input, 0, in.readableBytes());
	                BufferedReader reader = new BufferedReader(new StringReader(new String(input)));
	                message = reader.readLine();
	            }
	
	            System.out.println("message from server:" + message);
	        }
	
	        @Override
	        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
	            cause.printStackTrace(System.out);
	            ctx.pipeline().close();
	        }
	
	    }
	    
	    public static void main(String... args) {
	        NioEventLoopGroup eventLoop = new NioEventLoopGroup();
	        NioSocketChannel channel = new NioSocketChannel();
	        channel.pipeline().addLast(new EchoClientHandler());
	
	        ChannelPromise regPromise = channel.newPromise();
	        eventLoop.register(channel, regPromise);
	        try {
	            regPromise.sync();
	        } catch (InterruptedException e) {
	            e.printStackTrace(System.out);
	            channel.close();
	            eventLoop.shutdownGracefully();
	            System.exit(1);
	        }
	
	        ChannelPromise connectPromise = channel.newPromise();
	        channel.connect(new InetSocketAddress("localhost", 8080), connectPromise);
	        try {
	            connectPromise.sync();
	        } catch (InterruptedException e) {
	            e.printStackTrace(System.out);
	            channel.close();
	            eventLoop.shutdownGracefully();
	            System.exit(1);
	        }
	
	        System.out.println("<quit> for exit");
	        BufferedReader consoleReader = new BufferedReader(new InputStreamReader(System.in));
	        UnpooledByteBufAllocator bufAllocator = new UnpooledByteBufAllocator(true);
	        while (true) {
	            if (!channel.isActive()) {
	                break;
	            }
	
	            try {
	                StringBuilder sb = new StringBuilder(consoleReader.readLine());
	                for (int i = sb.length() - 1; i >= 0; i--) {
	                    if (sb.charAt(i) == '\n' || sb.charAt(i) == '\r') {
	                        sb.deleteCharAt(i);
	                    } else {    
	                        break;
	                    }
	                }
	
	                ByteBuf outBuf = bufAllocator.ioBuffer();
	                outBuf.writeBytes(sb.toString().getBytes());
	                channel.write(outBuf);
	                if ("quit".equalsIgnoreCase(sb.toString())) {
	                    try {
	                        channel.flush().sync();
	                    } catch (InterruptedException e1) {
	                        e1.printStackTrace(System.out);
	                    } finally {
	                        channel.close();
	                    }
	                    break;
	                } else {
	                    channel.flush().sync();
	                }
	            } catch (IOException e) {
	                e.printStackTrace(System.out);
	                channel.write("quit");
	                try {
	                    channel.flush().sync();
	                } catch (InterruptedException e1) {
	                    e1.printStackTrace(System.out);
	                    channel.close();
	                }
	            } catch (InterruptedException e) {
	                e.printStackTrace(System.out);
	            }
	        }
	
	        System.exit(0);
	    }
	}                    

{% endhighlight %}
