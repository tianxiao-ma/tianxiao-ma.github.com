package com.taobao.tianxiao;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.Lock;

/**
 * 利用队列和锁来实现多线程的同步，适用于多个任务可以被合并执行的情况，比如写文件操作。
 * @author tianxiao
 */
public class QueueSynchronization {
	//任务队列
	private static final LinkedList<ContentHolder> queue = new LinkedList<ContentHolder>();
	//锁对象
	private static final Lock lock = new ReentrantLock();
	//条件对象，用来执行wait和signal方法
	private static final Condition cond = lock.newCondition();

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

	public void printContent(ContentHolder holder) {
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

		if (holder.isPrinted) {
			return;
		}

		{// 能够进入到这里，说明这个线程提交打印的ContentHolder是队列的第一个
			// 等待有新的ContentHolder进入队列
			try {
				TimeUnit.MILLISECONDS.sleep(1);
			} catch (InterruptedException e) {
				;
			}

			//这里必须要加锁，因为需要操作queue这个共享变量
			//这个锁的主要目的是让各个线程看到的内存中的数据保持一致性，并不是用来排他地执行下面的逻辑
			lock.lock();
			try {
				//为了避免一个线程把所有的内容都打印了，
				//限制每个线程最多打印queue中的10个元素
				int count = 0;
				while (true) {
					//获取队列中的第一个元素，但是不能弹出，因为有数量控制
					ContentHolder first = queue.peekFirst();
					//queue中没有更多元素或者已经打印了10次，则推出循环
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
		}
	}

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
}

