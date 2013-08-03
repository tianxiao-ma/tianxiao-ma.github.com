---
title: Java 7 ForkJoinPool原理分析
layout: post
permalink: /2013/07/how-to-use-java-generlization/
date: Sat Aug 3 16:10:00 pm GMT+8 2013
published: true
---

`ForkJoinPool`是[Java 7](http://docs.oracle.com/javase/7/docs/)中引入了一个高性能任务执行框架，与现有`ExecutorService`的最大区别是采用了`work-stealing`技术，JDK官方文档的说法如下：

> all threads in the pool attempt to find and execute subtasks created by other active tasks (eventually blocking waiting for work if none exist). This enables efficient processing when most tasks spawn other subtasks (as do most ForkJoinTasks). 

翻译过来就是：

> `ForkJoinPool`中的每个线程在干完自己的活之后，会去拿其他线程的活来干。

Java中现有的`ExecutorService`都是一个线程执行一个任务，当一个线程执行完自己的任务之后，会等待新任务的提交或者去任务队列看是不是有可以执行的任务，如果等待一段时间之后发现没有可以执行的任务，那么这个线程就消亡了。但是在`ForkJoinPool`中，这种情况发生了变化，一个线程在干完自己的活之后，还回去抢别的线程的活来干，一直要到所有的线程都没活了，大家才会停下来。

那么一个线程在执行的时候怎么让别的线程可以抢自己的活呢？需要两个类来配合，一个是`ForkJoinWorkerThread`，另外一个是`ForkJoinTask`类。下面来看看这两个类是怎么配合工作，一个线程的活可以被别的线程抢去干的。

首先，为了让一个线程能够抢其他线程的任务，每个线程执行的任务都要能够产生子任务(只有一个任务的话，也没法抢)。其次，要有一个地放能够存放所有的子任务，并且别的线程能从这个地方抢这些子任务去执行。产生子任务的工作由`ForkJoinTask`类的`fork`方法完成，而保存子任务的地点则由`ForkJoinWorkerThread`提供。下面通过一个例子来详细说明整个过程，例子摘自[`RecursiveAction`的JDK文档](http://docs.oracle.com/javase/7/docs/api/java/util/concurrent/RecursiveAction.html)。

{% highlight java linenos %}

	class Applyer extends RecursiveAction {
		final double[] array;
		final int lo, hi;
		double result;
		Applyer next; // 保存所有被创建的子类的引用
		Applyer(double[] array, int lo, int hi, Applyer next) {
			this.array = array; this.lo = lo; this.hi = hi;
			this.next = next;
		}
	
		// 执行计算
		double atLeaf(int l, int h) {
	  		double sum = 0;
			for (int i = l; i < h; ++i) // perform leftmost base step
				sum += array[i] * array[i];
			return sum;
		}
	
		@Override
		protected void compute() {
			int l = lo;
			int h = hi;
			Applyer right = null;
			// 在条件允许的情况下，不断地创建子任务
			while (h - l > 1 && getSurplusQueuedTaskCount() <= 3) {
				int mid = (l + h) >>> 1;
				right = new Applyer(array, mid, h, right);
				right.fork();
				h = mid;
			}
			double sum = atLeaf(l, h);
			// 获取每个子任务的结果，叠加到最终的结果上
			while (right != null) {
				if (right.tryUnfork()) // directly calculate if not stolen
					sum += right.atLeaf(right.lo, right.hi);
				else {
					right.join();
					sum += right.result;
				}
				right = right.next;
			}
			result = sum;
		}
	}

{% endhighlight %}

`RecursiveAction`是`ForkJoinTask`的子类，从名字可以看到，这个类是专门为递归计算设计的。上面的类实现了`RecursiveAction`，用来计算一个数组中各个元素的平方和。代码第25行的`while`循环用来不断地创建子任务，每一个子任务负责计算数组的后半段元素的平方和。只要条件允许，这样的子任务会不断的创建。`getSurplusQueuedTaskCount()`方法返回过剩的任务数，也就是当前线程的任务总数与能够被抢走的任务数的差值，如果这个差值比较大，说明其他线程也都比较忙了，自己的活都干部完，没工夫抢别人的活了，这个时候我们就要自己把剩下的活给干了。子任务被创建之后，代码的第28行调用了`ForkJoinTask`的`fork`方法，调用完这个方法之后，这个子任务就进入到当前线程的任务队列中去了，其他线程就有机会能够抢到这个任务去执行了。

在创建完子任务之后，代码第31行针对剩余的数据元素进行了一次计算，然后第32行的`while`循环遍历所有被创建的子任务，获取子任务的计算结果并累加到最终结果上。第34行进行了一次判断，看看子任务是不是还在当前线程的任务队列中，如果还在那就取出来在当前线程完成计算，如果不在，说明这个任务被其他线程抢去执行了，这个时候会执行第37行的`right.join()`方法。`join`方法的作用简单来说就是等待任务完成，但是这个等待不是挂起，而是做了很多其他的事情的。

经过第32行的`while`循环之后，已经获取到了所有子任务的计算结果并累加到了最终结果上，整个计算也就结束了。

让我们再进一步看一下`ForkJoinTask`类的`fork`和`join`方法，先来`fork`方法，代码如下：

{% highlight java linenos %}

    public final ForkJoinTask<V> fork() {
        ((ForkJoinWorkerThread) Thread.currentThread())
            .pushTask(this);
        return this;
    }

{% endhighlight %}

这个方法比较简单，就是把当前任务提交到当前线程的任务队列中，注意代码中的强制类型转换，从这个转换可以看出，要想成功执行`fork`方法，当前线程必须是`ForkJoinWorkerThread`类型，换句话说就是任务必须被提交到`ForkJoinPool`里面才可以。另外一点就是，通过`pushTask`这个方法名可以推断出`ForkJoinWorkerThread`的任务队列被设计成一个栈，所有的任务都是后进先出的。

下面来看下`join`方法， 前面已经说过了，这个方法并不是简单的等待任务完成，在等待的过程中还干了不少其他的事情，下面来看看这个方法的代码：

{% highlight java linenos %}

	// ForkJoinTask的join方法
    public final V join() {
        if (doJoin() != NORMAL)
            return reportResult();
        else
            return getRawResult();
    }

	// ForkJoinTask的doJoin方法
    private int doJoin() {
        Thread t; ForkJoinWorkerThread w; int s; boolean completed;
        if ((t = Thread.currentThread()) instanceof ForkJoinWorkerThread) {
            if ((s = status) < 0)
                return s;
            // 如果任务还在任务队列上，在当前线程完成这个任务并返回
            if ((w = (ForkJoinWorkerThread)t).unpushTask(this)) {
                try {
                    completed = exec();
                } catch (Throwable rex) {
                    return setExceptionalCompletion(rex);
                }
                if (completed)
                    return setCompletion(NORMAL);
            }
            // 调用ForkJoinWorkerThread的joinTask方法
            return w.joinTask(this);
        }
        else
            return externalAwaitDone();
    }
    
    // ForkJoinWorkerThread的joinTask方法
    final int joinTask(ForkJoinTask<?> joinMe) {
        ForkJoinTask<?> prevJoin = currentJoin;
        currentJoin = joinMe;
        for (int s, retries = MAX_HELP;;) {
            if ((s = joinMe.status) < 0) {
                currentJoin = prevJoin;
                return s;
            }
            if (retries > 0) {
                if (queueTop != queueBase) {
                	// 如果当前线程的任务队列中还有未执行的任务，那么称着等待的时间执行一个当前队列的任务
                    if (!localHelpJoinTask(joinMe))
                        retries = 0;           // cannot help
                }
                else if (retries == MAX_HELP >>> 1) {
                    --retries;                 // check uncommon case
                    // 检查其他线程的任务队列，如果发现joinMe是在某个线程的任务队列的末尾，在当前线程中把这个任务执行掉，这种情况不太会发生
                    if (tryDeqAndExec(joinMe) >= 0)
                        Thread.yield();        // for politeness
                }
                else
                	// 1. 找到抢了任务的线程，并帮住那个线程执行任务，知道当前任务被那个线程执行完了为止；
                	// 2. 如果抢了当前任务的那个线程的某个任务也调用了任务的join方法，在当前的任务没有完成的情况下，去帮抢了当前任务的那个线程第一步中的操作，这个过程周而复始，知道当前任务完成为止；
                    retries = helpJoinTask(joinMe) ? MAX_HELP : retries - 1;
            }
            else {
                retries = MAX_HELP;           // restart if not done
                pool.tryAwaitJoin(joinMe);
            }
        }
    }

{% endhighlight %}

总体来说`ForkJoinTask`的`join`方法在等待任务完成的过程中并没有停下来休息，而是主动帮其他线程执行去执行任务，非常地敬业。但是，帮忙也是有回报的，通过帮其他线程的忙，自己线程执行`join`方法的那个任务也是有很大可能被提早完成的。