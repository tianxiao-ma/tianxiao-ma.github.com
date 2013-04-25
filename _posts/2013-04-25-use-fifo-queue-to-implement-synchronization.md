---
title: 使用队列和锁来实现多线程同步
layout: post
permalink: /2013/04/use-fifo-queue-to-implement-synchronization/
date: Wed Apr 25 19:14:00 pm GMT+8 2013
published: true
---

本文介绍一种通过队列和锁来实现线程同步的方法，改方法来自于[leveldb](https://code.google.com/p/leveldb/)。leveldb是C++写的，本文将采用Java语言来实现。通过队列和锁的方式来实现同步的这种方式，对于多个线程所做的工作可以被一个线程合并完成的场景特别适用。比如，每个线程都需要往文件中写一条记录，但是不同线程中的记录又可以被合并起来同时写入文件就是其中的一中情况，leveldb中也是通过这个方式来处理累类似的情况。有兴趣可以看一下leveldb的源代码(DBImpl的Write方法采用了这种方式)。下面来看算法的Java版本。

代码中用到了`LinkedList`作为先进先出队列，使用`ReentrantLock`和`Condition`作为同步机制。定义如下：

	//任务队列
	private static final LinkedList<ContentHolder> queue = new LinkedList<ContentHolder>();
	//锁对象
	private static final Lock lock = new ReentrantLock();
	//条件对象，用来执行wait和signal方法
	private static final Condition cond = lock.newCondition();
	
定义了一个`ContentHolder`类，该类是`QueueSynchronization`类的一个内部类，用来持有需要打印的字符串，定义如下：

	static class ContentHolder {
		private boolean isPrinted;
		private Condition cond;
		private String content;

		ContentHolder(String content) {
			this.content = content;
			this.isPrinted = false;
			this.cond = QueueSynchronization.cond;
		}

		String getContent() {
			return content;
		}
	}

这个类中有三个属性，`isPrinted`表示内容是否已经被打印了，`cond`保存对`Condition`对象的引用，用来执行挂起和唤醒操作，`content`用来保存需要打印的内容。在构造函数中，将`isPrinted`设置为false，`cond`引用了在外层类中定义的`Condition`对象。

打印逻辑放到`QueueSynchronization`类的`printContent`方法中，逻辑主要可以分为三个部分，分别是进入队列并等待、打印和唤醒。先来看进入队列并等待部分的代码：

	lock.lock();
	try {
		queue.addLast(holder);
		//持有不是队列第一个元素的线程将进入挂起状态没有被打印的元素将进入挂起状态
		//使用while是为了防止意外唤醒
		while (!holder.isPrinted && holder != queue.peekFirst()) {
			try {
				cond.await();
			} catch (InterruptedException e) {
				e.printStackTrace(System.out);
			}
		}
	} finally {
		lock.unlock();
	}

这部分操作首先获取锁，然后将`holder`对象放到队列中，关键的部分是`while`循环，该循环判断当前的这个`holder`的打印状态以及在队列中的位置，如果还没有被打印并且不是队列的第一个元素，那么就会挂起当前线程。通过`while`循环可以防止线程意外唤醒。

如果当前线程处在未打印状态，并且是队列的第一个元素，那么就会进入到真正的打印流程。代码如下：

	lock.lock();
	try {
		//为了避免一个线程把所有的内容都打印了，
		//限制每个线程最多打印queue中的10个元素
		int count = 0;
		while (true) {
			//获取队列中的第一个元素，但是不能弹出，因为有数量控制
			ContentHolder first = queue.peekFirst();
			//queue中没有更多元素或者已经打印了10次，则退出循环
			if (count++ >= 10 || first == null) {
				break;
			}
			System.out.println(first.getContent() + "-->printed by " + Thread.currentThread().getName());
			//设置每一个被打印的ContentHolder的打印状态为true
			first.isPrinted = true;
			//执行唤醒操作
			first.cond.signal();
			//弹出队列中的第一个元素
			queue.pollFirst();
		}
	} finally {
		lock.unlock();
	}

所有的操作放到一个`while(true)`循环中，因为我们需要打印队列中尽可能多的`ContentHolder`对象。`while(true)`循环体首先从队列中取出第一个元素，如果该元素不为空(表示队列中还有数据)并且打印次数小于10次(为了有更好的输出效果，否则一个线程就有可能把队列中的所有元素都打印完了)，那么就把这个`ContentHolder`对象的内容打印出来。打印语句后面的操作也很关键，目的是修改打印状态以及执行唤醒操作，这样在前面被挂起的线程就会重新开始执行，而且由于打印状态被设置成true了，这个线程在醒来之后会判断这个状态，发现它持有的`ContentHolder`对象已经被打印了之后就会直接返回了(可以参考后面给出的源码)。这个循环在当前队列被消耗完或者已经打印了10次之后就会退出。这部分操作也必须要加锁，但是加锁的目的是为了数据同步，因为操作了共享对象，如果不执行同步，那么其他线程有可能看不到共享对象发生的变化。这里的共享对象主要是`ContentHolder`对象和队列。

启动程序的main函数代码如下：

	public static void main(String[] args) {
		System.out.println("Main thread is:" + Thread.currentThread().getName());
		final QueueSynchronization queueSync = new QueueSynchronization();

		List<Thread> threads = new ArrayList<Thread>();
		for (int i = 0; i < 20; i++) {
			final Thread t = new Thread(new Runnable() {
				public void run() {
					queueSync.printContent(new ContentHolder(Thread.currentThread().getName()));
				}
			});
			t.start();
			threads.add(t);
		}

		for (Thread t : threads) {
			try {
				t.join();
			} catch (InterruptedException e) {
				e.printStackTrace(System.out);
			}
		}
	}

程序初始化了20个线程，将`ContentHolder`对象的内容设置成当前线程的名字，程序的输出结果如下：

	Main thread is:main
	Thread-1-->printed by Thread-1
	Thread-2-->printed by Thread-1
	Thread-3-->printed by Thread-3
	Thread-4-->printed by Thread-3
	Thread-5-->printed by Thread-3
	Thread-6-->printed by Thread-3
	Thread-7-->printed by Thread-3
	Thread-8-->printed by Thread-3
	Thread-9-->printed by Thread-3
	Thread-10-->printed by Thread-3
	Thread-11-->printed by Thread-3
	Thread-12-->printed by Thread-3
	Thread-13-->printed by Thread-13
	Thread-14-->printed by Thread-13
	Thread-15-->printed by Thread-13
	Thread-16-->printed by Thread-13
	Thread-17-->printed by Thread-13
	Thread-18-->printed by Thread-13
	Thread-19-->printed by Thread-13
	Thread-20-->printed by Thread-13
	
可以看到Thread-1打印了2个`ContentHolder`对象中的内容，Thread-3答应了10个`ContentHolder`对象中的内容，Thread-13打印了8个`ContentHolder`对象中的内容。

[<<点击获取程序的完整源代码>>](/files/QueueSynchronization.java)
	
