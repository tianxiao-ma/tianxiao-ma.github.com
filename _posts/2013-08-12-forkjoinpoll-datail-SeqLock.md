---
title: ForkJoinWorkerThread中使用的顺序锁(SeqLock)
layout: post
permalink: /2013/08/forkjoinpoll-datail-SeqLock
date: Sat Aug 12 01:30:00 pm GMT+8 2013
published: true
---

`ForkJoinWorkerThread`中没有使用加锁(`Lock`)或者同步(`Synchronized`)来保护不同线程对共享变量的访问，而是使用了无锁的`[SeqLock]()`算法。SeqLock同步算法使得读写线程都不会被阻塞，但是会出现一些无效地循环操作，来看下面的代码。

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

顺序锁实现的关键是代码第3行和第7行对`queueBase`的读取和比较操作。首先，在第3行读取了
