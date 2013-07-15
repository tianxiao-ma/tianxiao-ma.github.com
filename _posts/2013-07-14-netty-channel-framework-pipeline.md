---
title: Netty Channel框架详解之Pipeline机制
layout: post
permalink: /2013/07/netty-channel-framework-pipeline/
date: Mon Jul 15 23:00:00 pm GMT+8 2013
published: true
---

在[上一篇文章]()中，对[Netty](http://netty.io)的`Channel`框架做了一个整体的介绍，也给出了初始化一个NIO Server的代码示例。本节将对`Channel`框架中的`Pipeline`机制进行展开介绍。

`ChannelPipeline`接口继承了`ChannelInboundInvoker`接口和`ChannelOutboundInvoker`接口，这样`ChannelPipeline`就变成了一个*Mix-In*接口，提供了处理Netty中的inbound事件和outbound事件的接口。在Netty中，inbound事件主要包括新数据读取事件和`Channel`在`EventLoop`中的相关事件(向`EventLoop`注册、取消注册等)，这些事件最终都会被传递到`ChannelHandler`的相应接口上，应用程序可以根据需要来处理相关的事件。outbound事件基本都是与直接发生在`Channel`上操作相关的事件，比如绑定ip和端口(bind)、链接远程服务器(connect)、关闭channel、从channel上读写数据、通过channel发送文件等等。从Netty对这两个接口的定义和使用来看，`ChannelInboundInvoker`接口的抽象级别要高一些，而`ChannelOutboundInvoker`接口中的方法跟底层的channel的操作关联度更大。从接口的命名来看，不是特别的好。

每个`Channel`的具体实现类，在初始化的时候，都默认创建了一个`ChannelPipline`，是`DefaultChannelPipeline`的一个实例。`DefaultChannelPipeline`的构造函数如下：

{% highlight java linenos %}

    public DefaultChannelPipeline(Channel channel) {
        if (channel == null) {
            throw new NullPointerException("channel");
        }
        this.channel = channel;

        TailHandler tailHandler = new TailHandler();
        tail = new DefaultChannelHandlerContext(this, generateName(tailHandler), tailHandler);

        HeadHandler headHandler;
        switch (channel.metadata().bufferType()) {
        case BYTE:
            headHandler = new ByteHeadHandler(channel.unsafe());
            break;
        case MESSAGE:
            headHandler = new MessageHeadHandler(channel.unsafe());
            break;
        default:
            throw new Error("unknown buffer type: " + channel.metadata().bufferType());
        }

        head = new DefaultChannelHandlerContext(this, generateName(headHandler), headHandler);

        head.next = tail;
        tail.prev = head;
    }

{% endhighlight %}

从其构造函数可以看到整个pipeline是由一个个`DefaultChannelHandlerContext`对象组成的，同时，Netty将会在这个pipeline中添加头尾(head和tail)两个元素，从24，25两行代码，我们还能知道，整个pipeline是一个循环链表。对于Netty的pipeline来说，`head`和`tail`两个元素是比较特殊的，这一点可以从`DefaultChannelHandlerContext`的构造函数定义看出来，`DefaultChannelHandlerContext`定义了三个构造函数，如下：

{% highlight java linenos %}

    DefaultChannelHandlerContext(DefaultChannelPipeline pipeline, EventExecutorGroup group, String name, ChannelHandler handler);
    DefaultChannelHandlerContext(DefaultChannelPipeline pipeline, String name, HeadHandler handler);
    DefaultChannelHandlerContext(DefaultChannelPipeline pipeline, String name, TailHandler handler);

{% endhighlight %}

上面第2和第3个构造函数是专门为`head`和`tail`两个元素提供的，在前面给出的`DefaultChannelPipeline`的构造函数可以看到这一点。`DefaultChannelPipeline`定义了很多方法可以往pipeline中添加`ChannelHandler`的方法，每一个被添加到`DefaultChannelPipeline`中的`ChannelHandler`都会被封装到一个`DefaultChannelHandlerContext`中，然后根据使用的添加方法被添加到pipeline的相应位置。以`DefaultChannelPipeline`的`addLast`方法为例，来看一下添加的过程，相关代码如下：

{% highlight java linenos %}

    @Override
    public ChannelPipeline addLast(ChannelHandler... handlers) {
        return addLast(null, handlers);
    }
    
    @Override
    public ChannelPipeline addLast(EventExecutorGroup executor, ChannelHandler... handlers) {
        if (handlers == null) {
            throw new NullPointerException("handlers");
        }

        for (ChannelHandler h: handlers) {
            if (h == null) {
                break;
            }
            addLast(executor, generateName(h), h);
        }

        return this;
    }

    @Override
    public ChannelPipeline addLast(EventExecutorGroup group, final String name, ChannelHandler handler) {
        synchronized (this) {
            checkDuplicateName(name);

            DefaultChannelHandlerContext newCtx = new DefaultChannelHandlerContext(this, group, name, handler);
            addLast0(name, newCtx);
        }

        return this;
    }

    private void addLast0(final String name, DefaultChannelHandlerContext newCtx) {
        checkMultiplicity(newCtx);

        DefaultChannelHandlerContext prev = tail.prev;
        newCtx.prev = prev;
        newCtx.next = tail;
        prev.next = newCtx;
        tail.prev = newCtx;

        name2ctx.put(name, newCtx);

        callHandlerAdded(newCtx);
    }
    
{% endhighlight %}

从上面代码的调用链可以看出来，新添加的`ChannelHandler`会被包装到一个`DefaultChannelHandlerContext`对象中，而从调用链末尾第34行的`addLast0`方法的代码可以看到，虽然从方法名来看新添加的`ChannelHandler`将会被放到整个pipeline的末尾，但是这个`ChannelHandler`还是被放到了`tail`对象的前面。同样的情况对于在pipeline开头添加`ChannelHandler`的情况也是一样的，新添加的`ChannelHandler`会被放到`head`对象的后面。

那么，`head`和`tail`这两个对象在整个pipeline中到底扮演了什么样的角色呢？首先要从`DefaultChannelPipeline`如何实现分别从`ChannelInboundInvoker`和`ChannelOutboundInvoker`继承过来的方法说起。从`DefaultChannelPipeline`对这些接口的实现中可以总结出下面的规律：

> 对于`ChannelInboundInvoker`接口方法的调用，整个pipeline是从`head`对象往`tail`对象方向寻找合适的`ChannelHandler`委派相关调用；对于`ChannelOutboundInvoker`接口方法的调用则正好相反，是从`tail`对象往`head`对象方向寻找合适的`ChannelHandler`委派相关调用；

在得到了上面的结论之后，在让我们来看下`head`和`tail`对象的具体实现。由于`DefaultChannelHandlerContext`对象只是对`ChannelHandler`对象的一个封装，`DefaultChannelHandlerContext`上的方法调用最终都会被委派给被封装的`Channel
Handler`，而从前面关于`DefaultChannelHandlerContext`类的构造函数可以知道，`tail`对象是对`TailHandler`的封装，`head`对象是对`HeadHandler`，因此我们只需要看一下这两个`ChannelHandler`的实现即可，先来看`TailHandler`的实现，代码如下：

{% highlight java linenos %}

    static final class TailHandler implements ChannelInboundHandler {

        final ByteBuf byteSink = Unpooled.buffer(0);
        final MessageBuf<Object> msgSink = Unpooled.messageBuffer(0);

		... //省略了空实现
		
        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            logger.warn(
                    "An exceptionCaught() event was fired, and it reached at the tail of the pipeline. " +
                            "It usually means the last handler in the pipeline did not handle the exception.", cause);
        }

        @Override
        public Buf newInboundBuffer(ChannelHandlerContext ctx) throws Exception {
            throw new Error();
        }

        @Override
        public void inboundBufferUpdated(ChannelHandlerContext ctx) throws Exception {
            int byteSinkSize = byteSink.readableBytes();
            if (byteSinkSize != 0) {
                byteSink.clear();
                logger.warn(
                        "Discarded {} inbound byte(s) that reached at the tail of the pipeline. " +
                        "Please check your pipeline configuration.", byteSinkSize);
            }

            int msgSinkSize = msgSink.size();
            if (msgSinkSize != 0) {
                MessageBuf<Object> in = msgSink;
                for (;;) {
                    Object m = in.poll();
                    if (m == null) {
                        break;
                    }
                    BufUtil.release(m);
                    logger.debug(
                            "Discarded inbound message {} that reached at the tail of the pipeline. " +
                                    "Please check your pipeline configuration.", m);
                }
                logger.warn(
                        "Discarded {} inbound message(s) that reached at the tail of the pipeline. " +
                        "Please check your pipeline configuration.", msgSinkSize);
            }
        }
    }

{% endhighlight %}

为了节省篇幅，上面的代码省略了空实现。这个实现最主要的部分就是对`inboundBufferUpdated`方法的实现，当某个Netty从某个channel获取了数据之后，就会调用`ChannelPipeline`的`fireInboundBufferUpdated`方法，从而通知整个pipeline中所有对这个事件感兴趣的`ChannelHandler`来处理从channel读到的数据。`fireInboundBufferUpdated`方法是定义在`ChannelInboundInvoker`接口中的，在前面我们已经说过了，`DefaultChannelPipeline`对于`ChannelInboundInvoker`中的方法，都是从`head`对象开始往后执行的，整个pipeline的末尾就是`tail`对象，这就需要`tail`对象起到*守门员*的角色，不管前面的处理器有没有正确处理读到的数据，或者前面有没有处理器处理过读到的数据，都需要有一个合理的行为来保证整个框架的正确运行。比较合理的最发就是上面代码中的做法，也就是丢弃所有读到的数据，然后打印日志。

接着来看下`HeadHandler`类的实现，代码如下，同样去掉了空实现和多余的代码：

{% highlight java linenos %}

    abstract static class HeadHandler implements ChannelOutboundHandler {

        protected final Unsafe unsafe;
        ByteBuf byteSink;
        MessageBuf<Object> msgSink;
        boolean initialized;

        protected HeadHandler(Unsafe unsafe) {
            this.unsafe = unsafe;
        }

        void init(ChannelHandlerContext ctx) {
            assert !initialized;
            switch (ctx.channel().metadata().bufferType()) {
            case BYTE:
                byteSink = ctx.alloc().ioBuffer();
                msgSink = Unpooled.messageBuffer(0);
                break;
            case MESSAGE:
                byteSink = Unpooled.buffer(0);
                msgSink = Unpooled.messageBuffer();
                break;
            default:
                throw new Error();
            }
        }

        @Override
        public final void bind(
                ChannelHandlerContext ctx, SocketAddress localAddress, ChannelPromise promise)
                throws Exception {
            unsafe.bind(localAddress, promise);
        }

        @Override
        public final void connect(
                ChannelHandlerContext ctx,
                SocketAddress remoteAddress, SocketAddress localAddress,
                ChannelPromise promise) throws Exception {
            unsafe.connect(remoteAddress, localAddress, promise);
        }

        @Override
        public final void disconnect(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
            unsafe.disconnect(promise);
        }

        @Override
        public final void close(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
            unsafe.close(promise);
        }

        @Override
        public final void deregister(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
            unsafe.deregister(promise);
        }

        @Override
        public final void read(ChannelHandlerContext ctx) {
            unsafe.beginRead();
        }

        @Override
        public final void sendFile(
                ChannelHandlerContext ctx, FileRegion region, ChannelPromise promise) throws Exception {
            unsafe.sendFile(region, promise);
        }

        @Override
        public final Buf newOutboundBuffer(ChannelHandlerContext ctx) throws Exception {
            throw new Error();
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            ctx.fireExceptionCaught(cause);
        }
    }

    private static final class ByteHeadHandler extends HeadHandler {

        private ByteHeadHandler(Unsafe unsafe) {
            super(unsafe);
        }

        @Override
        public void flush(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
            int discardedMessages = 0;
            MessageBuf<Object> in = msgSink;
            for (;;) {
                Object m = in.poll();
                if (m == null) {
                    break;
                }

                if (m instanceof ByteBuf) {
                    ByteBuf src = (ByteBuf) m;
                    byteSink.writeBytes(src, src.readerIndex(), src.readableBytes());
                } else {
                    logger.debug(
                            "Discarded outbound message {} that reached at the head of the pipeline. " +
                                    "Please check your pipeline configuration.", m);
                    discardedMessages ++;
                }

                BufUtil.release(m);
            }

            if (discardedMessages != 0) {
                logger.warn(
                        "Discarded {} outbound message(s) that reached at the head of the pipeline. " +
                        "Please check your pipeline configuration.", discardedMessages);
            }

            unsafe.flush(promise);
        }
    }

    private static final class MessageHeadHandler extends HeadHandler {

        private MessageHeadHandler(Unsafe unsafe) {
            super(unsafe);
        }

        @Override
        public void flush(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
            int byteSinkSize = byteSink.readableBytes();
            if (byteSinkSize != 0) {
                byteSink.clear();
                logger.warn(
                        "Discarded {} outbound byte(s) that reached at the head of the pipeline. " +
                                "Please check your pipeline configuration.", byteSinkSize);
            }
            unsafe.flush(promise);
        }
    }
 
{% endhighlight %}

从上面`HeadHandler`及其子类我们可以看到，每一个方法最后都将调用传递给了一个`unsafe`对象。这个`unsafe`对象是与Netty中每个不同类型的`Channel`绑定的，每个`Channel`内部都会有个`unsafe`对象，用来执行真正的面向channel的底层操作，类似于*Java*中的`Unsafe`类。前面我们已经提到，对于`ChannelOutboundInvoker`接口中定义的事件，pipeline是从`tail`开始往`head`方向逐个调用合适的`ChannelHandler`的，从上面Netty对于`HeadHandler`的实现来看，这些方法最终都会在某个具体的channel上产生相关的操作，我们通过添加`ChannelOutboundHandler`接口的实现类到pipeline中，可以在具体的channel执行相关操作之前，做一些数据准备或者其他一些事情，而最终与channel相关的操作则由Netty帮我们完成了。

在了解pipeline的执行顺序之后，我们就可以根据自己的需要往pipeline中添加不同类型的`ChannelHandler`来完成不同的功能，而Netty则为我们处理了大部分与具体channel相关的事情，使得我们能够更加专注与应用逻辑的编写。在pipeline的处理过程中，当前的`ChannelHandler`完成了处理之后，将调用传递给pipeline上的下一个`ChannelHandler`的工作需要我们做一些额外的工作，否则的话pipeline上的下一个`ChannelHandler`将不会被继续执行，整个处理过程就中断了。为了说明这个问题，我们来看下`ChannelOperationHandlerAdapter`的实现，代码如下：

{% highlight java linenos %}

	public abstract class ChannelOperationHandlerAdapter extends ChannelHandlerAdapter implements ChannelOperationHandler {
	    @Override
	    public void bind(ChannelHandlerContext ctx, SocketAddress localAddress,
	            ChannelPromise promise) throws Exception {
	        ctx.bind(localAddress, promise);
	    }
	    
	    ...
	}

{% endhighlight %}

上面的代码只给出了一个接口的实现，不过用来说明问题足够了。`ChannelOperationHandlerAdapter`类实现了`ChannelOperationHandler`接口，这个接口里面定义了处理Netty中outbound事件的相关接口。以上面给出的`bind`接口为例，其中直接调用了`ctx`的`bind`方法，这一步就是将处理移交给pipeline上下一个`ChannelHandler`所需要执行的步骤。`ChannelHandler`及其各种子接口上的方法上都有一个`ChannelHandlerContext`类型的参数，目的就是为了能够通过调用这个类上相关的方法将处理移交给pipeline上的下一个`ChannelHandler`。下面来看下`DefaultChannelHandlerContext`类中`bind`方法的实现，代码如下：

{% highlight java linenos %}

	...
	
    @Override
    public ChannelFuture bind(SocketAddress localAddress, ChannelPromise promise) {
        if (localAddress == null) {
            throw new NullPointerException("localAddress");
        }
        validateFuture(promise);
        return findContextOutbound().invokeBind(localAddress, promise);
    }
    
    ...

{% endhighlight %}

在上面代码中，我们注意`findContextOutbound`方法的调用，这个方法的代码如下：

{% highlight java linenos %}

    private DefaultChannelHandlerContext findContextOutbound() {
        DefaultChannelHandlerContext ctx = this;
        do {
            ctx = ctx.prev;
        } while (!(ctx.handler() instanceof ChannelOperationHandler));
        return ctx;
    }

{% endhighlight %}

作用就是从当前的`DefaultChannelHandlerContext`开始，在pipeline上往前找下一个`DefaultChannelHandlerContext`，这个`DefaultChannelHandlerContext`中的handler实现了`ChannelOperationHandler`接口。我们在前面已经介绍过，对于outbound事件，Netty的pipeline是从`tail`对象往`head`对象方法执行的，在这里也可以得到验证。相应的，在`DefaultChannelHandlerContext`中也存在一个`findContextInbound`方法，用来找下一个可以处理inbound事件的`DefaultChannelHandlerContext`对象的，这个方法的代码如下：

{% highlight java linenos %}

    private DefaultChannelHandlerContext findContextInbound() {
        DefaultChannelHandlerContext ctx = this;
        do {
            ctx = ctx.next;
        } while (!(ctx.handler() instanceof ChannelStateHandler));
        return ctx;
    }

{% endhighlight %}

到这里为止，已经对Netty中channel框架的几个主要部分都比较详细的介绍。总接下来就是，在Netty提供的框架下，我们基本只需要编写负责处理业务逻辑的`ChannelHandler`，并将其放到`ChannelPipeline`上，剩下的所有事情Netty都帮我们处理掉了。因此，在没有特殊需求的情况，要编写一个Client-Server应用是非常方便的。