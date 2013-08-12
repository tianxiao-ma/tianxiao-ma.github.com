---
title: ForkJoinWorkerThread中使用的顺序锁(SeqLock)
layout: post
permalink: /2013/08/forkjoinpoll-datail-SeqLock
date: Sat Aug 12 01:30:00 pm GMT+8 2013
published: true
---

`ForkJoinWorkerThread`中没有使用加锁(`Lock`)或者同步(`Synchronized`)来保护不同线程对共享变量的访问，而是使用了无锁的`[SeqLock](https://en.wikipedia.org/wiki/Seqlock)`算法。SeqLock同步算法使得读写线程都不会被阻塞，但是会出现一些无效地循环操作，来看下面的代码。

{% highlight java linenos %}

    final ForkJoinTask<?> deqTask() {
        ForkJoinTask<?> t; ForkJoinTask<?>[] q; int b, i;
        if (queueTop != (b = queueBase) &&
            (q = queue) != null && // must read q after b
            (i = (q.length - 1) & b) >= 0 &&
            (t = q[i]) != null && 
            queueBase == b &&
            UNSAFE.compareAndSwapObject(q, (i << ASHIFT) + ABASE, t, null)) {
            queueBase = b + 1;
            return t;
        }
        return null;
    }

{% endhighlight %}

上面代码是从`ForkJoinWorkerThread`摘录下来的，这个`deqTask`方法干的事情是从`ForkJoinWorkerThread`的任务队列末尾取去一个任务，如果取道了就返回这个任务，否则返回`null`。

代码中的`queueTop`是当前任务队列的头部索引，下一个任务将会被放到这个索引指向的位置；`queueBase`是任务队列的尾部指针，指向队列中最后一个有效的任务；`queue`是任务队列的引用。这几个变量中，只有`queueBase`被声明成了`volatile`。

顺序锁实现的关键是代码第3行和第7行对`queueBase`的读取和比较操作。首先，在第3行读取了`queueBase`赋值给变量`b`，之后从`queue`中的索引`b`位置读取一个任务，然后在代码的第7行重新比较了一下`queueBase`和变量`b`。第3行和第7行对`queueBase`的读取和比较就实现了顺序锁，先读取一个`volatile`变量的值，然后执行一些操作，最后在将之前读取的值与`volatile`变量的当前值进行一次比较，如果发现不相等，说明有其他线程对共享变量做了修改。

代码的第9行对`queueBase`执行了加1操作，这样其他线程在执行第7行的比较逻辑的时候，就会因为发现当前`queueBase`值与之前读到的`queueBase`不相等而不再执行后面的代码。

第8行代码使用了Java中`Unsafe`对象提供的内存CAS操作，因为虽然之前使用了`SeqLock`，但是仍然有可能出现多个线程同时执行到第8行代码的情况，使用CAS操作可以保证只有一个线程能够成功更新`queue`中的元素。

关于上面的代码，还有一点需要说明，第4行有一个注释，意思是说在读取`queue`之前必须先读取`queueBase`，原因在于内存可见性。为了理解这个规定，先来看下[JSR 133 FAQ](http://www.cs.umd.edu/~pugh/java/memoryModel/jsr-133-faq.html#volatile)关于`volatile`的说明，如下：

> Writing to a volatile field has the same memory effect as a monitor release, and reading from a volatile field has the same memory effect as a monitor acquire

在新的Java内存模型中(1.5机器后的版本)，`volatile`变量除了具有直接读写内存的效果之外，对`volatile`变量的写操作具有与释放监视器相同的内存效果，而读取`volatile`变量则具有与获取监视器相同的内存效果。下面是Java内存模型中定义的获取和释放监视器时对内存的影响，摘自[JSR 133 FAQ](http://www.cs.umd.edu/~pugh/java/memoryModel/jsr-133-faq.html#synchronization)：

> release the monitor, which has the effect of flushing the cache to main memory, so that writes made by this thread can be visible to other threads；
> acquire the monitor, which has the effect of invalidating the local processor cache so that variables will be reloaded from main memory

在释放监视器时会将缓存刷新到内存，这样在释放监视器之前所有发生变化的变量的新的值都会被刷新到内存中，而获取监视器时则会失效缓存，这样所有的变量都将重新从内存载入。

我们前面说过，除了`queueBase`之外，其他的所有变量都不是`volatile`类型的，所以对其他变量的读取都必须放到对`queueBase`变量的读取之后，这样才能读到这些变量的最新值。

> Tips：`queueTop != (b = queueBase)`这行代码中，会先执行`b = queueBase`，然后才会执行比较，所以`queueTop`的读取是在`queueBase`的读取之后的
