---
title: 使用Reference对象检测未释放的资源
layout: post
permalink: /2013/10/use-reference-to-detect-memory-leak
date: Fri Oct 19 16:10:56 pm GMT+8 2013
published: true
---

Java中有三个Reference类，分别是`SoftReference`、`WeakReference`和`PhantomReference`。这些引用类在被引用的对象可以被垃圾收集器执行回收的时候会被放到`ReferenceQueue`中，利用Java这个机制，我们可以在应用中检测没有执行资源清理操作的对象。

首先，需要定义一个`PhantomReference`的子类，如下：

{% highlight java linenos %}

public class DefaultResourceLeak extends PhantomReference<Object> implements ResourceLeak {
        private final AtomicBoolean freed;
        public DefaultResourceLeak(Object referent, ReferenceQueue<Object> refQueue) {
            super(referent, refQueue);
            if (referent != null) {
                freed = new AtomicBoolean();
            } else {
                freed = new AtomicBoolean(true);
            }
        }

        @Override
        public boolean release() {
            if (freed.compareAndSet(false, true)) {
                return true;
            }
            return false;
        }
    }
    
{% endhighlight %}

其次，需要一个创建检测类，利用上面定义的`DefaultResourceLeak`类来检测未释放资源就被回收了的对象，如下：

{% highlight java linenos %}

public final class ResourceLeakDetector<T> {
	private Log logger = LogFactory.getLog(ResourceLeakDetector.class);
	private final String resourceType;
	private final ReferenceQueue<Object> refQueue = new ReferenceQueue<Object>();
	
	public ResourceLeakDetector(String resourceType) {
        this.resourceType = resourceType;
    }
    
	public ResourceLeak open(T obj) {
        reportLeak();
        return new DefaultResourceLeak(obj, refQueue);
    }
    
    private void reportLeak() {
        // Detect and report previous leaks.
        for (;;) {
            @SuppressWarnings("unchecked")
            DefaultResourceLeak ref = (DefaultResourceLeak) refQueue.poll();
            if (ref == null) {
                break;
            }
			// clear the reference, so the referent can be reclaimed by the GC
            ref.clear();

            if (!ref.release()) {
                continue;
            } else {
            	logger.warn("LEAK: " + resourceType + " was GC'd before being released correctly.");
            }         
        }
    }
}

{% endhighlight %}

在应用或者系统中，通过使用上面的两个类就可以达到检测使用后未正确执行清理操作的对象了。使用方法如下，这里假设我们有一个`Buffer`类需要执行检测，这个类的定义如下：

{% highlight java linenos %}

public final class Buffer {
	private static ResourceLeakDetector<Buffer> detector = new ResourceLeakDetector<Buffer>(Buffer.class.getSimpleName());
	private DefaultResourceLeak leak;
	public Buffer() {
		leak = detector.open(this);
	}	
	
	public void close() {
		leak.release();
	}
}

{% endhighlight %}

上面的这个`Buffer`类的构造函数中调用了`ResourceLeakDetector`的`open`方法， 从而创建了一个指向自己的影子引用。这样，当`Buffer`对象可以被垃圾回收时，就会进入到`ResourceLeakDetector`类中定义的`refQueue`中。

在`ResourceLeakDetector`的`reportLeak`方法中，如果从`refQueue`中拿到了代表影子引用的`DefaultResourceLeak`对象，就会检查这个对象的`freed`属性，如果这个属性没有被设置成`true`，就说明系统或者应用在使用完`Buffer`对象之后，没有调用其上的`close`方法。这样就可以达到检测和报告未正确执行资源清理的目的了。