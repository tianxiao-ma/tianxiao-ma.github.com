---
title: 锁及其实现
layout: post
permalink: /2013/11/deep-in-lock
date: Fri Nov 25 21:39:56 pm GMT+8 2013
published: true
---

这里的锁是指我们在编码的过程中经常会见到或者用到的程序语言内建或者其他第三方库提供的用来控制程序对某个数据或者代码段并发访问的一种机制。

就Java来说，我们可以通过`synchronized`关键字、`Lock`对象来实现锁，那么这些锁或者说这种机制是怎么实现的呢？首先，锁肯定不是机器指令级别提供的东西，也说就是通过一条指令是没有办法实现锁的，锁是由操作系统或者类库利用特殊的机器指令实现的，方便更上层的应用程序或者语言使用。

首先来看机器指令，以Intel x64 CPU为例，Intel在其关于其x64芯片的[编程手册第8章](http://www.intel.com/content/dam/www/public/us/en/documents/manuals/64-ia-32-architectures-software-developer-vol-3a-part-1-manual.pdf)提到了这部分内容，对于一个多核cpu来说，需要提供下面的一些保证：

> * To maintain system memory coherency — When two or more processors are attempting simultaneously to access the same address in system memory, some communication mechanism or memory access protocol must be available to promote data coherency and, in some instances, to allow one processor to temporarily lock a memory location.

> * To maintain cache consistency — When one processor accesses data cached on another processor, it must not receive incorrect data. If it modifies data, all other processors that access that data must receive the modified data.

> * To allow predictable ordering of writes to memory — In some circumstances, it is important that memory writes be observed externally in precisely the same order as programmed.

> * To distribute interrupt handling among a group of processors — When several processors are operating in a system in parallel, it is useful to have a centralized mechanism for receiving interrupts and distributing them to available processors for servicing.

基本上就是提供对内存或者缓存数据读写的一致性保证。

有了CPU提供的指令，就可以实现锁。一般来说，操作系统会对机器指令进行封装，然后通过系统调用的方式暴露给应用程序使用(确实没有必要让大家都通过汇编去各自实现一遍)。我们在Java中用到的锁或者其他的同步控制工具类和机制其实都是在操作系统提供的系统调用基础之上进行的二次封装。由于不同的硬件提供上往往都会有自己的并发系统函数库，这就导致想要在不同硬件平台实现可移植性非常麻烦，所以国际标准化组织就搞出来了一个标准的编程接口`IEEE POSIX 1003.1c standard (1995)`，遵循这个标准的实现被成为`POSIX Threads`或者`Pthreads`。如果用c或者c++的话，就会用到这套编程接口。为了更加直观，让我们来看下win32下的一个[开源实现](http://www.sourceware.org/pthreads-win32/)。顺便说一下，为什么在windows下会有一个开源的实现，而针对linux平台却没有呢？估计是windows原生的库没有遵循标准编程接口的关系。

首先来看锁的定义，

{% highlight c linenos %}

struct pthread_mutex_t_
{
  LONG lock_idx;		/* Provides exclusive access to mutex state
				   via the Interlocked* mechanism.
				    0: unlocked/free.
				    1: locked - no other waiters.
				   -1: locked - with possible other waiters.
				*/
  int recursive_count;		/* Number of unlocks a thread needs to perform
				   before the lock is released (recursive
				   mutexes only). */
  int kind;			/* Mutex type. */
  pthread_t ownerThread;
  HANDLE event;			/* Mutex release notification to waiting
				   threads. */
  ptw32_robust_node_t*
                    robustNode; /* Extra state for robust mutexes  */
};

{% endhighlight %}

可以看到锁其实是一个结构体，同步是通过通过对`lock_idx`这个变量的排他访问来实现的，下面是一段加锁的代码，我们可以更加清楚的看到，

![pthread_lock](/images/2013-11/pthread-lock.png)

第一个框中的代码是对`lock_idx`这个变量的原子访问操作，如果设置失败，就会进入下面的`while`循环让当前线程去等待一个`event`事件上的一个信号。这个`while`循环的作用跟我们在编程中使用的`while`循环的作用类似，只有当获得锁之后才会真正开始执行应用程序。
	
	
	
	
	
	
	