---
title: 避免线程调用栈过度增长的一种方法
layout: post
permalink: /2014/11/localscheduler
date: Thu Nov 6 12:10:56 pm GMT+8 2014
published: true
---

假设有一个线程本地调度器`LocalSchedule`，当前线程提交的任务会在当前线程被执行。而当前线程提交的任务可能会出现循环调用某个方法的情况，比如下面的这个`Worder`类：

{% highlight java linenos %}
public class Worker {
    public void doWork() {
        LocalScheduler.scheduler.submit(new Runnable() {
            @Override
            public void run() {
                System.out.println("do work");
                doWork();
            }
        });
    }
}
{% endhighlight %}

上面的这个`Worker`类在提交的任务中，不断调用`doWork`方法，如果不对这种情况进行处理，那么当前线程的调用栈会不断增长，最终导致线程因为调用栈过大而异常退出。在实现`LocalScheduler`的时候需要对这种情况进行处理，代码如下：

{% highlight java linenos %}
public class LocalScheduler {
    public static final LocalScheduler scheduler = new LocalScheduler();

    private LocalScheduler() {}

    private boolean isRunning = false;
    private LinkedList<Runnable> tasks = new LinkedList<Runnable>();

    public void submit(Runnable r) {
      // 对于isRunning的判断很重要，如果没有isRunning这个变量在，会形成无限循环，最终导致栈溢出
        if (!isRunning) {
            isRunning = true;

            r.run();
            try {
                Runnable next;
                while ((next = tasks.poll()) != null) {
                    next.run();
                }
            } finally {
                isRunning = false;
            }
        }

        tasks.offer(r);
    }
}
{% endhighlight %}

在`LocalScheduler`的实现中，加入了一个`isRunning`变量，在`submit`方法中会判断`isRunning`的值，只有当`isRunning`为false的时候才会直接执行传进来的`Runnable`，否则会将`Runnable`对象放到`tasks`队列中。对于前面的`Worker`类来说，当`doWork`方法第二次被调用时，向`LocalScheduler`提交的`Runnable`会被放到`tasks`队列中，从而避免了递归调用。在`submit`方法内部的`while`循环则用来执行执行`tasks`队列中的所有`Runnable`。

上面的方式从本质上讲是通过`while`循环代替递归调用。

